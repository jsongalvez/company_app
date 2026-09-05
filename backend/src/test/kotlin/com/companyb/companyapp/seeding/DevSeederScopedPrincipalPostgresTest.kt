package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.test.BasePostgresTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `seeded scoped principal is branch-bound without global capabilities`() {
        DevSeeder.seed(
            config(scopedTestUsername = scopedUsername, scopedTestPassword = "scope-pass"),
        )

        transaction {
            val user =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq scopedUsername }
                    .single()
            val userId = user[AppUserTable.id]

            assertNoRole(userId)
            assertBranchCapabilityShape(userId)
            assertSingleActiveAssignment(userId)
            assertNotNull(UserRepository.findByUsername(scopedUsername))
        }
    }

    private fun assertNoRole(userId: UUID) {
        assertEquals(
            0,
            UserRoleTable.selectAll().where { UserRoleTable.userId eq userId }.count(),
            "Scoped principal must hold no role (roles derive GLOBAL capability bundles)",
        )
    }

    private fun assertBranchCapabilityShape(userId: UUID) {
        val grants =
            UserCapabilityTable
                .selectAll()
                .where { UserCapabilityTable.userId eq userId }
                .toList()

        val codeById =
            CapabilityTable
                .selectAll()
                .where { CapabilityTable.id inList grants.map { it[UserCapabilityTable.capabilityId] } }
                .associate { it[CapabilityTable.id] to it[CapabilityTable.code] }

        assertEquals(
            setOf(CapabilityContextType.BRANCH),
            grants.groupBy { it[UserCapabilityTable.contextType] }.keys,
            "Every scoped grant must be BRANCH-scoped — no GLOBAL rows",
        )
        assertEquals(
            listOf(
                "ASSIGN_COMPENSATION",
                "EDIT_BRANCH_DATA",
                "EDIT_PAST_DAY",
                "MANAGE_PRODUCTS",
                "SUBMIT_REMITTANCE",
                "VIEW_BRANCH_DATA",
                "VOID_SESSION",
            ),
            grants.map { codeById.getValue(it[UserCapabilityTable.capabilityId]) }.sorted(),
            "Scoped principal holds exactly the fixture-branch coordinator set",
        )
        assertTrue(
            grants.all { it[UserCapabilityTable.contextId] == DEV_FIXTURE_BRANCH_ID },
            "Every grant must bind to the K6 Fixture Branch",
        )
    }

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
        scopedTestUsername: String,
        scopedTestPassword: String,
    ) = AppConfig(
        appPort = 8080,
        dbHost = System.getenv("DB_HOST") ?: "localhost",
        dbPort = System.getenv("DB_PORT") ?: "5432",
        dbName = "test",
        dbUser = "test",
        dbPassword = "test",
        jwtSecret = "test-secret-that-is-at-least-32-chars",
        jwtIssuer = "test",
        jwtAudience = "test",
        authDummyPassword = "test-dummy-password-at-least-32-characters",
        testUsername = null,
        testPassword = null,
        scopedTestUsername = scopedTestUsername,
        scopedTestPassword = scopedTestPassword,
        reliefTestUsername = null,
        reliefTestPassword = null,
    )
}
