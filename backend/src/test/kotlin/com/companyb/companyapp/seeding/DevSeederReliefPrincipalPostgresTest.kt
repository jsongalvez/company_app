package com.companyb.companyapp.seeding

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pins the relief k6 principal's seeded shape (#413): no role, no GLOBAL rows, exactly
 * one BRANCH-scoped EDIT_BRANCH_DATA grant at the fixture branch, and deliberately NO
 * home assignment — the requestReliefAccess gate excludes assigned users while
 * grant/deny authority requires one, so this seat is what makes the relief flows
 * reachable end-to-end.
 */
class DevSeederReliefPrincipalPostgresTest : BasePostgresTest() {
    // Suffix kept short: the seeded email (username + @example.com) fits varchar(50).
    private val reliefUsername = "k6relief-${UUID.randomUUID().toString().substring(0, 8)}"

    override fun initTestData() {
        transaction {
            // Pre-create the fixture branch so the seeder's insertIgnore no-ops
            // and teardown owns the row.
            BranchTable.insertIgnore {
                it[id] = DEV_FIXTURE_BRANCH_ID
                it[name] = "K6 Fixture Branch"
                it[branchType] = BranchType.CLINIC
            }
        }
    }

    @Test
    fun `seeded relief principal is a Practitioner with no assignment`() {
        DevSeeder.seed(
            config(reliefUsername = reliefUsername, reliefPassword = "relief-pass"),
        )

        transaction {
            val user =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq reliefUsername }
                    .single()
            val userId = user[AppUserTable.id]

            assertEquals(setOf("PRACTITIONER"), rolesOf(userId))
            assertNoAssignment(userId)
            assertNotNull(UserRepository.findByUsername(reliefUsername))
        }
    }

    private fun rolesOf(userId: UUID): Set<String> =
        UserRoleTable
            .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
            .select(RoleTable.name)
            .where { UserRoleTable.userId eq userId }
            .map { it[RoleTable.name] }
            .toSet()

    private fun assertNoAssignment(userId: UUID) {
        assertEquals(
            0,
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        UserBranchAssignmentTable.endedAt.isNull()
                }.count(),
            "Relief principal must hold NO home assignment — assigned users cannot request relief duty",
        )
    }

    private fun config(
        reliefUsername: String,
        reliefPassword: String,
    ) = DevFixtureConfig(
        globalUsername = null,
        globalPassword = null,
        scopedUsername = null,
        scopedPassword = null,
        reliefUsername = reliefUsername,
        reliefPassword = reliefPassword,
    )
}
