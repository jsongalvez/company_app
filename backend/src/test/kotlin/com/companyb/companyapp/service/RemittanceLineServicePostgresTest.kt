package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.ProductCreateParams
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceDayBreakdown
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.session.SessionBaseRateService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class RemittanceLineServicePostgresTest : BasePostgresTest() {
    private companion object {
        const val CONCURRENT_BREAKDOWNS = 2
        const val CONCURRENT_LINES = 2
        const val EXECUTOR_TERMINATION_SECONDS = 10L
    }

    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val productCategoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val productSaleId = TestFixtures.uuid()
    private lateinit var branchDayId: UUID
    private val rateId = TestFixtures.uuid()
    private val secondSessionRateId = TestFixtures.uuid()
    private val subsequentRateId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "rl")

        DatabaseTestHelper.insertTestBranch(branchId)

        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        DatabaseTestHelper.insertTestClient(clientId)

        insertSessionBaseRate()
        insertSessionBaseRate(secondSessionRateId, SessionType.SECOND_SESSION)
        insertSessionBaseRate(subsequentRateId, SessionType.SUBSEQUENT)

        ensureBranchDay()
    }

    @Test
    fun `add SESSION line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        createSession()

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
        assertEquals(sessionId, line.sessionId)
        assertEquals("1500.00", line.amount.toPlainString())

        val updatedRemittance = RemittanceService.getRemittance(remittance.id).remittance
        assertEquals(remittance.version + 1, updatedRemittance.version)
    }

    @Test
    fun `add PRODUCT_SALE line succeeds`() {
        val remittance = createDraftRemittance()
        createSession()
        createProductSale()

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
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
    @Suppress("LongMethod")
    fun `add line rejects session and product sale from another branch`() {
        val remittance = createDraftRemittance()

        val foreignBranchId = TestFixtures.uuid()
        val foreignClientId = TestFixtures.uuid()
        val foreignBranchDayId = TestFixtures.uuid()
        val foreignSessionId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(foreignBranchId, "Foreign Remittance Source")
        DatabaseTestHelper.insertTestClient(foreignClientId)
        createSession()
        createProductSale()
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
            ProductSaleTable.update({ ProductSaleTable.id eq productSaleId }) {
                it[ProductSaleTable.branchDayId] = foreignBranchDayId
            }
        }

        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId,
                remittance.id,
                TestFixtures.uuid(),
                RemittanceLineType.SESSION,
                foreignSessionId,
                null,
                BigDecimal("1500.00"),
            )
        }
        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId,
                remittance.id,
                TestFixtures.uuid(),
                RemittanceLineType.PRODUCT_SALE,
                null,
                productSaleId,
                BigDecimal("500.00"),
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
        DatabaseTestHelper.insertTestSession(
            id = outOfRangeSessionId,
            clientId = clientId,
            branchDayId = outOfRangeDay.id,
        )

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.SESSION,
                    sessionId = outOfRangeSessionId,
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add PRODUCT_SALE line rejects source outside remittance range`() {
        val remittance = createDraftRemittance()
        val outOfRangeDay = BranchDayService.resolveOrCreate(branchId, LocalDate.of(2026, 6, 1))
        insertProductCategory()
        insertProduct()
        val outOfRangeSaleId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProductSale(
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
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = null,
                    productSaleId = outOfRangeSaleId,
                    amount = BigDecimal("500.00"),
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))

        assertEquals(remittance.version, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(remittance.id).lines.isEmpty())
        assertEquals(0, remittanceLineAuditCount())
    }

    @Test
    fun `add line idempotent duplicate returns existing`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

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
        val versionAfterFirst = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountAfterFirst = remittanceLineAuditCount()

        val second =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        assertEquals(first.id, second.id)
        assertEquals(first.amount, second.amount)
        assertEquals(versionAfterFirst, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountAfterFirst, remittanceLineAuditCount())
    }

    @Test
    fun `add line rejects altered UUID retry payload`() {
        val remittance = createDraftRemittance()
        createSession()
        val secondSessionId = TestFixtures.uuid()
        completeSession(sessionId)
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("500.00"),
            )
        }

        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line rejects altered UUID retry source and creator`() {
        val remittance = createDraftRemittance()
        createSession()
        val secondSessionId = TestFixtures.uuid()
        completeSession(sessionId)
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = secondSessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }

        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line concurrent same UUID returns one line and audit`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(CONCURRENT_LINES)
        val ready = CountDownLatch(CONCURRENT_LINES)
        val start = CountDownLatch(1)
        val futures =
            (1..CONCURRENT_LINES).map {
                executor.submit<UUID> {
                    ready.countDown()
                    start.await()
                    val line =
                        RemittanceService.addLine(
                            callerId = callerId,
                            remittanceId = remittance.id,
                            id = lineId,
                            type = RemittanceLineType.SESSION,
                            sessionId = sessionId,
                            productSaleId = null,
                            amount = BigDecimal("1500.00"),
                        )
                    line.id
                }
            }

        try {
            assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, futures.map { it.get() }.toSet().size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals(1, remittanceLineAuditCount())
        assertEquals(1, RemittanceService.getRemittance(remittance.id).lines.size)
    }

    @Test
    fun `add line rejects client ID already used by another remittance`() {
        val firstRemittance = createDraftRemittance()
        val secondRemittance = createDraftRemittance(RemittanceType.PRODUCT)
        createSession()
        completeSession(sessionId)
        val secondSessionId = TestFixtures.uuid()
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = firstRemittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )
        val firstVersion = RemittanceService.getRemittance(firstRemittance.id).remittance.version
        val versionBefore = RemittanceService.getRemittance(secondRemittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = secondRemittance.id,
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = secondSessionId,
                productSaleId = null,
                amount = BigDecimal("2000.00"),
            )
        }

        assertEquals(firstVersion, RemittanceService.getRemittance(firstRemittance.id).remittance.version)
        assertEquals(versionBefore, RemittanceService.getRemittance(secondRemittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(secondRemittance.id).lines.isEmpty())
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()
        createSession()

        val line =
            RemittanceService.addLine(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
    }

    @Test
    fun `add SESSION line already in another draft returns conflict`() {
        val firstDraft = createDraftRemittance()
        val secondDraft = createDraftRemittance(RemittanceType.PRODUCT)
        createSession()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = firstDraft.id,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val error =
            assertFailsWith<ConflictException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = secondDraft.id,
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.SESSION,
                    sessionId = sessionId,
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                )
            }
        assertEquals("Session already included in a remittance line", error.message)
    }

    @Test
    fun `add SESSION line duplicate in same draft with different line id returns conflict`() {
        val remittance = createDraftRemittance()
        createSession()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val versionBeforeConflict =
            transaction {
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittance.id }
                    .single()[RemittanceTable.version]
            }
        val auditCountBeforeConflict =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq "remittance_line" }
                    .count()
            }

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }

        val versionAfterConflict =
            transaction {
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittance.id }
                    .single()[RemittanceTable.version]
            }
        val auditCountAfterConflict =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq "remittance_line" }
                    .count()
            }
        assertEquals(versionBeforeConflict, versionAfterConflict)
        assertEquals(auditCountBeforeConflict, auditCountAfterConflict)
    }

    @Test
    fun `add PRODUCT_SALE line duplicate returns conflict`() {
        val remittance = createDraftRemittance()
        createSession()
        createProductSale()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.PRODUCT_SALE,
            sessionId = null,
            productSaleId = productSaleId,
            amount = BigDecimal("500.00"),
        )

        val error =
            assertFailsWith<ConflictException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = null,
                    productSaleId = productSaleId,
                    amount = BigDecimal("500.00"),
                )
            }
        assertEquals("Product sale already included in a remittance line", error.message)
    }

    @Test
    fun `add SESSION line after soft-delete of previous line succeeds`() {
        val remittance = createDraftRemittance()
        createSession()

        val firstLineId = TestFixtures.uuid()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = firstLineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )
        RemittanceService.removeLine(callerId, remittance.id, firstLineId)

        val reAdded =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("2000.00"),
            )

        assertNotNull(reAdded)
        assertEquals("2000.00", reAdded.amount.toPlainString())
    }

    @Test
    fun `add line to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = TestFixtures.uuid(),
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = TestFixtures.uuid(),
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
    }

    @Test
    fun `add line to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        createSession()
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<ValidationException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = sessionId,
                productSaleId = null,
                amount = BigDecimal("1500.00"),
            )
        }
    }

    @Test
    fun `delete line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )

        val afterAdd = requireNotNull(RemittanceService.getRemittance(remittance.id).remittance)

        val deleted = RemittanceService.removeLine(callerId, remittance.id, lineId)
        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)

        val afterDelete = RemittanceService.getRemittance(remittance.id).remittance
        assertEquals(afterAdd.version + 1, afterDelete.version)
    }

    @Test
    fun `delete non-existent line returns not found`() {
        val remittance = createDraftRemittance()

        assertFailsWith<NotFoundException> {
            RemittanceService.removeLine(callerId, remittance.id, TestFixtures.uuid())
        }
    }

    @Test
    fun `delete line from non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

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

        assertFailsWith<ValidationException> {
            RemittanceService.removeLine(callerId, remittance.id, lineId)
        }
    }

    @Test
    fun `delete line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

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

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
        assertEquals(branchDayId, breakdown.branchDayId)
    }

    @Test
    fun `add day breakdown duplicate returns existing`() {
        val remittance = createDraftRemittance()
        val breakdownId = TestFixtures.uuid()

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
    fun `add day breakdown concurrent distinct IDs return existing`() {
        val remittance = createDraftRemittance()
        val executor = Executors.newFixedThreadPool(CONCURRENT_BREAKDOWNS)
        val ready = CountDownLatch(CONCURRENT_BREAKDOWNS)
        val start = CountDownLatch(1)
        val breakdownIds = (1..CONCURRENT_BREAKDOWNS).map { TestFixtures.uuid() }
        val futures =
            breakdownIds.map { breakdownId ->
                executor.submit<RemittanceDayBreakdown> {
                    ready.countDown()
                    start.await()
                    RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, branchDayId)
                }
            }

        val results =
            try {
                assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get() }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            }

        assertEquals(1, results.map { it.id }.toSet().size)
        assertEquals(1, remittanceDayBreakdownAuditCount())
        assertEquals(1, RemittanceService.getRemittance(remittance.id).dayBreakdowns.size)
    }

    @Test
    fun `add day breakdown rejects client ID already used by another remittance`() {
        val firstRemittance = createDraftRemittance()
        val secondRemittance = createDraftRemittance(RemittanceType.PRODUCT)
        val breakdownId = TestFixtures.uuid()

        RemittanceService.addDayBreakdown(callerId, firstRemittance.id, breakdownId, branchDayId)

        assertFailsWith<ConflictException> {
            RemittanceService.addDayBreakdown(callerId, secondRemittance.id, breakdownId, branchDayId)
        }
        assertTrue(RemittanceService.getRemittance(secondRemittance.id).dayBreakdowns.isEmpty())
        assertEquals(1, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects same ID for another Branch Day`() {
        val remittance = createDraftRemittance()
        val otherDayId = BranchDayService.resolveOrCreate(branchId, TestFixtures.today.minusDays(1)).id
        val breakdownId = TestFixtures.uuid()

        RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, branchDayId)

        assertFailsWith<ConflictException> {
            RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, otherDayId)
        }
        assertEquals(1, RemittanceService.getRemittance(remittance.id).dayBreakdowns.size)
        assertEquals(1, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects foreign branch day`() {
        val foreignBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(foreignBranchId)
        val foreignDay = BranchDayService.resolveOrCreate(foreignBranchId, TestFixtures.today)
        val remittance = createDraftRemittance()
        assertFailsWith<NotFoundException> {
            RemittanceService.addDayBreakdown(callerId, remittance.id, TestFixtures.uuid(), foreignDay.id)
        }
        assertTrue(RemittanceService.getRemittance(remittance.id).dayBreakdowns.isEmpty())
        assertEquals(0, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects day outside remittance range`() {
        val remittance = createDraftRemittance()
        val outOfRangeDay = BranchDayService.resolveOrCreate(branchId, LocalDate.of(2026, 6, 1))

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addDayBreakdown(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    id = TestFixtures.uuid(),
                    branchDayId = outOfRangeDay.id,
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))
        assertTrue(RemittanceService.getRemittance(remittance.id).dayBreakdowns.isEmpty())
        assertEquals(0, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `update header rejects range excluding existing line and day`() {
        val remittance = createDraftRemittance()
        createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("1500.00"),
        )
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            branchDayId = branchDayId,
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version

        // Both fixtures sit on the operational today; a range strictly before today orphans them.
        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.updateHeader(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    type = RemittanceType.SESSION,
                    method = RemittanceMethod.BANK_TRANSFER,
                    dateRangeStart = TestFixtures.today.minusDays(60),
                    dateRangeEnd = TestFixtures.today.minusDays(30),
                    expectedVersion = versionBefore,
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))
        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
    }

    @Test
    fun `add day breakdown without capability is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
    }

    @Test
    fun `add day breakdown to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = TestFixtures.uuid(),
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `add day breakdown to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<ValidationException> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `get remittance detail returns lines and day breakdowns`() {
        val remittance = createDraftRemittance()
        createSession()
        createProductSale()

        val lineId = TestFixtures.uuid()
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
            id = TestFixtures.uuid(),
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
        createSession()
        completeSession(sessionId)
        val sessionId2 = TestFixtures.uuid()
        createSession(sessionId2)
        val lineId1 = TestFixtures.uuid()
        val lineId2 = TestFixtures.uuid()

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
        createSession()
        val lineId = TestFixtures.uuid()

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
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()

        val detail = RemittanceService.getRemittance(remittance.id)

        assertEquals(remittance.id, detail.remittance.id)
        assertTrue(detail.lines.isEmpty())
    }

    @Test
    fun `get non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.getRemittance(TestFixtures.uuid())
        }
    }

    @Test
    fun `add line writes audit log entry`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

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
        createSession()
        val lineId = TestFixtures.uuid()

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

    private fun createDraftRemittance(type: RemittanceType = RemittanceType.SESSION) =
        RemittanceService.createDraft(
            callerId = callerId,
            id = TestFixtures.uuid(),
            type = type,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            // #483 — the range must cover the operational today the session/sale fixtures
            // land on; out-of-range writes are rejected, so a fixed July window would fail.
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = TestFixtures.today,
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
            nextAppointmentDate = null,
        )
    }

    private fun createProductSale() {
        insertProductCategory()
        insertProduct()
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
    }

    private fun ensureBranchInventory() {
        val card = InventoryService.ensureCard(callerId, branchId, productId)
        transaction {
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }) {
                it[BranchInventoryTable.currentStock] = 20
                it[BranchInventoryTable.version] = card.version + 1
            }
        }
    }

    private fun insertSessionBaseRate(
        id: UUID = rateId,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        SessionBaseRateService.setRate(
            callerId = callerId,
            id = id,
            branchId = branchId,
            sessionType = sessionType,
            rate = BigDecimal("2500.00"),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun insertProductCategory(changedBy: UUID = callerId) {
        transaction {
            ProductCategoryRepository.createInTransaction(
                id = productCategoryId,
                name = "Test Category $productCategoryId",
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun insertProduct(changedBy: UUID = callerId) {
        transaction {
            ProductRepository.createInTransaction(
                ProductCreateParams(
                    id = productId,
                    name = "Test Product $productId",
                    productCategoryId = productCategoryId,
                    unitPrice = BigDecimal("500.00"),
                    commissionAmount = BigDecimal("50.00"),
                    changedBy = callerId,
                ),
            )
        }
    }

    private fun ensureBranchDay() {
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
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

    private fun remittanceLineAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.auditTableName eq RemittanceLineTable.tableName }
                .count()
        }

    private fun remittanceDayBreakdownAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.auditTableName eq RemittanceDayBreakdownTable.tableName }
                .count()
        }
}
