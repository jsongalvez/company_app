package com.companyb.companyapp.seeding

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the dev owner's seeded shape (#575): OWNER role plus an active home assignment at the
 * fixture branch. Capabilities alone leave BranchSelect empty (`MeRepository.findBranchRows`
 * lists assigned + clocked-in branches only), stranding the documented smoke-test login with
 * no clock-in path.
 */
class DevSeederGlobalPrincipalPostgresTest : BasePostgresTest() {
    private val ownerUsername = "k6owner-${UUID.randomUUID().toString().substring(0, 8)}"

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
    fun `seeded global principal keeps owner role with home assignment`() {
        DevSeeder.seed(
            config(globalUsername = ownerUsername, globalPassword = "owner-pass"),
        )

        transaction {
            val userId =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq ownerUsername }
                    .single()[AppUserTable.id]

            assertEquals(
                1,
                UserRoleTable.selectAll().where { UserRoleTable.userId eq userId }.count(),
                "Dev owner must keep exactly one role row",
            )
            val assignments =
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }
            assertEquals(1, assignments.count(), "Dev owner needs exactly one active home assignment")
            assertEquals(
                DEV_FIXTURE_BRANCH_ID,
                assignments.single()[UserBranchAssignmentTable.branchId],
            )
        }
    }

    private fun config(
        globalUsername: String,
        globalPassword: String,
    ) = DevFixtureConfig(
        globalUsername = globalUsername,
        globalPassword = globalPassword,
        scopedUsername = null,
        scopedPassword = null,
        reliefUsername = null,
        reliefPassword = null,
    )
}
