package com.companyb.companyapp.session.dashboard
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.commission.CommissionManualInclusionRepository
import com.companyb.companyapp.commission.CommissionManualInclusionUpsertParams
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.notification.NotificationService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val otherUserId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val saleId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "caller")
        IdentityFixtures.insertTestUser(otherUserId, "other")
        IdentityFixtures.insertTestUser(practitionerId, "practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Branch B")
        SessionClientFixtures.insertTestClient(clientId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)

        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
    }

    @Test
    fun `getToday returns enriched sessions with client name`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val data = DashboardService.getToday(callerId, branchId)

        assertEquals(1, data.sessions.size)
        assertEquals(sessionId, data.sessions.single().id)
        val clientNames = data.clientNames.getValue(clientId)
        assertEquals("Test", clientNames.firstName)
        assertEquals("Client", clientNames.lastName)
        assertFalse(sessionId in data.voidedSessionIds)
        assertTrue(data.practitioners.isEmpty())
        assertTrue(data.concerns.isEmpty())
        assertEquals(BigDecimal.ZERO, data.commission.amount)
        assertEquals(0, data.commission.productSalesCount)
    }

    @Test
    fun `getToday marks voided sessions and includes practitioner names`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedVoid(sessionId)
        seedPractitioner(sessionId, practitionerId)

        val data = DashboardService.getToday(callerId, branchId)

        assertTrue(sessionId in data.voidedSessionIds)
        assertEquals(1, data.practitioners.size)
        val practitioner = data.practitioners.single()
        assertEquals(practitionerId, practitioner.practitionerId)
        assertEquals("Test practitioner", practitioner.displayName)
    }

    @Test
    fun `getToday computes caller commission from sales sold while clocked in`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
            quantity = 2,
            commissionAmount = BigDecimal("100.00"),
        )

        val data = DashboardService.getToday(callerId, branchId)

        assertEquals(BigDecimal("200.0000"), data.commission.amount)
        assertEquals(1, data.commission.productSalesCount)
    }

    @Test
    fun `getToday counts sales by attendance eligibility not by seller`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = otherUserId,
            quantity = 1,
            commissionAmount = BigDecimal("50.00"),
        )

        val data = DashboardService.getToday(callerId, branchId)

        assertEquals(BigDecimal("50.0000"), data.commission.amount)
        assertEquals(1, data.commission.productSalesCount)
    }

    @Test
    fun `getToday excludes sales the caller was removed from via manual inclusion`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = otherUserId,
            quantity = 1,
            commissionAmount = BigDecimal("50.00"),
        )
        transaction {
            CommissionManualInclusionRepository.upsertInTransaction(
                CommissionManualInclusionUpsertParams(
                    id = TestFixtures.uuid(),
                    productSaleId = saleId,
                    userId = callerId,
                    isIncluded = false,
                    reason = null,
                    assignedBy = callerId,
                ),
            )
        }

        val data = DashboardService.getToday(callerId, branchId)

        assertEquals(BigDecimal.ZERO, data.commission.amount)
        assertEquals(0, data.commission.productSalesCount)
    }

    @Test
    fun `getToday returns empty day with zero commission`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, callerId)

        val data = DashboardService.getToday(callerId, branchId)

        assertTrue(data.sessions.isEmpty())
        assertEquals(BigDecimal.ZERO, data.commission.amount)
        assertEquals(0, data.commission.productSalesCount)
    }

    @Test
    fun `getToday throws ForbiddenException when not clocked in at the branch`() {
        assertFailsWith<ForbiddenException> {
            DashboardService.getToday(callerId, branchId)
        }
    }

    @Test
    fun `getToday throws ForbiddenException when clocked in at another branch`() {
        AttendanceService.clockIn(TestFixtures.uuid(), otherBranchId, callerId)

        assertFailsWith<ForbiddenException> {
            DashboardService.getToday(callerId, branchId)
        }
    }

    @Test
    fun `getToday throws NotFoundException for missing branch`() {
        assertFailsWith<NotFoundException> {
            DashboardService.getToday(callerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `getSessionDetail returns enriched session for notification bearer`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
        grantBranchView(callerId, branchId)
        seedPractitioner(sessionId, practitionerId)

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertEquals(sessionId, data.session.id)
        val clientNames = data.clientNames.getValue(clientId)
        assertEquals("Test", clientNames.firstName)
        assertEquals("Client", clientNames.lastName)
        assertFalse(sessionId in data.voidedSessionIds)
        assertEquals(1, data.practitioners.size)
        assertEquals(practitionerId, data.practitioners.single().practitionerId)
        assertTrue(data.concerns.isEmpty())
    }

    @Test
    fun `getSessionDetail marks voided sessions`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
        grantBranchView(callerId, branchId)
        seedVoid(sessionId)

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertTrue(sessionId in data.voidedSessionIds)
    }

    @Test
    fun `getSessionDetail accepts read notifications`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        val notification = seedNotification(sessionId, callerId, branchId)
        grantBranchView(callerId, branchId)
        NotificationService.markRead(callerId, notification.id)

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertEquals(sessionId, data.session.id)
    }

    @Test
    fun `getSessionDetail throws 404 when no notification exists for the session`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        grantBranchView(callerId, branchId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    @Test
    fun `getSessionDetail throws 404 when caller is not the notification bearer`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, otherUserId, branchId)
        grantBranchView(callerId, branchId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    @Test
    fun `getSessionDetail throws 404 when bearer holds no branch view`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    @Test
    fun `getSessionDetail allows global view holder with bearer`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = TestFixtures.uuid(),
        )

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertEquals(sessionId, data.session.id)
    }

    @Test
    fun `getSessionDetail revokes after direct grant removal while bearer row survives`() {
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
        grantBranchView(callerId, branchId)

        val before = DashboardService.getSessionDetail(callerId, sessionId)
        assertEquals(sessionId, before.session.id)

        IdentityFixtures.revokeAllCapabilities(callerId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    @Test
    fun `getSessionDetail revokes after role-derived assignment ends`() {
        val coordinator = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(coordinator, "revoked-coord")
        assignRole(coordinator, "COORDINATOR")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = coordinator,
            branchId = branchId,
            slot = 1,
            assignedBy = coordinator,
        )
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, coordinator, branchId)

        val before = DashboardService.getSessionDetail(coordinator, sessionId)
        assertEquals(sessionId, before.session.id)

        endAssignment(coordinator, branchId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(coordinator, sessionId)
        }
    }

    @Test
    fun `getSessionDetail revokes after coordinator role removal`() {
        val coordinator = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(coordinator, "role-stripped")
        assignRole(coordinator, "COORDINATOR")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = coordinator,
            branchId = branchId,
            slot = 1,
            assignedBy = coordinator,
        )
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, coordinator, branchId)

        val before = DashboardService.getSessionDetail(coordinator, sessionId)
        assertEquals(sessionId, before.session.id)

        transaction {
            UserRoleTable.deleteWhere { UserRoleTable.userId eq coordinator }
        }

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(coordinator, sessionId)
        }
    }

    private fun grantBranchView(
        userId: UUID,
        branchId: UUID,
    ) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId =
            transaction {
                RoleTable
                    .selectAll()
                    .where { RoleTable.name eq roleName }
                    .single()[RoleTable.id]
            }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    private fun endAssignment(
        userId: UUID,
        branchId: UUID,
    ) {
        transaction {
            UserBranchAssignmentTable.update(
                {
                    (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.branchId eq branchId)
                },
            ) {
                it[UserBranchAssignmentTable.endedAt] = CurrentTimestampWithTimeZone
            }
        }
    }

    private fun seedNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.notification.Notification {
        val notification =
            SessionClientFixtures.insertTestNotification(
                sessionId = sessionId,
                userId = userId,
                branchId = branchId,
            )
        return notification
    }

    private fun seedVoid(sessionId: UUID) {
        transaction {
            SessionVoidTable.insert {
                it[SessionVoidTable.id] = TestFixtures.uuid()
                it[SessionVoidTable.sessionId] = sessionId
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "test void"
            }
        }
    }

    private fun seedPractitioner(
        sessionId: UUID,
        practitionerId: UUID,
    ) {
        transaction {
            SessionPractitionerTable.insert {
                it[SessionPractitionerTable.sessionId] = sessionId
                it[SessionPractitionerTable.practitionerId] = practitionerId
                it[SessionPractitionerTable.remarks] = null
                it[SessionPractitionerTable.slotAtTime] = 1
            }
        }
    }
}
