package com.companyb.companyapp.service
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DailySalesSummaryServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchDayId = TestFixtures.uuid()
    private val today = TestFixtures.today

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "summary-user")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Summary Test Branch")
        insertBranchDay(branchDayId, branchId, today)
    }

    @Test
    fun `returns zero summary when no data exists for branch day`() {
        grantViewBranchData(callerId)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalProductSales))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCommission))
    }

    @Test
    fun `returns correct gross income from completed sessions`() {
        grantViewBranchData(callerId)
        val client1Id = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = client1Id,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        val client2Id = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = client2Id,
            branchDayId = branchDayId,
            sessionType = SessionType.SUBSEQUENT,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("1500.00"),
            finalPrice = BigDecimal("1500.00"),
        )

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal("4000.00"), summary.grossIncome)
    }

    @Test
    fun `excludes non-completed sessions from gross income`() {
        grantViewBranchData(callerId)
        val testClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.PENDING,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal.ZERO.setScale(2), summary.grossIncome)
    }

    @Test
    fun `excludes voided sessions from gross income`() {
        grantViewBranchData(callerId)
        val sessionId = TestFixtures.uuid()
        val testClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        insertSessionVoid(sessionId)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
    }

    @Test
    fun `includes completed sessions that were unvoided`() {
        grantViewBranchData(callerId)
        val sessionId = TestFixtures.uuid()
        val testClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        insertSessionVoid(sessionId)
        unvoidSession(sessionId)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(0, BigDecimal("2500.00").compareTo(summary.grossIncome))
    }

    @Test
    fun `returns correct total compensation`() {
        grantViewBranchData(callerId)
        val user1 = TestFixtures.uuid()
        val user2 = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(user1, "summary-user")
        IdentityFixtures.insertTestUser(user2, "summary-user")
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, user1, BigDecimal("500.00"), assignedBy = callerId)
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, user2, BigDecimal("300.00"), assignedBy = callerId)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal("800.00"), summary.totalCompensation)
    }

    @Test
    fun `returns correct total expenses excluding deleted`() {
        grantViewBranchData(callerId)
        val userId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(userId, "summary-user")
        CommerceFinanceFixtures.insertTestExpense(branchDayId, userId, BigDecimal("200.00"), deleted = false)
        CommerceFinanceFixtures.insertTestExpense(branchDayId, userId, BigDecimal("100.00"), deleted = true)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal("200.00"), summary.totalExpenses)
    }

    @Test
    fun `returns correct product sales total`() {
        grantViewBranchData(callerId)
        val userId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(userId, "summary-user")
        insertProductSale(branchDayId, userId, BigDecimal("300.00"))

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal("300.00"), summary.totalProductSales)
    }

    @Test
    fun `returns correct commission total`() {
        grantViewBranchData(callerId)
        val userId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(userId, "summary-user")
        insertCommissionSplit(branchDayId, userId, BigDecimal("150.0000"))

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(0, BigDecimal("150.0000").compareTo(summary.totalCommission))
    }

    @Test
    fun `calculates net income correctly`() {
        grantViewBranchData(callerId)
        val userId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(userId, "summary-user")
        val netIncomeTestClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = netIncomeTestClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("5000.00"),
            finalPrice = BigDecimal("5000.00"),
        )
        CommerceFinanceFixtures.insertTestCompensation(
            branchDayId,
            userId,
            BigDecimal("1000.00"),
            assignedBy = callerId,
        )
        CommerceFinanceFixtures.insertTestExpense(branchDayId, userId, BigDecimal("500.00"), deleted = false)

        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(BigDecimal("5000.00"), summary.grossIncome)
        assertEquals(BigDecimal("1000.00"), summary.totalCompensation)
        assertEquals(BigDecimal("500.00"), summary.totalExpenses)
        assertEquals(BigDecimal("3500.00"), summary.netIncome)
    }

    @Test
    fun `returns zero summary without VIEW_BRANCH_DATA capability`() {
        val summary = DailySalesSummaryService.getDailySummary(branchId, today)

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalProductSales))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCommission))
    }

    @Test
    fun `throws 404 when branch does not exist`() {
        grantViewBranchData(callerId)
        val fakeBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            DailySalesSummaryService.getDailySummary(fakeBranchId, today)
        }
    }

    private fun insertBranchDay(
        id: UUID,
        branchId: UUID,
        date: LocalDate,
    ) {
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
            }
        }
    }

    private fun insertSessionVoid(sessionId: UUID) {
        transaction {
            SessionVoidTable.insertIgnore {
                it[SessionVoidTable.sessionId] = sessionId
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "Test void"
            }
        }
    }

    private fun unvoidSession(sessionId: UUID) {
        transaction {
            SessionVoidTable.update({
                SessionVoidTable.sessionId eq sessionId
            }) {
                it[SessionVoidTable.unvoidedAt] = TestFixtures.now
                it[SessionVoidTable.unvoidedBy] = callerId
                it[SessionVoidTable.unvoidedReason] = "Test unvoid"
            }
        }
    }

    private fun insertProductSale(
        branchDayId: UUID,
        userId: UUID,
        totalAmount: BigDecimal,
    ) {
        val productCategoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        transaction {
            ProductCategoryTable.insert {
                it[ProductCategoryTable.id] = productCategoryId
                it[ProductCategoryTable.name] = "Test Category $productCategoryId"
            }
            ProductTable.insert {
                it[ProductTable.id] = productId
                it[ProductTable.name] = "Test Product"
                it[ProductTable.productCategoryId] = productCategoryId
                it[ProductTable.unitPrice] = BigDecimal("100.00")
                it[ProductTable.commissionAmount] = BigDecimal.ZERO
            }
            ProductSaleTable.insert {
                it[ProductSaleTable.branchDayId] = branchDayId
                it[ProductSaleTable.productId] = productId
                it[ProductSaleTable.productName] = "Test Product"
                it[ProductSaleTable.handledBy] = userId
                it[ProductSaleTable.quantity] = 1
                it[ProductSaleTable.unitPriceAtTime] = totalAmount
                it[ProductSaleTable.totalAmountAtTime] = totalAmount
                it[ProductSaleTable.commissionAmountAtTime] = BigDecimal.ZERO
                it[ProductSaleTable.isWalkIn] = true
            }
        }
    }

    private fun insertCommissionSplit(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CommissionSplitTable.insert {
                it[CommissionSplitTable.id] = TestFixtures.uuid()
                it[CommissionSplitTable.branchDayId] = branchDayId
                it[CommissionSplitTable.userId] = userId
                it[CommissionSplitTable.amount] = amount
            }
        }
    }

    private fun grantViewBranchData(userId: UUID) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = userId,
        )
    }
}
