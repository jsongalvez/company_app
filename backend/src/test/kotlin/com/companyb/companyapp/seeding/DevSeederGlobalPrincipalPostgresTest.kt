package com.companyb.companyapp.seeding

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.testsupport.database.BasePostgresTest
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
            config(testUsername = ownerUsername, testPassword = "owner-pass"),
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
        testUsername: String,
        testPassword: String,
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
        testUsername = testUsername,
        testPassword = testPassword,
        scopedTestUsername = null,
        scopedTestPassword = null,
        reliefTestUsername = null,
        reliefTestPassword = null,
    )
}
