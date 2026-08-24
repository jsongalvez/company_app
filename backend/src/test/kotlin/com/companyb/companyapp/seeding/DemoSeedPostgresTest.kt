package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.test.BasePostgresTest
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the #415 demo dataset: full cast across role angles, populated world, and strict
 * idempotency across repeated boots.
 */
class DemoSeedPostgresTest : BasePostgresTest() {
    private val manila: ZoneId = ZoneId.of("Asia/Manila")

    private val demoUsernames =
        listOf(
            OWNER_USERNAME,
            COORDINATOR_USERNAME,
            PRACTITIONER_USERNAME,
            PRACTITIONER_B_USERNAME,
            ACCOUNTANT_USERNAME,
            ONBOARDING_USERNAME,
        )

    override fun initTestData() = Unit

    @Test
    fun `demo seed populates every angle and repeats as a no-op`() {
        seedDemo()

        transaction {
            val users =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username inList demoUsernames }
                    .associate { it[AppUserTable.username] to it[AppUserTable.id] }
            assertEquals(6, users.size, "All six demo logins must exist")

            assertOwnerShape(users.getValue(OWNER_USERNAME))
            assertOnboardingShape(users.getValue(ONBOARDING_USERNAME))
            assertBranchStaffShapes(users)
        }

        trackDemoRows { table, column, id -> trackOwned(table, column, id) }
        assertWorldPopulated()

        // Second boot: identical row counts everywhere — pure no-op.
        val countsBefore = snapshotCounts()
        seedDemo()
        assertEquals(countsBefore, snapshotCounts(), "Repeated demo seeding must not add rows")
    }

    private fun seedDemo() {
        DemoSeed.seed(config(demoSeed = true))
    }

    private fun config(demoSeed: Boolean) =
        AppConfig(
            appHost = "localhost",
            appPort = 8080,
            dbHost = "localhost",
            dbPort = "5432",
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
            reliefTestUsername = null,
            reliefTestPassword = null,
            demoSeed = demoSeed,
        )

    private fun assertOwnerShape(ownerId: UUID) {
        val globalCaps =
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.userId eq ownerId) and
                        (UserCapabilityTable.contextType eq CapabilityContextType.GLOBAL)
                }.count()
        assertTrue(globalCaps >= 9, "Owner must hold the GLOBAL capability set")
        assertNotNull(UserRepository.findByUsername(OWNER_USERNAME))
    }

    private fun assertOnboardingShape(onboardingId: UUID) {
        assertEquals(
            0,
            UserCapabilityTable.selectAll().where { UserCapabilityTable.userId eq onboardingId }.count(),
            "Onboarding hire holds zero direct capabilities",
        )
        assertTrue(
            UserRoleTable
                .selectAll()
                .where {
                    (UserRoleTable.userId eq onboardingId) and (UserRoleTable.roleId eq roleIdByName("ONBOARDING"))
                }.empty()
                .not(),
            "Onboarding hire carries the ONBOARDING role",
        )
    }

    private fun assertBranchStaffShapes(users: Map<String, UUID>) {
        val coordinatorId = users.getValue(COORDINATOR_USERNAME)
        val practitionerBId = users.getValue(PRACTITIONER_B_USERNAME)

        assertEquals(
            CLINIC_ID,
            activeAssignmentBranch(coordinatorId),
            "Coordinator is assigned to the demo clinic",
        )
        assertEquals(
            TOUR_ID,
            activeAssignmentBranch(practitionerBId),
            "Second practitioner is assigned to the provincial tour branch",
        )
    }

    private fun activeAssignmentBranch(userId: UUID): UUID =
        UserBranchAssignmentTable
            .selectAll()
            .where {
                (UserBranchAssignmentTable.userId eq userId) and
                    UserBranchAssignmentTable.endedAt.isNull()
            }.single()[UserBranchAssignmentTable.branchId]

    private fun assertWorldPopulated() {
        transaction {
            val today = LocalDate.now(manila)
            val clinicToday =
                BranchDayTable
                    .selectAll()
                    .where { (BranchDayTable.branchId eq CLINIC_ID) and (BranchDayTable.date eq today) }
                    .single()[BranchDayTable.id]

            val pendingClientIds =
                SessionTable
                    .selectAll()
                    .where { SessionTable.sessionStatus eq SessionStatus.PENDING }
                    .map { it[SessionTable.clientId] }
            assertTrue(pendingClientIds.isNotEmpty(), "At least one actionable PENDING demo session exists")

            val practitionerId =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.username eq PRACTITIONER_USERNAME }
                    .single()[AppUserTable.id]
            assertEquals(
                1L,
                AttendanceTable
                    .selectAll()
                    .where {
                        (AttendanceTable.branchDayId eq clinicToday) and
                            (AttendanceTable.userId eq practitionerId) and
                            AttendanceTable.clockOut.isNull()
                    }.count(),
                "Clinic practitioner is clocked in for today so dashboards render immediately",
            )
        }
    }

    private fun snapshotCounts(): Map<String, Long> =
        transaction {
            mapOf(
                "users" to AppUserTable.selectAll().where { AppUserTable.username inList demoUsernames }.count(),
                "clients" to
                    ClientTable
                        .selectAll()
                        .where {
                            ClientTable.id inList
                                DEMO_CLIENTS.map { UUID.fromString(it.fixedId) }
                        }.count(),
                "products" to
                    ProductTable
                        .selectAll()
                        .where {
                            ProductTable.id inList
                                DEMO_PRODUCTS.map { UUID.fromString(it.fixedId) }
                        }.count(),
                "days" to
                    BranchDayTable
                        .selectAll()
                        .where {
                            BranchDayTable.branchId inList
                                listOf(
                                    CLINIC_ID,
                                    TOUR_ID,
                                )
                        }.count(),
                "sessions" to
                    SessionTable
                        .selectAll()
                        .where {
                            SessionTable.branchDayId inList
                                BranchDayTable
                                    .selectAll()
                                    .where {
                                        BranchDayTable.branchId inList listOf(CLINIC_ID, TOUR_ID)
                                    }.map { it[BranchDayTable.id] }
                        }.count(),
                "inventory" to
                    BranchInventoryTable
                        .selectAll()
                        .where {
                            BranchInventoryTable.branchId inList
                                listOf(CLINIC_ID, TOUR_ID)
                        }.count(),
            )
        }

    private fun roleIdByName(roleName: String): UUID =
        RoleTable
            .selectAll()
            .where { RoleTable.name eq roleName }
            .single()[RoleTable.id]
}

