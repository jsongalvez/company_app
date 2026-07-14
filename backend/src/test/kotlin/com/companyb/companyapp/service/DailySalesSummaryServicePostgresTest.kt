package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DailySalesSummaryServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchDayId = UUID.randomUUID()
    private val today = LocalDate.now(ZoneId.of("Asia/Manila"))

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(callerId)
        insertBranch(branchId, "Summary Test Branch")
        insertBranchDay(branchDayId, branchId, today)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `returns zero summary when no data exists for branch day`() {
        grantViewBranchData(callerId)

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
        insertSession(branchDayId, SessionType.REGULAR, SessionStatus.COMPLETED, BigDecimal("2500.00"))
        insertSession(branchDayId, SessionType.SUBSEQUENT, SessionStatus.COMPLETED, BigDecimal("1500.00"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("4000.00"), summary.grossIncome)
    }

    @Test
    fun `excludes non-completed sessions from gross income`() {
        grantViewBranchData(callerId)
        insertSession(branchDayId, SessionType.REGULAR, SessionStatus.PENDING, BigDecimal("2500.00"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal.ZERO.setScale(2), summary.grossIncome)
    }

    @Test
    fun `excludes voided sessions from gross income`() {
        grantViewBranchData(callerId)
        val sessionId = UUID.randomUUID()
        insertSession(
            branchDayId,
            SessionType.REGULAR,
            SessionStatus.COMPLETED,
            BigDecimal("2500.00"),
            sessionId = sessionId,
        )
        insertSessionVoid(sessionId)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
    }

    @Test
    fun `includes completed sessions that were unvoided`() {
        grantViewBranchData(callerId)
        val sessionId = UUID.randomUUID()
        insertSession(
            branchDayId,
            SessionType.REGULAR,
            SessionStatus.COMPLETED,
            BigDecimal("2500.00"),
            sessionId = sessionId,
        )
        insertSessionVoid(sessionId)
        unvoidSession(sessionId)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal("2500.00").compareTo(summary.grossIncome))
    }

    @Test
    fun `returns correct total compensation`() {
        grantViewBranchData(callerId)
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        insertUser(user1)
        insertUser(user2)
        insertCompensation(branchDayId, user1, BigDecimal("500.00"))
        insertCompensation(branchDayId, user2, BigDecimal("300.00"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("800.00"), summary.totalCompensation)
    }

    @Test
    fun `returns correct total expenses excluding deleted`() {
        grantViewBranchData(callerId)
        val userId = UUID.randomUUID()
        insertUser(userId)
        insertExpense(branchDayId, BigDecimal("200.00"), userId, deleted = false)
        insertExpense(branchDayId, BigDecimal("100.00"), userId, deleted = true)

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("200.00"), summary.totalExpenses)
    }

    @Test
    fun `returns correct product sales total`() {
        grantViewBranchData(callerId)
        val userId = UUID.randomUUID()
        insertUser(userId)
        insertProductSale(branchDayId, userId, BigDecimal("300.00"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(BigDecimal("300.00"), summary.totalProductSales)
    }

    @Test
    fun `returns correct commission total`() {
        grantViewBranchData(callerId)
        val userId = UUID.randomUUID()
        insertUser(userId)
        insertCommissionSplit(branchDayId, userId, BigDecimal("150.0000"))

        val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, today)

        assertEquals(0, BigDecimal("150.0000").compareTo(summary.totalCommission))
    }

    @Test
    fun `calculates net income correctly`() {
        grantViewBranchData(callerId)
        val userId = UUID.randomUUID()
        insertUser(userId)
        insertSession(branchDayId, SessionType.REGULAR, SessionStatus.COMPLETED, BigDecimal("5000.00"))
        insertCompensation(branchDayId, userId, BigDecimal("1000.00"))
        insertExpense(branchDayId, BigDecimal("500.00"), userId, deleted = false)

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
        val fakeBranchId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            DailySalesSummaryService.getDailySummary(callerId, fakeBranchId, today)
        }
    }

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "summary-user-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Summary User",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
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

    @Suppress("LongParameterList")
    private fun insertSession(
        branchDayId: UUID,
        sessionType: SessionType = SessionType.REGULAR,
        sessionStatus: SessionStatus = SessionStatus.COMPLETED,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
        sessionId: UUID = UUID.randomUUID(),
    ) {
        val clientId = UUID.randomUUID()
        transaction {
            ClientTable.insert {
                it[ClientTable.id] = clientId
                it[ClientTable.firstName] = "Test"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 30
            }
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = branchDayId
                it[SessionTable.sessionType] = sessionType
                it[SessionTable.sessionStatus] = sessionStatus
                it[SessionTable.basePrice] = finalPrice
                it[SessionTable.finalPrice] = finalPrice
                it[SessionTable.isWalkIn] = false
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

    private fun insertCompensation(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = UUID.randomUUID()
                it[CompensationTable.workBranchDayId] = branchDayId
                it[CompensationTable.payingBranchDayId] = branchDayId
                it[CompensationTable.userId] = userId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = callerId
            }
        }
    }

    private fun insertExpense(
        branchDayId: UUID,
        amount: BigDecimal,
        userId: UUID,
        deleted: Boolean = false,
    ) {
        transaction {
            val expenseId = UUID.randomUUID()
            ExpenseTable.insert {
                it[ExpenseTable.id] = expenseId
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = userId
                it[ExpenseTable.notes] = "Test expense"
                if (deleted) {
                    it[ExpenseTable.deletedBy] = userId
                    it[ExpenseTable.deletedAt] = OffsetDateTime.now()
                }
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
                it[ProductSaleTable.id] = UUID.randomUUID()
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
            capabilityCode = "VIEW_BRANCH_DATA",
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = userId,
        )
    }

    private fun deleteTestRows() {
        transaction {
            SessionVoidTable.deleteAll()
            SessionTable.deleteAll()
            ClientTable.deleteAll()
            CommissionSplitTable.deleteAll()
            ProductSaleTable.deleteAll()
            ProductTable.deleteAll()
            ProductCategoryTable.deleteAll()
            ExpenseTable.deleteAll()
            CompensationTable.deleteAll()
            BranchDayTable.deleteWhere {
                BranchDayTable.branchId eq branchId
            }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            UserCapabilityTable.deleteWhere {
                UserCapabilityTable.userId eq callerId
            }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }
}
