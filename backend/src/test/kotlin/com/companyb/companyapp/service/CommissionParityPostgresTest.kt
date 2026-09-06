package com.companyb.companyapp.service

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.model.CommissionManualInclusionUpsertParams
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.dashboard.DashboardService
import com.companyb.companyapp.service.finance.commission.CommissionService
import com.companyb.companyapp.service.finance.commission.CommissionShare
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #497 parity between the persisted commission recalculation and the live dashboard card:
 * one batched aggregation serves both, so OPEN-day splits must equal the live view for the
 * same fixtures — fractional divisions, multiple attendance intervals, manual
 * inclusion/exclusion, zero-commission and voided sales included.
 */
class CommissionParityPostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val userA = TestFixtures.uuid()
    private val userB = TestFixtures.uuid()
    private val userC = TestFixtures.uuid()
    private val userD = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(userA, "parity-a")
        IdentityFixtures.insertTestUser(userB, "parity-b")
        IdentityFixtures.insertTestUser(userC, "parity-c")
        IdentityFixtures.insertTestUser(userD, "parity-d")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Parity Branch")
        SessionClientFixtures.insertTestClient(clientId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
    }

    @Test
    fun `persisted splits equal the live aggregation for mixed fixtures`() {
        val base = TestFixtures.realNow().atOffset(ZoneOffset.UTC)
        clockIn(branchDayId, userA, base.minusHours(5))
        clockIn(branchDayId, userB, base.minusHours(5), base.minusHours(3))
        clockIn(branchDayId, userB, base.minusHours(1))
        clockIn(branchDayId, userD, base.minusHours(5))

        sell(branchDayId, base.minusHours(4), BigDecimal("1.00"), 1)
        sell(branchDayId, base.minusHours(2), BigDecimal("10.00"), 2)
        val zeroSale = sell(branchDayId, base.minusMinutes(30), BigDecimal("0.00"), 3)
        val gatedSale = sell(branchDayId, base.minusMinutes(30), BigDecimal("30.00"), 1)
        include(zeroSale, userC, true)
        include(gatedSale, userD, false)

        val voidSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(voidSessionId, clientId, branchDayId)
        sell(
            branchDayId,
            base.minusMinutes(30),
            BigDecimal("999.00"),
            1,
            sessionId = voidSessionId,
        )
        transaction {
            SessionVoidTable.insert {
                it[SessionVoidTable.id] = TestFixtures.uuid()
                it[SessionVoidTable.sessionId] = voidSessionId
                it[SessionVoidTable.voidedBy] = userA
                it[SessionVoidTable.voidReason] = "parity void"
            }
        }

        CommissionService.recalculate(branchDayId)

        val persisted = CommissionService.getByBranchDayId(branchDayId).associate { it.userId to it.amount }
        val live = CommissionService.liveCommissions(branchDayId)

        assertEquals(setOf(userA, userB, userC, userD), persisted.keys)
        assertEquals(setOf(userA, userB, userC, userD), live.keys)
        for ((userId, share) in live) {
            assertEquals(persisted.getValue(userId), share.amount, "live/persisted drift for $userId")
        }
        assertEquals(BigDecimal("25.3333"), persisted.getValue(userA))
        assertEquals(BigDecimal("15.3333"), persisted.getValue(userB))
        assertEquals(BigDecimal("10.3333"), persisted.getValue(userD))
        assertEquals(BigDecimal("0.0000"), persisted.getValue(userC))
        assertEquals(4, live.getValue(userA).eligibleSaleCount)
        assertEquals(3, live.getValue(userB).eligibleSaleCount)
        assertEquals(3, live.getValue(userD).eligibleSaleCount)
        assertEquals(1, live.getValue(userC).eligibleSaleCount)

        val dashboard = DashboardService.getToday(userA, branchId)
        assertEquals(BigDecimal("25.3333"), dashboard.commission.amount)
        assertEquals(4, dashboard.commission.productSalesCount)
    }

    @Test
    fun `input reads stay bounded as sales grow`() {
        val base = TestFixtures.realNow().atOffset(ZoneOffset.UTC)
        val fewDay = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        val manyDay = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(2))

        clockIn(fewDay, userA, base.minusHours(5))
        sell(fewDay, base.minusHours(4), BigDecimal("10.00"), 1)

        clockIn(manyDay, userA, base.minusHours(5))
        clockIn(manyDay, userB, base.minusHours(5))
        val manySales =
            (1..5).map {
                sell(manyDay, base.minusHours(4), BigDecimal("10.00"), 1)
            }
        include(manySales[0], userC, true)
        include(manySales[1], userD, false)

        val (fewSelects, fewTotals) = captureInputSelects(fewDay)
        val (manySelects, manyTotals) = captureInputSelects(manyDay)

        assertInputReads(fewSelects)
        assertInputReads(manySelects)
        assertEquals(1, fewTotals.size)
        assertEquals(setOf(userA, userB, userC), manyTotals.keys)
    }

    @Test
    fun `automatic recalculation skips past days while force recalculates`() {
        val base = TestFixtures.realNow().atOffset(ZoneOffset.UTC)
        clockIn(branchDayId, userA, base.minusHours(5))
        sell(branchDayId, base.minusHours(4), BigDecimal("10.00"), 1)
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq branchDayId }) {
                it[BranchDayTable.status] = DayStatus.PAST
            }
        }

        CommissionService.recalculate(branchDayId)
        assertTrue(CommissionService.getByBranchDayId(branchDayId).isEmpty())

        CommissionService.manualRecalculate(branchDayId)
        val splits = CommissionService.getByBranchDayId(branchDayId)
        assertEquals(1, splits.size)
        assertEquals(BigDecimal("10.0000"), splits.single().amount)
    }

    @Test
    fun `empty-day recalculation removes stale splits`() {
        val base = TestFixtures.realNow().atOffset(ZoneOffset.UTC)
        clockIn(branchDayId, userA, base.minusHours(5))
        val saleId = sell(branchDayId, base.minusHours(4), BigDecimal("10.00"), 1)
        CommissionService.recalculate(branchDayId)
        assertEquals(1, CommissionService.getByBranchDayId(branchDayId).size)

        transaction {
            ProductSaleTable.deleteWhere { ProductSaleTable.id eq saleId }
        }
        CommissionService.recalculate(branchDayId)
        assertTrue(CommissionService.getByBranchDayId(branchDayId).isEmpty())
    }

    private fun assertInputReads(selects: List<String>) {
        assertEquals(3, selects.size, "expected exactly sales+attendance+inclusions reads, saw: $selects")
        assertEquals(1, selects.count { "FROM product_sale" in it }, "one sales read, saw: $selects")
        assertEquals(1, selects.count { "FROM attendance" in it }, "one attendance read, saw: $selects")
        assertEquals(
            1,
            selects.count { "FROM commission_manual_inclusion" in it },
            "one inclusions read, saw: $selects",
        )
    }

    private fun captureInputSelects(dayId: UUID): Pair<List<String>, Map<UUID, CommissionShare>> {
        val selects = mutableListOf<String>()
        val totals =
            transaction {
                registerInterceptor(
                    object : StatementInterceptor {
                        override fun beforeExecution(
                            transaction: Transaction,
                            context: StatementContext,
                        ) {
                            val sql = context.sql(transaction)
                            if (sql.startsWith("SELECT", ignoreCase = true)) selects.add(sql)
                        }
                    },
                )
                CommissionService.computeTotalsInTransaction(dayId)
            }
        return selects to totals
    }

    private fun clockIn(
        dayId: UUID,
        userId: UUID,
        clockIn: OffsetDateTime,
        clockOut: OffsetDateTime? = null,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = dayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = clockIn
                it[AttendanceTable.clockOut] = clockOut
            }
        }
    }

    private fun sell(
        dayId: UUID,
        soldAt: OffsetDateTime,
        commissionAmount: BigDecimal,
        quantity: Int,
        sessionId: UUID? = null,
    ): UUID {
        val saleId = TestFixtures.uuid()
        transaction {
            ProductSaleTable.insert {
                it[ProductSaleTable.id] = saleId
                it[ProductSaleTable.branchDayId] = dayId
                it[ProductSaleTable.productId] = this@CommissionParityPostgresTest.productId
                it[ProductSaleTable.productName] = "Parity Product"
                it[ProductSaleTable.handledBy] = userA
                it[ProductSaleTable.quantity] = quantity
                it[ProductSaleTable.unitPriceAtTime] = BigDecimal("100.00")
                it[ProductSaleTable.totalAmountAtTime] = BigDecimal("100.00")
                it[ProductSaleTable.commissionAmountAtTime] = commissionAmount
                it[ProductSaleTable.soldAt] = soldAt
                it[ProductSaleTable.isWalkIn] = sessionId == null
                if (sessionId != null) it[ProductSaleTable.sessionId] = sessionId
            }
        }
        return saleId
    }

    private fun include(
        saleId: UUID,
        userId: UUID,
        isIncluded: Boolean,
    ) {
        transaction {
            CommissionManualInclusionRepository.upsertInTransaction(
                CommissionManualInclusionUpsertParams(
                    id = TestFixtures.uuid(),
                    productSaleId = saleId,
                    userId = userId,
                    isIncluded = isIncluded,
                    reason = null,
                    assignedBy = userA,
                ),
            )
        }
    }
}