private fun trackDemoRows(track: (Table, Column<UUID>, UUID) -> Unit) {
    transaction {
        trackUserRows(track)
        trackWorldRows(track)
    }
}

private fun trackUserRows(track: (Table, Column<UUID>, UUID) -> Unit) {
    val userIds =
        AppUserTable
            .selectAll()
            .where {
                AppUserTable.username inList
                    listOf(
                        OWNER_USERNAME,
                        COORDINATOR_USERNAME,
                        PRACTITIONER_USERNAME,
                        PRACTITIONER_B_USERNAME,
                        ACCOUNTANT_USERNAME,
                        ONBOARDING_USERNAME,
                    )
            }.map { it[AppUserTable.id] }
    userIds.forEach { track(AppUserTable, AppUserTable.id, it) }
    for (userId in userIds) {
        track(UserRoleTable, UserRoleTable.userId, userId)
        track(UserCapabilityTable, UserCapabilityTable.userId, userId)
        track(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, userId)
        track(AttendanceTable, AttendanceTable.userId, userId)
        track(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, userId)
    }
}

private fun trackWorldRows(track: (Table, Column<UUID>, UUID) -> Unit) {
    val demoBranchIds = listOf(CLINIC_ID, TOUR_ID)
    val dayIds =
        BranchDayTable
            .selectAll()
            .where { BranchDayTable.branchId inList demoBranchIds }
            .map { it[BranchDayTable.id] }
    dayIds.forEach { track(BranchDayTable, BranchDayTable.id, it) }

    val sessionIds =
        if (dayIds.isEmpty()) {
            emptyList()
        } else {
            SessionTable
                .selectAll()
                .where { SessionTable.branchDayId inList dayIds }
                .map { it[SessionTable.id] }
        }
    sessionIds.forEach { track(SessionTable, SessionTable.id, it) }
    for (sessionId in sessionIds) {
        track(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        track(SessionConcernTable, SessionConcernTable.sessionId, sessionId)
    }

    ClientTable
        .selectAll()
        .where { ClientTable.id inList DEMO_CLIENTS.map { UUID.fromString(it.fixedId) } }
        .forEach { track(ClientTable, ClientTable.id, it[ClientTable.id]) }

    track(ProductCategoryTable, ProductCategoryTable.id, MEDICINES_CATEGORY_ID)
    for (spec in DEMO_PRODUCTS) {
        track(ProductTable, ProductTable.id, UUID.fromString(spec.fixedId))
    }
    BranchInventoryTable
        .selectAll()
        .where { BranchInventoryTable.branchId inList demoBranchIds }
        .forEach { track(BranchInventoryTable, BranchInventoryTable.id, it[BranchInventoryTable.id]) }
    InventoryMovementTable
        .selectAll()
        .where { InventoryMovementTable.branchId inList demoBranchIds }
        .forEach { track(InventoryMovementTable, InventoryMovementTable.id, it[InventoryMovementTable.id]) }
    SessionBaseRateTable
        .selectAll()
        .where { SessionBaseRateTable.branchId inList demoBranchIds }
        .forEach { track(SessionBaseRateTable, SessionBaseRateTable.id, it[SessionBaseRateTable.id]) }
    ConcernTable
        .selectAll()
        .where { ConcernTable.label inList listOf(CONSULTATION_LABEL, EXTRACTION_LABEL) }
        .forEach { track(ConcernTable, ConcernTable.id, it[ConcernTable.id]) }
}
