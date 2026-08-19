@file:Suppress("LargeClass")

package com.companyb.companyapp.service

import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemittanceReadBackServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private var sessionId: UUID? = null
    private var productSaleId: UUID? = null
    private var sessionCreated = false
    private var productSaleCreated = false

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val branchDayDate = LocalDate.of(2026, 7, 10)

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "remittance-read")
        trackOwned(AppUserTable, AppUserTable.id, callerId)

        DatabaseTestHelper.insertTestBranch(branchId, "Test Remittance Branch ${UUID.randomUUID()}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Remittance Branch ${UUID.randomUUID()}")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)

        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    // ──────────────────────────────────────────────
    // G1 — draft list
    // ──────────────────────────────────────────────

    @Test
    fun `list returns only remittances of the branch`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val otherRemittanceId = UUID.randomUUID()
        RemittanceService.createDraft(
            callerId = callerId,
            id = otherRemittanceId,
            type = RemittanceType.SESSION,
            branchId = otherBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )

        val result = RemittanceService.listRemittances(branchId, null)

        assertEquals(1, result.size)
        assertEquals(remittanceId, result[0].remittance.id)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceTable, RemittanceTable.id, otherRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, otherRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, otherRemittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, otherRemittanceId)
    }

    @Test
    fun `list filters by status`() {
        val draftId = UUID.randomUUID()
        RemittanceService.createDraft(
            callerId = callerId,
            id = draftId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val submittedId = UUID.randomUUID()
        createSubmittedSessionRemittance(submittedId, BigDecimal("100.00"))

        val drafts = RemittanceService.listRemittances(branchId, RemittanceStatus.DRAFT)
        val submitted = RemittanceService.listRemittances(branchId, RemittanceStatus.SUBMITTED)
        val all = RemittanceService.listRemittances(branchId, null)

        assertEquals(listOf(draftId), drafts.map { it.remittance.id })
        assertEquals(listOf(submittedId), submitted.map { it.remittance.id })
        assertEquals(2, all.size)
        trackOwned(RemittanceTable, RemittanceTable.id, draftId)
        trackOwned(RemittanceTable, RemittanceTable.id, submittedId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, draftId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, submittedId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, draftId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, submittedId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, draftId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, submittedId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `list orders by createdAt desc with id desc tiebreak`() {
        val older = UUID.randomUUID()
        val newer = UUID.randomUUID()
        val olderCreatedAt = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC)
        val newerCreatedAt = olderCreatedAt.plusMinutes(5)
        insertDraftDirect(older, olderCreatedAt, LocalDate.of(2026, 7, 1))
        insertDraftDirect(newer, newerCreatedAt, LocalDate.of(2026, 7, 2))
        trackOwned(RemittanceTable, RemittanceTable.id, older)
        trackOwned(RemittanceTable, RemittanceTable.id, newer)

        val result = RemittanceService.listRemittances(branchId, null)

        assertEquals(listOf(newer, older), result.map { it.remittance.id })
    }

    @Test
    fun `list shows net income for submitted SESSION rows only`() {
        val sessionRemittanceId = UUID.randomUUID()
        val productRemittanceId = UUID.randomUUID()
        createSubmittedSessionRemittance(sessionRemittanceId, BigDecimal("500.00"))
        createSubmittedProductRemittance(productRemittanceId, BigDecimal("200.00"))

        val result = RemittanceService.listRemittances(branchId, null).associateBy { it.remittance.id }

        assertEquals(BigDecimal("500.00"), result.getValue(sessionRemittanceId).netIncome)
        assertNull(result.getValue(productRemittanceId).netIncome)
        trackOwned(RemittanceTable, RemittanceTable.id, sessionRemittanceId)
        trackOwned(RemittanceTable, RemittanceTable.id, productRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, sessionRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, productRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, sessionRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, productRemittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, sessionRemittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, productRemittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId!!)
    }

    @Test
    fun `list for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.listRemittances(UUID.randomUUID(), null)
        }
    }

    // ──────────────────────────────────────────────
    // G2 — sessions in range
    // ──────────────────────────────────────────────

    @Test
    fun `sessions in range returns client name price and time`() {
        val sId = createSession(branchDayDate)
        val otherBranchDayId = DatabaseTestHelper.createBranchDayForDate(otherBranchId, branchDayDate)
        val otherSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = otherSessionId,
            clientId = clientId,
            branchDayId = otherBranchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(sId, result[0].id)
        assertEquals("Test Client", result[0].clientName)
        assertEquals(BigDecimal("2500.00"), result[0].finalPrice)
        trackOwned(SessionTable, SessionTable.id, sId)
        trackOwned(SessionTable, SessionTable.id, otherSessionId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, otherBranchDayId)
    }

    @Test
    fun `sessions in range excludes out-of-range dates`() {
        createSession(branchDayDate)
        val outsideDayId = DatabaseTestHelper.createBranchDayForDate(branchId, LocalDate.of(2026, 8, 1))
        val outsideSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = outsideSessionId,
            clientId = clientId,
            branchDayId = outsideDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(SessionTable, SessionTable.id, outsideSessionId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, outsideDayId)
    }

    @Test
    fun `sessions in range returns null client name for anonymized client`() {
        val anonymizedClientId = UUID.randomUUID()
        DatabaseTestHelper.insertTestClient(anonymizedClientId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        val anonSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = anonSessionId,
            clientId = anonymizedClientId,
            branchDayId = dayId,
        )
        transaction {
            ClientTable.update({ ClientTable.id eq anonymizedClientId }) {
                it[ClientTable.firstName] = null
                it[ClientTable.lastName] = null
            }
        }

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertNull(result[0].clientName)
        trackOwned(SessionTable, SessionTable.id, anonSessionId)
        trackOwned(ClientTable, ClientTable.id, anonymizedClientId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    @Test
    fun `sessions in range excludes voided sessions`() {
        val sId = createSession(branchDayDate)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        val voidedSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = voidedSessionId,
            clientId = clientId,
            branchDayId = dayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        transaction {
            SessionVoidTable.insertIgnore {
                it[SessionVoidTable.sessionId] = voidedSessionId
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "Test void"
            }
        }
        trackOwned(SessionVoidTable, SessionVoidTable.voidedBy, callerId)

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(listOf(sId), result.map { it.id })
        trackOwned(SessionTable, SessionTable.id, sId)
        trackOwned(SessionTable, SessionTable.id, voidedSessionId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    @Test
    fun `sessions in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findSessionsInRange(UUID.randomUUID(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G3 — product sales in range
    // ──────────────────────────────────────────────

    @Test
    fun `product sales in range returns product name and amount`() {
        val psId = createProductSale(branchDayDate)
        val otherBranchDayId = DatabaseTestHelper.createBranchDayForDate(otherBranchId, branchDayDate)
        val otherPsId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val prodId = UUID.randomUUID()
        DatabaseTestHelper.insertTestCategory(catId, "Cat ${UUID.randomUUID()}")
        DatabaseTestHelper.insertTestProduct(prodId, "Other Product", catId)
        DatabaseTestHelper.insertTestProductSale(
            id = otherPsId,
            branchDayId = otherBranchDayId,
            productId = prodId,
            handledBy = callerId,
            productName = "Other Product",
        )

        val result = RemittanceService.findProductSalesInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(psId, result[0].id)
        assertEquals("Test Product", result[0].productName)
        assertEquals(BigDecimal("100.00"), result[0].totalAmountAtTime)
        trackOwned(ProductSaleTable, ProductSaleTable.id, psId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, otherPsId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, catId)
        trackOwned(ProductTable, ProductTable.id, prodId)
        trackOwned(BranchDayTable, BranchDayTable.id, otherBranchDayId)
    }

    @Test
    fun `product sales in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findProductSalesInRange(UUID.randomUUID(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G4 — branch days in range
    // ──────────────────────────────────────────────

    @Test
    fun `branch days in range applies lazy effective status`() {
        val today = LocalDate.now(BranchDayService.manilaZone)
        val openDayId = DatabaseTestHelper.createBranchDayForDate(branchId, today.plusDays(10))
        val pastOpenDayId = DatabaseTestHelper.createBranchDayForDate(branchId, today.minusDays(5))
        val remittedDayId = DatabaseTestHelper.createBranchDayForDate(branchId, today.minusDays(10))
        val submittedId = UUID.randomUUID()
        createDraftRemittance(submittedId)
        val breakdownId = UUID.randomUUID()
        RemittanceService.addDayBreakdown(callerId, submittedId, breakdownId, remittedDayId)
        val version = RemittanceService.getRemittance(submittedId).remittance.version
        RemittanceService.submit(callerId, submittedId, version)
        trackOwned(BranchDayTable, BranchDayTable.id, openDayId)
        trackOwned(BranchDayTable, BranchDayTable.id, pastOpenDayId)
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        trackOwned(RemittanceTable, RemittanceTable.id, submittedId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, submittedId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, submittedId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, submittedId)

        val result =
            RemittanceService
                .findBranchDaysInRange(
                    branchId,
                    today.minusDays(15),
                    today.plusDays(15),
                ).associateBy { it.date }

        assertEquals(DayStatus.REMITTED, result.getValue(today.minusDays(10)).status)
        assertEquals(DayStatus.PAST, result.getValue(today.minusDays(5)).status)
        assertEquals(DayStatus.OPEN, result.getValue(today.plusDays(10)).status)
    }

    @Test
    fun `branch days in range excludes days outside range and other branches`() {
        val outsideDayId = DatabaseTestHelper.createBranchDayForDate(branchId, LocalDate.of(2026, 9, 1))
        val otherBranchDayId = DatabaseTestHelper.createBranchDayForDate(otherBranchId, branchDayDate)

        val result = RemittanceService.findBranchDaysInRange(branchId, rangeStart, rangeEnd)

        assertTrue(result.isEmpty())
        trackOwned(BranchDayTable, BranchDayTable.id, outsideDayId)
        trackOwned(BranchDayTable, BranchDayTable.id, otherBranchDayId)
    }

    @Test
    fun `branch days in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findBranchDaysInRange(UUID.randomUUID(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G5 — day-breakdown DELETE
    // ──────────────────────────────────────────────

    @Test
    fun `remove day breakdown succeeds and writes audit row`() {
        val remittanceId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)

        val removed = RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)

        assertEquals(breakdownId, removed.id)
        assertTrue(RemittanceService.getRemittance(remittanceId).dayBreakdowns.isEmpty())
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.DELETE) and
                            (AuditLogTable.auditTableName eq RemittanceDayBreakdownTable.tableName) and
                            (AuditLogTable.recordId eq breakdownId)
                    }.count()
            }
        assertTrue(auditCount == 1L)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    @Test
    fun `remove day breakdown on submitted remittance throws validation`() {
        val remittanceId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("100.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        assertFailsWith<ValidationException> {
            RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `remove day breakdown with wrong parent returns not found`() {
        val remittanceId = UUID.randomUUID()
        val otherRemittanceId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        RemittanceService.createDraft(
            callerId = callerId,
            id = otherRemittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)

        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, otherRemittanceId, breakdownId)
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceTable, RemittanceTable.id, otherRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, otherRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, otherRemittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, otherRemittanceId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    @Test
    fun `remove missing day breakdown returns not found`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)

        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, remittanceId, UUID.randomUUID())
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `remove day breakdown on missing remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, UUID.randomUUID(), UUID.randomUUID())
        }
    }

    // ──────────────────────────────────────────────
    // G6 — snapshot block in detail
    // ──────────────────────────────────────────────

    @Test
    fun `detail includes snapshot for submitted SESSION remittance`() {
        val remittanceId = UUID.randomUUID()
        createSubmittedSessionRemittance(remittanceId, BigDecimal("500.00"))

        val detail = RemittanceService.getRemittance(remittanceId)

        assertNotNull(detail.snapshot)
        assertEquals(BigDecimal("500.00"), detail.snapshot.grossIncome)
        assertEquals(BigDecimal("500.00"), detail.snapshot.netIncome)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `detail has no snapshot for draft or PRODUCT remittance`() {
        val draftId = UUID.randomUUID()
        createDraftRemittance(draftId)
        val productId = UUID.randomUUID()
        createSubmittedProductRemittance(productId, BigDecimal("200.00"))

        assertNull(RemittanceService.getRemittance(draftId).snapshot)
        assertNull(RemittanceService.getRemittance(productId).snapshot)
        trackOwned(RemittanceTable, RemittanceTable.id, draftId)
        trackOwned(RemittanceTable, RemittanceTable.id, productId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, draftId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, productId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, draftId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, productId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, draftId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, productId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId!!)
    }

    // ──────────────────────────────────────────────
    // G7 — drift
    // ──────────────────────────────────────────────

    @Test
    fun `drift equals frozen when nothing changed after submit`() {
        val remittanceId = UUID.randomUUID()
        createSubmittedSessionRemittance(remittanceId, BigDecimal("1000.00"))

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal("1000.00"), drift.frozen.grossIncome)
        assertTrue(drift.frozen.totalCompensation.compareTo(drift.currentCompensation) == 0)
        assertTrue(drift.frozen.totalExpenses.compareTo(drift.currentExpenses) == 0)
        assertTrue(drift.frozen.netIncome.compareTo(drift.currentNet) == 0)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `drift reflects later compensation and expense changes`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val compId = UUID.randomUUID()
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = compId
                it[CompensationTable.workBranchDayId] = dayId
                it[CompensationTable.payingBranchDayId] = dayId
                it[CompensationTable.userId] = callerId
                it[CompensationTable.amount] = BigDecimal("200.00")
                it[CompensationTable.assignedBy] = callerId
            }
            ExpenseTable.insert {
                it[ExpenseTable.id] = UUID.randomUUID()
                it[ExpenseTable.branchDayId] = dayId
                it[ExpenseTable.amount] = BigDecimal("150.00")
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
            }
        }

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal("200.00"), drift.currentCompensation)
        assertEquals(BigDecimal("150.00"), drift.currentExpenses)
        assertEquals(BigDecimal("650.00"), drift.currentNet)
        assertEquals(BigDecimal("1000.00"), drift.frozen.netIncome)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
        trackOwned(CompensationTable, CompensationTable.assignedBy, callerId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, callerId)
    }

    @Test
    fun `drift excludes soft-deleted expenses`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val expenseId = UUID.randomUUID()
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = expenseId
                it[ExpenseTable.branchDayId] = dayId
                it[ExpenseTable.amount] = BigDecimal("300.00")
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
                it[ExpenseTable.deletedBy] = callerId
                it[ExpenseTable.deletedAt] = CurrentTimestampWithTimeZone
            }
        }

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal.ZERO, drift.currentExpenses)
        assertEquals(BigDecimal("1000.00"), drift.currentNet)
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, callerId)
    }

    @Test
    fun `drift returns not found without snapshot`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)

        assertFailsWith<NotFoundException> {
            RemittanceService.getDrift(remittanceId)
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `drift on missing remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.getDrift(UUID.randomUUID())
        }
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private fun createDraftRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
    }

    @Suppress("LongMethod")
    private fun createSubmittedSessionRemittance(
        remittanceId: UUID,
        lineAmount: BigDecimal,
    ) {
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, lineAmount)
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    private fun createSubmittedProductRemittance(
        remittanceId: UUID,
        lineAmount: BigDecimal,
    ) {
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addProductLine(remittanceId, lineId, lineAmount)
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
    }

    private fun insertDraftDirect(
        remittanceId: UUID,
        createdAt: OffsetDateTime,
        submittedDate: LocalDate,
    ) {
        // locals dodge the Exposed insert/update table receiver, which shadows test fields
        // that share names with columns (branchId, submittedDate, createdAt)
        val targetBranchId = branchId
        val targetSubmittedDate = submittedDate
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.branchId] = targetBranchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = targetSubmittedDate
                it[RemittanceTable.dateRangeStart] = rangeStart
                it[RemittanceTable.dateRangeEnd] = rangeEnd
                it[RemittanceTable.submittedBy] = callerId
            }
            val targetCreatedAt = createdAt
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.createdAt] = targetCreatedAt
            }
        }
    }

    private fun addSessionLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val sId = createSession(branchDayDate)
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sId,
            productSaleId = null,
            amount = amount,
        )
    }

    private fun addProductLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val psId = createProductSale(branchDayDate)
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.PRODUCT_SALE,
            sessionId = null,
            productSaleId = psId,
            amount = amount,
        )
    }

    private fun createSession(dayDate: LocalDate): UUID {
        if (sessionCreated) return sessionId!!
        DatabaseTestHelper.insertTestClient(clientId)
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDate)
        val sId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = dayId,
        )
        sessionId = sId
        sessionCreated = true
        return sId
    }

    private fun createProductSale(dayDate: LocalDate): UUID {
        if (productSaleCreated) return productSaleId!!
        val psId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val prodId = UUID.randomUUID()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDate)
        DatabaseTestHelper.insertTestCategory(catId, "Cat ${psId.toString().take(8)}")
        DatabaseTestHelper.insertTestProduct(prodId, "Prod ${psId.toString().take(8)}", catId)
        DatabaseTestHelper.insertTestProductSale(
            id = psId,
            branchDayId = dayId,
            productId = prodId,
            handledBy = callerId,
        )
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, catId)
        trackOwned(ProductTable, ProductTable.id, prodId)
        productSaleId = psId
        productSaleCreated = true
        return psId
    }
}
