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
 * Pins the branch-scoped k6 principal's seeded shape (#411): no role, no GLOBAL rows,
 * exactly the fixture-branch capability set, and an active home assignment.
 */
class DevSeederScopedPrincipalPostgresTest : BasePostgresTest() {
    // Suffix kept short: the seeded email (username + @example.com) fits varchar(50).
    private val scopedUsername = "k6scoped-${UUID.randomUUID().toString().substring(0, 8)}"

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
    fun `seeded scoped principal is a Coordinator member of the fixture branch`() {
        DevSeeder.seed(
            config(scopedUsername = scopedUsername, scopedPassword = "scope-pass"),
        )

        transaction {
            val user =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq scopedUsername }
                    .single()
            val userId = user[AppUserTable.id]

            assertEquals(setOf("COORDINATOR"), rolesOf(userId))
            assertSingleActiveAssignment(userId)
            assertNotNull(UserRepository.findByUsername(scopedUsername))
        }
    }

    private fun rolesOf(userId: UUID): Set<String> =
        UserRoleTable
            .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
            .select(RoleTable.name)
            .where { UserRoleTable.userId eq userId }
            .map { it[RoleTable.name] }
            .toSet()

    private fun assertSingleActiveAssignment(userId: UUID) {
        val assignments =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        UserBranchAssignmentTable.endedAt.isNull()
                }
        assertEquals(1, assignments.count(), "Exactly one active home assignment")
        assertEquals(
            DEV_FIXTURE_BRANCH_ID,
            assignments.single()[UserBranchAssignmentTable.branchId],
        )
    }

    private fun config(
        scopedUsername: String,
        scopedPassword: String,
    ) = DevFixtureConfig(
        globalUsername = null,
        globalPassword = null,
        scopedUsername = scopedUsername,
        scopedPassword = scopedPassword,
        reliefUsername = null,
        reliefPassword = null,
    )
}
