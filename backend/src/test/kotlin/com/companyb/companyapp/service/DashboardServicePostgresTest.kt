package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionUpsertParams
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.NotificationService
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.dashboard.DashboardService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val practitionerId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val saleId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        DatabaseTestHelper.insertTestUser(otherUserId, "other")
        DatabaseTestHelper.insertTestUser(practitionerId, "practitioner")
        DatabaseTestHelper.insertTestBranch(branchId, "Branch A")
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Branch B")
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)

        trackOwned(AppUserTable, AppUserTable.id, callerId)
        trackOwned(AppUserTable, AppUserTable.id, otherUserId)
        trackOwned(AppUserTable, AppUserTable.id, practitionerId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)

        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)
        trackOwned(SessionTable, SessionTable.branchDayId, branchDayId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        trackOwned(AttendanceTable, AttendanceTable.userId, callerId)
        trackOwned(AttendanceTable, AttendanceTable.userId, otherUserId)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, callerId)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, otherUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherUserId)
        trackOwned(ProductSaleTable, ProductSaleTable.branchDayId, branchDayId)
        trackOwned(CommissionManualInclusionTable, CommissionManualInclusionTable.productSaleId, saleId)
    }

    @Test
    fun `getToday returns enriched sessions with client name`() {
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)
        DatabaseTestHelper.insertTestSession(
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
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
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
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)
        DatabaseTestHelper.insertTestProductSale(
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
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)
        DatabaseTestHelper.insertTestProductSale(
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
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)
        DatabaseTestHelper.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = otherUserId,
            quantity = 1,
            commissionAmount = BigDecimal("50.00"),
        )
        CommissionManualInclusionRepository.upsert(
            CommissionManualInclusionUpsertParams(
                id = UUID.randomUUID(),
                productSaleId = saleId,
                userId = callerId,
                isIncluded = false,
                reason = null,
                assignedBy = callerId,
            ),
        )

        val data = DashboardService.getToday(callerId, branchId)

        assertEquals(BigDecimal.ZERO, data.commission.amount)
        assertEquals(0, data.commission.productSalesCount)
    }

    @Test
    fun `getToday returns empty day with zero commission`() {
        AttendanceService.clockIn(UUID.randomUUID(), branchId, callerId)

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
        AttendanceService.clockIn(UUID.randomUUID(), otherBranchId, callerId)

        assertFailsWith<ForbiddenException> {
            DashboardService.getToday(callerId, branchId)
        }
    }

    @Test
    fun `getToday throws NotFoundException for missing branch`() {
        assertFailsWith<NotFoundException> {
            DashboardService.getToday(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `getSessionDetail returns enriched session for notification bearer`() {
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
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
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, callerId, branchId)
        seedVoid(sessionId)

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertTrue(sessionId in data.voidedSessionIds)
    }

    @Test
    fun `getSessionDetail accepts read notifications`() {
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        val notification = seedNotification(sessionId, callerId, branchId)
        NotificationService.markRead(callerId, notification.id)

        val data = DashboardService.getSessionDetail(callerId, sessionId)

        assertEquals(sessionId, data.session.id)
    }

    @Test
    fun `getSessionDetail throws 404 when no notification exists for the session`() {
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    @Test
    fun `getSessionDetail throws 404 when caller is not the notification bearer`() {
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        seedNotification(sessionId, otherUserId, branchId)

        assertFailsWith<NotFoundException> {
            DashboardService.getSessionDetail(callerId, sessionId)
        }
    }

    private fun seedNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.repository.model.Notification {
        val id = UUID.randomUUID()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = "Test notification"
            }
        }
        trackOwned(NotificationTable, NotificationTable.id, id)
        return transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq id }
                .single()
                .let { row ->
                    com.companyb.companyapp.repository.model.Notification(
                        id = row[NotificationTable.id],
                        sessionId = row[NotificationTable.sessionId],
                        userId = row[NotificationTable.userId],
                        branchId = row[NotificationTable.branchId],
                        message = row[NotificationTable.message],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                    )
                }
        }
    }

    private fun seedVoid(sessionId: UUID) {
        transaction {
            SessionVoidTable.insert {
                it[SessionVoidTable.id] = UUID.randomUUID()
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
