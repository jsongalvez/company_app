package com.companyb.companyapp.remittance
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemittanceLineServicePostgresTest : RemittanceLineServicePostgresBase() {
    @Test
    fun `add SESSION line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        createSession()

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
        assertEquals(sessionId, line.sessionId)
        assertEquals("1500.00", line.amount.toPlainString())

        val updatedRemittance = RemittanceService.getRemittance(remittance.id).remittance
        assertEquals(remittance.version + 1, updatedRemittance.version)
    }

    @Test
    fun `a Sessions remittance refuses a product sale`() {
        val remittance = createDraftRemittance(RemittanceType.SESSION)
        createSession()
        createProductSale()

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    NewRemittanceLine(
                        id = TestFixtures.uuid(),
                        type = RemittanceLineType.PRODUCT_SALE,
                        sessionId = null,
                        productSaleId = productSaleId,
                        amount = BigDecimal("500.00"),
                    ),
                )
            }
        assertEquals("This remittance is for sessions. Product sales go in a Product sales remittance.", error.message)
    }

    @Test
    fun `a draft that holds items keeps its kind`() {
        val remittance = createDraftRemittance(RemittanceType.SESSION)
        createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(),
        )
        val version = RemittanceService.getRemittance(remittance.id).remittance.version

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.updateHeader(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    RemittanceHeader(
                        type = RemittanceType.PRODUCT,
                        method = RemittanceMethod.BANK_TRANSFER,
                        dateRangeStart = remittance.dateRangeStart,
                        dateRangeEnd = remittance.dateRangeEnd,
                    ),
                    expectedVersion = version,
                )
            }
        assertEquals("Remove the included items before changing the kind.", error.message)
        assertEquals(RemittanceType.SESSION, RemittanceService.getRemittance(remittance.id).remittance.type)
    }

    @Test
    fun `add PRODUCT_SALE line succeeds`() {
        val remittance = createDraftRemittance(RemittanceType.PRODUCT)
        createSession()
        createProductSale()

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                NewRemittanceLine(
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = null,
                    productSaleId = productSaleId,
                    amount = BigDecimal("500.00"),
                ),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.PRODUCT_SALE, line.type)
        assertEquals(productSaleId, line.productSaleId)
    }

    @Test
    fun `add line rejects session and product sale from another branch`() {
        val remittance = createDraftRemittance()

        val foreign = seedForeignBranchSource()
        createSession()
        createProductSale()
        moveProductSaleToBranchDay(productSaleId, foreign.branchDayId)

        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                NewRemittanceLine(
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.SESSION,
                    sessionId = foreign.sessionId,
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                ),
            )
        }
        val productRemittance = createDraftRemittance(RemittanceType.PRODUCT)
        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = productRemittance.id,
                NewRemittanceLine(
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = null,
                    productSaleId = productSaleId,
                    amount = BigDecimal("500.00"),
                ),
            )
        }

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add SESSION line rejects source outside remittance range`() {
        val remittance = createDraftRemittance()
        val outOfRangeDay = BranchDayService.resolveOrCreate(branchId, LocalDate.of(2026, 6, 1))
        val outOfRangeSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = outOfRangeSessionId,
            clientId = clientId,
            branchDayId = outOfRangeDay.id,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    NewRemittanceLine(
                        id = TestFixtures.uuid(),
                        type = RemittanceLineType.SESSION,
                        sessionId = outOfRangeSessionId,
                        productSaleId = null,
                        amount = BigDecimal("1500.00"),
                    ),
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add PRODUCT_SALE line rejects source outside remittance range`() {
        val remittance = createDraftRemittance(RemittanceType.PRODUCT)
        val outOfRangeDay = BranchDayService.resolveOrCreate(branchId, LocalDate.of(2026, 6, 1))
        insertProductCategory()
        insertProduct()
        val outOfRangeSaleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = outOfRangeSaleId,
            branchDayId = outOfRangeDay.id,
            productId = productId,
            handledBy = callerId,
        )

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    NewRemittanceLine(
                        id = TestFixtures.uuid(),
                        type = RemittanceLineType.PRODUCT_SALE,
                        sessionId = null,
                        productSaleId = outOfRangeSaleId,
                        amount = BigDecimal("500.00"),
                    ),
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add SESSION line rejects voided source`() {
        val remittance = createDraftRemittance()
        createSession()
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    sessionLine(),
                )
            }
        assertTrue(checkNotNull(error.message).contains("voided", ignoreCase = true))

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add SESSION line succeeds after unvoid`() {
        val remittance = createDraftRemittance()
        createSession()
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")
        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(),
            )

        assertNotNull(line)
        assertEquals(sessionId, line.sessionId)
    }

    @Test
    fun `add SESSION line rejects non-COMPLETED source statuses`() {
        val remittance = createDraftRemittance()
        val statuses =
            listOf(
                SessionStatus.PENDING,
                SessionStatus.NO_SHOW,
                SessionStatus.CANCELLED,
            )
        statuses.forEach { status ->
            val statusClientId = SessionClientFixtures.insertTestClient()
            val statusSessionId = TestFixtures.uuid()
            SessionClientFixtures.insertTestSession(
                id = statusSessionId,
                clientId = statusClientId,
                branchDayId = branchDayId,
                sessionStatus = status,
            )

            val error =
                assertFailsWith<ValidationException> {
                    RemittanceService.addLine(
                        callerId = callerId,
                        remittanceId = remittance.id,
                        NewRemittanceLine(
                            id = TestFixtures.uuid(),
                            type = RemittanceLineType.SESSION,
                            sessionId = statusSessionId,
                            productSaleId = null,
                            amount = BigDecimal("1500.00"),
                        ),
                    )
                }
            assertTrue(
                checkNotNull(error.message).contains("COMPLETED"),
                "expected COMPLETED-only rejection for $status, got: ${error.message}",
            )
        }

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `session picker excludes non-COMPLETED sessions`() {
        val completedClientId = SessionClientFixtures.insertTestClient()
        val completedSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = completedSessionId,
            clientId = completedClientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        listOf(
            SessionStatus.PENDING,
            SessionStatus.NO_SHOW,
            SessionStatus.CANCELLED,
        ).forEach { status ->
            SessionClientFixtures.insertTestSession(
                id = TestFixtures.uuid(),
                clientId = SessionClientFixtures.insertTestClient(),
                branchDayId = branchDayId,
                sessionStatus = status,
            )
        }

        val result =
            RemittanceService.findSessionsInRange(
                branchId = branchId,
                from = LocalDate.of(2026, 7, 1),
                to = TestFixtures.today,
            )

        assertEquals(listOf(completedSessionId), result.map { it.id })
    }

    // #925 persisted-range pre-gate (the #924 fail-closed class): remittance_line.amount
    // is NUMERIC(10,2) and caller-chosen, so overflow must 400 before the write.
    @Test
    fun `add SESSION line rejects amount over NUMERIC(10,2) range with no row written`() {
        val remittance = createDraftRemittance()
        createSession()
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(blockedId, BigDecimal("9999999999.99")),
            )
        }
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
    }

    private data class ForeignBranchSource(
        val branchDayId: UUID,
        val sessionId: UUID,
    )

    private fun seedForeignBranchSource(): ForeignBranchSource {
        val foreignBranchId = TestFixtures.uuid()
        val foreignClientId = TestFixtures.uuid()
        val foreignBranchDayId = TestFixtures.uuid()
        val foreignSessionId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(foreignBranchId, "Foreign Remittance Source")
        SessionClientFixtures.insertTestClient(foreignClientId)
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = foreignBranchDayId
                it[BranchDayTable.branchId] = foreignBranchId
                it[BranchDayTable.date] = LocalDate.of(2026, 7, 10)
            }
            SessionTable.insert {
                it[SessionTable.id] = foreignSessionId
                it[SessionTable.clientId] = foreignClientId
                it[SessionTable.branchDayId] = foreignBranchDayId
                it[SessionTable.sessionType] = SessionType.REGULAR
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
                it[SessionTable.isWalkIn] = false
                it[SessionTable.basePrice] = BigDecimal("2500.00")
                it[SessionTable.finalPrice] = BigDecimal("2500.00")
            }
        }
        return ForeignBranchSource(foreignBranchDayId, foreignSessionId)
    }

    private fun moveProductSaleToBranchDay(
        saleId: UUID,
        branchDayId: UUID,
    ) {
        transaction {
            ProductSaleTable.update({ ProductSaleTable.id eq saleId }) {
                it[ProductSaleTable.branchDayId] = branchDayId
            }
        }
    }
}
