package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.RemittanceRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class RemittanceLineServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val productCategoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val productSaleId = UUID.randomUUID()
    private lateinit var branchDayId: UUID
    private val rateId = UUID.randomUUID()
    private val secondSessionRateId = UUID.randomUUID()
    private val subsequentRateId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "rl")
        trackOwned(AppUserTable, AppUserTable.id, callerId)

        DatabaseTestHelper.insertTestBranch(branchId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)

        insertSessionBaseRate()
        insertSessionBaseRate(secondSessionRateId, SessionType.SECOND_SESSION)
        insertSessionBaseRate(subsequentRateId, SessionType.SUBSEQUENT)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, secondSessionRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)

        ensureBranchDay()

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `add SESSION line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
        assertEquals(sessionId, line.sessionId)
        assertEquals("1500.00", line.amount.toPlainString())

        val updatedRemittance = RemittanceRepository.findById(remittance.id)
        assertNotNull(updatedRemittance)
        assertEquals(remittance.version + 1, updatedRemittance.version)
    }

    @Test
    fun `add PRODUCT_SALE line succeeds`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        createProductSale()

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = null,
                productSaleId = productSaleId,
                amount = BigDecimal("500.00"),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.PRODUCT_SALE, line.type)
        assertEquals(productSaleId, line.productSaleId)
    }

    @Test
    fun `add line idempotent duplicate returns existing`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        val first =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        val second =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("2000.00"),
            )

        assertEquals(first.id, second.id)
        assertEquals(first.amount, second.amount)
    }

    @Test
    fun `add line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherUser)
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)

        val line =
            RemittanceService.addLine(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
    }

    @Test
    fun `add line to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundResponse> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = UUID.randomUUID(),
                id = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                sessionId = UUID.randomUUID(),
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
    }

    @Test
    fun `add line to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
    }

    @Test
    fun `add SESSION line without sessionId returns bad request`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                sessionId = null,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
    }

    @Test
    fun `add PRODUCT_SALE line with sessionId returns bad request`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `delete line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val afterAdd = RemittanceRepository.findById(remittance.id)!!

        val deleted = RemittanceService.removeLine(callerId, remittance.id, lineId)
        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)

        val afterDelete = RemittanceRepository.findById(remittance.id)
        assertNotNull(afterDelete)
        assertEquals(afterAdd.version + 1, afterDelete.version)
    }

    @Test
    fun `delete non-existent line returns not found`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        assertFailsWith<NotFoundResponse> {
            RemittanceService.removeLine(callerId, remittance.id, UUID.randomUUID())
        }
    }

    @Test
    fun `delete line from non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        markRemittanceSubmitted(remittance.id)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.removeLine(callerId, remittance.id, lineId)
        }
    }

    @Test
    fun `delete line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherUser)
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val deleted = RemittanceService.removeLine(otherUser, remittance.id, lineId)
        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)
    }

    @Test
    fun `add day breakdown succeeds`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
        assertEquals(branchDayId, breakdown.branchDayId)
    }

    @Test
    fun `add day breakdown duplicate returns existing`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        val breakdownId = UUID.randomUUID()

        val first =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = breakdownId,
                branchDayId = branchDayId,
            )

        val second =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = breakdownId,
                branchDayId = branchDayId,
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `add day breakdown without capability is allowed at service layer`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherUser)
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
    }

    @Test
    fun `add day breakdown to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundResponse> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = UUID.randomUUID(),
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `add day breakdown to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `get remittance detail returns lines and day breakdowns`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        createProductSale()

        val lineId = UUID.randomUUID()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittance.id,
            id = UUID.randomUUID(),
            branchDayId = branchDayId,
        )

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(remittance.id, detail.remittance.id)
        assertEquals(1, detail.lines.size)
        assertEquals(1, detail.dayBreakdowns.size)
        assertEquals(BigDecimal("1500.00"), detail.totalAmount)
    }

    @Test
    fun `get remittance with multiple lines calculates total correctly`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        completeSession(sessionId)
        val sessionId2 = UUID.randomUUID()
        createSession(sessionId2)
        trackOwned(SessionTable, SessionTable.id, sessionId2)
        val lineId1 = UUID.randomUUID()
        val lineId2 = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId1,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1000.00"),
        )

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId2,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId2,
            productSaleId = null,
            amount = BigDecimal("2000.00"),
        )

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(BigDecimal("3000.00"), detail.totalAmount)
    }

    @Test
    fun `deleted line is excluded from totals`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1000.00"),
        )

        RemittanceService.removeLine(callerId, remittance.id, lineId)

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(0, detail.lines.size)
        assertEquals(BigDecimal.ZERO, detail.totalAmount)
    }

    @Test
    fun `get remittance without capability is allowed at service layer`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)

        val detail = RemittanceService.getRemittance(remittance.id)

        assertEquals(remittance.id, detail.remittance.id)
        assertTrue(detail.lines.isEmpty())
    }

    @Test
    fun `get non-existent remittance returns not found`() {
        assertFailsWith<NotFoundResponse> {
            RemittanceService.getRemittance(UUID.randomUUID())
        }
    }

    @Test
    fun `add line writes audit log entry`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq "remittance_line")
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `delete line writes audit log entry`() {
        val remittance = createDraftRemittance()
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        createSession()
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val lineId = UUID.randomUUID()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        RemittanceService.removeLine(callerId, remittance.id, lineId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq "remittance_line") and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    private fun createDraftRemittance() =
        RemittanceService.createDraft(
            callerId = callerId,
            id = UUID.randomUUID(),
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )

    private fun createSession(sid: UUID = sessionId) {
        SessionService.create(
            callerId = callerId,
            id = sid,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            bookedAt = null,
            nextAppointmentDate = null,
        )
    }

    private fun createProductSale() {
        insertProductCategory()
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, productCategoryId)
        insertProduct()
        trackOwned(ProductTable, ProductTable.id, productId)
        ensureBranchInventory()
        ProductSaleService.sell(
            callerId = callerId,
            id = productSaleId,
            branchDayId = branchDayId,
            sessionId = sessionId,
            clientId = null,
            isWalkIn = false,
            productId = productId,
            quantity = 1,
            expectedVersion = 2,
        )
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId)
    }

    private fun ensureBranchInventory() {
        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        transaction {
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }) {
                it[BranchInventoryTable.currentStock] = 20
                it[BranchInventoryTable.version] = card.version + 1
            }
        }
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.branchId, branchId)
    }

    private fun insertSessionBaseRate(
        id: UUID = rateId,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        SessionBaseRateRepository.setRate(
            id = id,
            setBy = callerId,
            branchId = branchId,
            sessionType = sessionType,
            rate = BigDecimal("2500.00"),
            effectiveFrom = now,
            effectiveUntil = now.plusYears(10),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun insertProductCategory(changedBy: UUID = callerId) {
        ProductCategoryRepository.create(
            id = productCategoryId,
            name = "Test Category $productCategoryId",
            changedBy = callerId,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun insertProduct(changedBy: UUID = callerId) {
        ProductRepository.create(
            id = productId,
            name = "Test Product $productId",
            productCategoryId = productCategoryId,
            unitPrice = BigDecimal("500.00"),
            commissionAmount = BigDecimal("50.00"),
            changedBy = callerId,
        )
    }

    private fun ensureBranchDay() {
        val day = BranchDayService.resolveOrCreate(branchId, LocalDate.now())
        branchDayId = day.id
    }

    private fun completeSession(sid: UUID) {
        transaction {
            SessionTable.update({ SessionTable.id eq sid }) {
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
            }
        }
    }

    private fun markRemittanceSubmitted(remittanceId: UUID) {
        transaction {
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
            }
        }
    }
}
