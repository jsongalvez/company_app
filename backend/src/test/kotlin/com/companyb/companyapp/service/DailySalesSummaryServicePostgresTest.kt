package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
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
    private val callerId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchDayId = UUID.randomUUID()
    private val today = LocalDate.now(ZoneId.of("Asia/Manila"))

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Summary Test Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        insertBranchDay(branchDayId, branchId, today)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        trackOwned(SessionTable, SessionTable.branchDayId, branchDayId)
        trackOwned(CompensationTable, CompensationTable.workBranchDayId, branchDayId)
        trackOwned(CompensationTable, CompensationTable.payingBranchDayId, branchDayId)
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, branchDayId)
        trackOwned(ProductSaleTable, ProductSaleTable.branchDayId, branchDayId)
        trackOwned(CommissionSplitTable, CommissionSplitTable.branchDayId, branchDayId)
        trackOwned(SessionVoidTable, SessionVoidTable.voidedBy, callerId)
    }

    @Test
    fun `returns zero summary when no data exists for branch day`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

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
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val client1Id = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, client1Id)
        DatabaseTestHelper.insertTestSession(
            id = UUID.randomUUID(),
            clientId = client1Id,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        val client2Id = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, client2Id)
        DatabaseTestHelper.insertTestSession(
            id = UUID.randomUUID(),
            clientId = client2Id,
            branchDayId = branchDayId,
            sessionType = SessionType.SUBSEQUENT,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("1500.00"),
            finalPrice = BigDecimal("1500.00"),
        )

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("4000.00"), summary.grossIncome)
    }

    @Test
    fun `excludes non-completed sessions from gross income`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val testClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, testClientId)
        DatabaseTestHelper.insertTestSession(
            id = UUID.randomUUID(),
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.PENDING,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal.ZERO.setScale(2), summary.grossIncome)
    }

    @Test
    fun `excludes voided sessions from gross income`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val sessionId = UUID.randomUUID()
        val testClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, testClientId)
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        insertSessionVoid(sessionId)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
    }

    @Test
    fun `includes completed sessions that were unvoided`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val sessionId = UUID.randomUUID()
        val testClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, testClientId)
        DatabaseTestHelper.insertTestSession(
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

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal("2500.00").compareTo(summary.grossIncome))
    }

    @Test
    fun `returns correct total compensation`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(user1, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, user1)
        DatabaseTestHelper.insertTestUser(user2, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, user2)
        DatabaseTestHelper.insertTestCompensation(branchDayId, user1, BigDecimal("500.00"), assignedBy = callerId)
        DatabaseTestHelper.insertTestCompensation(branchDayId, user2, BigDecimal("300.00"), assignedBy = callerId)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("800.00"), summary.totalCompensation)
    }

    @Test
    fun `returns correct total expenses excluding deleted`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val userId = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(userId, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        DatabaseTestHelper.insertTestExpense(branchDayId, userId, BigDecimal("200.00"), deleted = false)
        DatabaseTestHelper.insertTestExpense(branchDayId, userId, BigDecimal("100.00"), deleted = true)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("200.00"), summary.totalExpenses)
    }

    @Test
    fun `returns correct product sales total`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val userId = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(userId, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        insertProductSale(branchDayId, userId, BigDecimal("300.00"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("300.00"), summary.totalProductSales)
    }

    @Test
    fun `returns correct commission total`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val userId = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(userId, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        insertCommissionSplit(branchDayId, userId, BigDecimal("150.0000"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal("150.0000").compareTo(summary.totalCommission))
    }

    @Test
    fun `calculates net income correctly`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val userId = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(userId, "summary-user")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        val netIncomeTestClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, netIncomeTestClientId)
        DatabaseTestHelper.insertTestSession(
            id = UUID.randomUUID(),
            clientId = netIncomeTestClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("5000.00"),
            finalPrice = BigDecimal("5000.00"),
        )
        DatabaseTestHelper.insertTestCompensation(branchDayId, userId, BigDecimal("1000.00"), assignedBy = callerId)
        DatabaseTestHelper.insertTestExpense(branchDayId, userId, BigDecimal("500.00"), deleted = false)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("5000.00"), summary.grossIncome)
        assertEquals(BigDecimal("1000.00"), summary.totalCompensation)
        assertEquals(BigDecimal("500.00"), summary.totalExpenses)
        assertEquals(BigDecimal("3500.00"), summary.netIncome)
    }

    @Test
    fun `throws 403 when lacking VIEW_BRANCH_DATA capability`() {
        assertFailsWith<ForbiddenResponse> {
            DailySalesSummaryService.getDailySummary(callerId, branchId, today)
        }
    }

    @Test
    fun `throws 404 when branch does not exist`() {
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val fakeBranchId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            DailySalesSummaryService.getDailySummary(callerId, fakeBranchId, today)
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
                it[SessionVoidTable.unvoidedAt] = OffsetDateTime.now()
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
        val productCategoryId = UUID.randomUUID()
        val productId = UUID.randomUUID()
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
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, productCategoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
    }

    private fun insertCommissionSplit(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CommissionSplitTable.insert {
                it[CommissionSplitTable.id] = UUID.randomUUID()
                it[CommissionSplitTable.branchDayId] = branchDayId
                it[CommissionSplitTable.userId] = userId
                it[CommissionSplitTable.amount] = amount
            }
        }
    }

    private fun grantViewBranchData(userId: UUID) {
        DatabaseTestHelper.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = userId,
        )
    }
}
