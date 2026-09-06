package com.companyb.companyapp.seeding

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityTable
import com.companyb.companyapp.authorization.UserCapabilityTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
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
    fun `seeded relief principal holds capability without an assignment`() {
        DevSeeder.seed(
            config(reliefTestUsername = reliefUsername, reliefTestPassword = "relief-pass"),
        )

        transaction {
            val user =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq reliefUsername }
                    .single()
            val userId = user[AppUserTable.id]

            assertNoRole(userId)
            assertBranchCapabilityShape(userId)
            assertNoAssignment(userId)
            assertNotNull(UserRepository.findByUsername(reliefUsername))
        }
    }

    private fun assertNoRole(userId: UUID) {
        assertEquals(
            0,
            UserRoleTable.selectAll().where { UserRoleTable.userId eq userId }.count(),
            "Relief principal must hold no role (roles derive GLOBAL capability bundles)",
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
            "Every relief grant must be BRANCH-scoped — no GLOBAL rows",
        )
        assertEquals(
            listOf("EDIT_BRANCH_DATA"),
            grants.map { codeById.getValue(it[UserCapabilityTable.capabilityId]) },
            "Relief principal holds exactly the requester capability (the #157 day-gate code)",
        )
        assertTrue(
            grants.all { it[UserCapabilityTable.contextId] == DEV_FIXTURE_BRANCH_ID },
            "Every grant must bind to the K6 Fixture Branch",
        )
    }

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
        reliefTestUsername: String,
        reliefTestPassword: String,
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
        scopedTestUsername = null,
        scopedTestPassword = null,
        reliefTestUsername = reliefTestUsername,
        reliefTestPassword = reliefTestPassword,
    )
}
