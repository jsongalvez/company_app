package com.companyb.companyapp.seeding

import com.companyb.companyapp.authorization.UserCapabilityTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.session.SessionBaseRateRepository
import com.companyb.companyapp.session.SessionBaseRateTable
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #880 — rerun-repair: every seed path reconciles per entity instead of
 * early-returning on the existing user row, and every path that ensures the
 * fixture branch leaves the session base rates behind.
 */
class DevSeederReseedPostgresTest : BasePostgresTest() {
    override fun initTestData() {
        transaction {
            BranchTable.insertIgnore {
                it[id] = DEV_FIXTURE_BRANCH_ID
                it[name] = "K6 Fixture Branch"
                it[branchType] = BranchType.CLINIC
            }
        }
    }

    @Test
    fun `reseed heals scoped principal after capability and assignment wipe`() {
        val username = "k6heal-${UUID.randomUUID().toString().substring(0, 8)}"
        DevSeeder.seed(config(scoped = username to "scope-pass"))

        val userId =
            transaction {
                AppUserTable.selectAll().where { AppUserTable.username eq username }.single()[AppUserTable.id]
            }
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            UserBranchAssignmentTable.deleteWhere { UserBranchAssignmentTable.userId eq userId }
        }

        // A second seed heals the wipe; a third proves the heal inserted no duplicates.
        DevSeeder.seed(config(scoped = username to "scope-pass"))
        DevSeeder.seed(config(scoped = username to "scope-pass"))

        transaction {
            assertEquals(
                7,
                UserCapabilityTable.selectAll().where { UserCapabilityTable.userId eq userId }.count(),
                "Reseed must restore exactly the 7 fixture-branch grants",
            )
            assertEquals(
                1,
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }.count(),
                "Reseed must restore the single active home assignment",
            )
            assertEquals(
                0,
                UserRoleTable.selectAll().where { UserRoleTable.userId eq userId }.count(),
                "Reseed must not attach a role to the scoped principal",
            )
            assertEquals(
                4,
                SessionBaseRateRepository.findActiveByBranchInTransaction(DEV_FIXTURE_BRANCH_ID).size,
                "Scoped-only seed must leave the 4 fixture-branch base rates behind",
            )
        }
    }

    @Test
    fun `reseed heals bare pre-created user into full scoped shape`() {
        val username = "k6bare-${UUID.randomUUID().toString().substring(0, 8)}"
        IdentityFixtures.insertUser(
            id = UUID.randomUUID(),
            username = username,
            passwordHash = "bare-user-hash",
            email = "$username@t.st",
            displayName = "Bare $username",
        )

        DevSeeder.seed(config(scoped = username to "scope-pass"))

        transaction {
            val rows = AppUserTable.selectAll().where { AppUserTable.username eq username }.toList()
            assertEquals(1, rows.size, "Reseed must reuse the pre-created user, not insert a second row")
            val userId = rows.single()[AppUserTable.id]
            assertEquals(
                7,
                UserCapabilityTable.selectAll().where { UserCapabilityTable.userId eq userId }.count(),
                "Reseed must grant the full 7-capability set to the bare user",
            )
            assertEquals(
                1,
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }.count(),
                "Reseed must assign the bare user to the fixture branch",
            )
        }
    }

    @Test
    fun `scoped-only seed provisions fixture branch base rates`() {
        val username = "k6srate-${UUID.randomUUID().toString().substring(0, 8)}"
        DevSeeder.seed(config(scoped = username to "scope-pass"))

        transaction {
            assertEquals(
                4,
                SessionBaseRateRepository.findActiveByBranchInTransaction(DEV_FIXTURE_BRANCH_ID).size,
                "Scoped-only seed must provision the 4 fixture-branch base rates",
            )
        }
    }

    @Test
    fun `relief-only seed provisions base rates while keeping no assignment`() {
        val username = "k6rrate-${UUID.randomUUID().toString().substring(0, 8)}"
        DevSeeder.seed(config(relief = username to "relief-pass"))
        DevSeeder.seed(config(relief = username to "relief-pass"))

        transaction {
            val userId =
                AppUserTable.selectAll().where { AppUserTable.username eq username }.single()[AppUserTable.id]
            assertEquals(
                1,
                UserCapabilityTable.selectAll().where { UserCapabilityTable.userId eq userId }.count(),
                "Relief reseed must keep exactly the single requester grant",
            )
            assertEquals(
                0,
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }.count(),
                "Relief reseed must keep the deliberate no-assignment seat",
            )
            assertEquals(
                4,
                SessionBaseRateRepository.findActiveByBranchInTransaction(DEV_FIXTURE_BRANCH_ID).size,
                "Relief-only seed must provision the 4 fixture-branch base rates",
            )
        }
    }

    @Test
    fun `reseed heals global principal after partial wipe without duplicates`() {
        val username = "k6gheal-${UUID.randomUUID().toString().substring(0, 8)}"
        DevSeeder.seed(config(global = username to "owner-pass"))

        val userId =
            transaction {
                AppUserTable.selectAll().where { AppUserTable.username eq username }.single()[AppUserTable.id]
            }
        transaction {
            UserRoleTable.deleteWhere { UserRoleTable.userId eq userId }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            UserBranchAssignmentTable.deleteWhere { UserBranchAssignmentTable.userId eq userId }
            SessionBaseRateTable.deleteWhere {
                (SessionBaseRateTable.branchId eq DEV_FIXTURE_BRANCH_ID) and
                    (
                        (SessionBaseRateTable.sessionType eq SessionType.REGULAR) or
                            (SessionBaseRateTable.sessionType eq SessionType.SUBSEQUENT)
                    )
            }
        }

        // A second seed heals the wipe; a third proves the heal inserted no duplicates.
        DevSeeder.seed(config(global = username to "owner-pass"))
        DevSeeder.seed(config(global = username to "owner-pass"))

        transaction {
            assertEquals(
                1,
                UserRoleTable.selectAll().where { UserRoleTable.userId eq userId }.count(),
                "Reseed must restore the OWNER role row",
            )
            assertEquals(
                17,
                UserCapabilityTable.selectAll().where { UserCapabilityTable.userId eq userId }.count(),
                "Reseed must restore all 10 GLOBAL plus 7 branch grants",
            )
            assertEquals(
                1,
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }.count(),
                "Reseed must restore the single active home assignment",
            )
            assertEquals(
                4,
                SessionBaseRateRepository.findActiveByBranchInTransaction(DEV_FIXTURE_BRANCH_ID).size,
                "Reseed must top up the 2 deleted rate types without touching the kept 2",
            )
        }
    }

    private fun config(
        global: Pair<String, String>? = null,
        scoped: Pair<String, String>? = null,
        relief: Pair<String, String>? = null,
    ) = DevFixtureConfig(
        globalUsername = global?.first,
        globalPassword = global?.second,
        scopedUsername = scoped?.first,
        scopedPassword = scoped?.second,
        reliefUsername = relief?.first,
        reliefPassword = relief?.second,
    )
}
