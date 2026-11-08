package com.companyb.companyapp.reporting

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commission.CommissionSplitTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.remittance.RemittanceDayBreakdownTable
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.remittance.RemittanceLineTable
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Shared export-test bed (RED repair split): [ExportServicePostgresTest] grew past
 * the LargeClass pin, so the daily/range and monthly/branch-type suites live in
 * separate classes sharing this bed. No behavior change — fields, seeds, and
 * remittance helpers moved verbatim.
 */
abstract class ExportServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val sourceId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val branchDayId = TestFixtures.uuid()
    protected val today = TestFixtures.today

    override fun initTestData() {
        IdentityFixtures.insertUser(
            id = callerId,
            username = "export-user-${callerId.toString().take(8)}",
            passwordHash = "hash",
            email = "${callerId.toString().take(8)}@test.com",
            displayName = "Export User",
        )
        BranchWorkforceFixtures.insertTestBranch(
            branchId,
            "Export Test Branch ${TestFixtures.uuid()}",
            BranchType.CLINIC,
        )
        insertBranchDay(branchDayId, branchId, today)
        grantViewBranchData(callerId)
    }

    protected fun seedFormulaBranch(name: String) {
        val id = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(id, name, BranchType.PROVINCIAL_TOUR)
        insertBranchDay(TestFixtures.uuid(), id, today)
        if (name == "lone\rcr") {
            createSubmittedRemittanceForBranch(id, BigDecimal("1000.00"), BigDecimal("800.00"), BigDecimal("500.00"))
        } else {
            createSubmittedRemittanceForBranch(id, BigDecimal("2000.00"), BigDecimal("400.00"), BigDecimal("100.00"))
        }
    }

    protected fun grantViewBranchData(userId: UUID) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    protected data class DayFinancials(
        val gross: BigDecimal,
        val comp: BigDecimal,
        val expense: BigDecimal,
        val productSales: BigDecimal,
        val commission: BigDecimal,
    )

    protected fun seedDayFinancials(
        dayId: UUID,
        financials: DayFinancials,
    ) {
        val clientId = TestFixtures.uuid()
        val userId = TestFixtures.uuid()
        val categoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(clientId)
        IdentityFixtures.insertTestUser(userId, "range-user")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        SessionClientFixtures.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = clientId,
            branchDayId = dayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = financials.gross,
            finalPrice = financials.gross,
        )
        CommerceFinanceFixtures.insertTestCompensation(dayId, userId, financials.comp, assignedBy = callerId)
        CommerceFinanceFixtures.insertTestExpense(dayId, userId, financials.expense)
        CommerceFinanceFixtures.insertTestProductSale(
            id = TestFixtures.uuid(),
            branchDayId = dayId,
            productId = productId,
            handledBy = userId,
            unitPrice = financials.productSales,
            totalAmount = financials.productSales,
        )
        transaction {
            CommissionSplitTable.insert {
                it[CommissionSplitTable.id] = TestFixtures.uuid()
                it[CommissionSplitTable.branchDayId] = dayId
                it[CommissionSplitTable.userId] = userId
                it[CommissionSplitTable.amount] = financials.commission
            }
        }
    }

    protected fun insertBranchDay(
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

    protected fun createSubmittedRemittance(
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ): UUID = createSubmittedRemittanceForBranch(branchId, grossIncome, compensation, expenses)

    protected fun createSubmittedProductRemittance(
        productRevenue: BigDecimal,
        targetBranchId: UUID = branchId,
    ): UUID {
        val remittanceId = TestFixtures.uuid()
        // NOTE: `targetBranchId` is a parameter (locals shadow table members), so the
        // `RemittanceTable.insert {}` receiver cannot hijack it the way a bare `branchId`
        // property would (that resolves to the `branch_id` column self-reference).
        val targetDayId = findOrCreateBranchDay(targetBranchId)
        transaction {
            val categoryId = TestFixtures.uuid()
            val productId = TestFixtures.uuid()
            val saleId = TestFixtures.uuid()
            CommerceFinanceFixtures.insertTestCategory(categoryId)
            CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
            CommerceFinanceFixtures.insertTestProductSale(
                id = saleId,
                branchDayId = targetDayId,
                productId = productId,
                handledBy = callerId,
                unitPrice = productRevenue,
                totalAmount = productRevenue,
            )
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.branchId] = targetBranchId
                it[RemittanceTable.type] = RemittanceType.PRODUCT
                it[RemittanceTable.method] = RemittanceMethod.HANDED_TO_ACCOUNTANT
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.version] = 2
                it[RemittanceTable.submittedDate] = today
                it[RemittanceTable.submittedBy] = callerId
                it[RemittanceTable.dateRangeStart] = today
                it[RemittanceTable.dateRangeEnd] = today
            }
            RemittanceLineTable.insert {
                it[RemittanceLineTable.id] = TestFixtures.uuid()
                it[RemittanceLineTable.remittanceId] = remittanceId
                it[RemittanceLineTable.type] = RemittanceLineType.PRODUCT_SALE
                it[RemittanceLineTable.amount] = productRevenue
                it[RemittanceLineTable.productSaleId] = saleId
            }
            insertRemittanceBreakdown(remittanceId, targetDayId)
        }
        return remittanceId
    }

    protected fun createSubmittedRemittanceForBranch(
        targetBranchId: UUID,
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ): UUID {
        val remittanceId = TestFixtures.uuid()
        val compensationUserId = TestFixtures.uuid()
        val targetDayId = findOrCreateBranchDay(targetBranchId)
        val sessionId = TestFixtures.uuid()
        val lineClientId = TestFixtures.uuid()

        transaction {
            IdentityFixtures.insertUser(
                id = compensationUserId,
                username = "comp-user-${compensationUserId.toString().take(8)}",
                passwordHash = "hash",
                email = "${compensationUserId.toString().take(8)}@test.com",
                displayName = "Comp User",
            )
            ClientTable.insert {
                it[ClientTable.id] = lineClientId
                it[ClientTable.firstName] = "Line"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 25
            }
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[SessionTable.clientId] = lineClientId
                it[SessionTable.branchDayId] = targetDayId
                it[SessionTable.sessionType] = com.companyb.companyapp.contracts.session.SessionType.REGULAR
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
                it[SessionTable.basePrice] = grossIncome
                it[SessionTable.finalPrice] = grossIncome
                it[SessionTable.isWalkIn] = false
            }
            insertRemittance(remittanceId, targetBranchId)
            insertRemittanceLine(remittanceId, grossIncome, sessionId)
            insertRemittanceBreakdown(remittanceId, targetDayId)
            CommerceFinanceFixtures.insertTestCompensation(
                targetDayId,
                compensationUserId,
                compensation,
                assignedBy = callerId,
            )
            CommerceFinanceFixtures.insertTestExpense(targetDayId, callerId, expenses)
            insertFinancialSnapshot(remittanceId, grossIncome, compensation, expenses)
        }
        return remittanceId
    }

    protected fun findOrCreateBranchDay(targetBranchId: UUID): UUID {
        val existing =
            transaction {
                BranchDayTable
                    .selectAll()
                    .where {
                        (BranchDayTable.branchId eq targetBranchId) and (BranchDayTable.date eq today)
                    }.singleOrNull()
            }
        if (existing != null) {
            return existing[BranchDayTable.id]
        }
        val dayId = TestFixtures.uuid()
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = dayId
                it[BranchDayTable.branchId] = targetBranchId
                it[BranchDayTable.date] = today
            }
        }
        return dayId
    }

    protected fun insertRemittance(
        remittanceId: UUID,
        targetBranchId: UUID,
    ) {
        RemittanceTable.insert {
            it[RemittanceTable.id] = remittanceId
            it[RemittanceTable.branchId] = targetBranchId
            it[RemittanceTable.type] = RemittanceType.SESSION
            it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
            it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
            it[RemittanceTable.version] = 2
            it[RemittanceTable.submittedDate] = today
            it[RemittanceTable.submittedBy] = callerId
            it[RemittanceTable.dateRangeStart] = today
            it[RemittanceTable.dateRangeEnd] = today
        }
    }

    protected fun insertRemittanceLine(
        remittanceId: UUID,
        grossIncome: BigDecimal,
        sessionId: UUID,
    ) {
        RemittanceLineTable.insert {
            it[RemittanceLineTable.id] = TestFixtures.uuid()
            it[RemittanceLineTable.remittanceId] = remittanceId
            it[RemittanceLineTable.type] = RemittanceLineType.SESSION
            it[RemittanceLineTable.amount] = grossIncome
            it[RemittanceLineTable.sessionId] = sessionId
        }
    }

    protected fun insertRemittanceBreakdown(
        remittanceId: UUID,
        targetDayId: UUID,
    ) {
        RemittanceDayBreakdownTable.insert {
            it[RemittanceDayBreakdownTable.id] = TestFixtures.uuid()
            it[RemittanceDayBreakdownTable.remittanceId] = remittanceId
            it[RemittanceDayBreakdownTable.branchDayId] = targetDayId
        }
    }

    protected fun insertFinancialSnapshot(
        remittanceId: UUID,
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ) {
        val netIncome = grossIncome - compensation - expenses
        RemittanceFinancialSnapshotTable.insert {
            it[RemittanceFinancialSnapshotTable.remittanceId] = remittanceId
            it[RemittanceFinancialSnapshotTable.grossIncome] = grossIncome
            it[RemittanceFinancialSnapshotTable.totalCompensation] = compensation
            it[RemittanceFinancialSnapshotTable.totalExpenses] = expenses
            it[RemittanceFinancialSnapshotTable.netIncome] = netIncome
        }
    }
}
