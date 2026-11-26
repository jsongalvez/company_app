package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/** Ids, seed rows and helpers shared by the expense suites. */
abstract class ExpenseServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "expense-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Expense Branch")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.member(callerId, branchId)
    }

    protected fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
        }

    /** An expense on [branchDayId]. */
    protected fun expense(
        id: UUID,
        amount: BigDecimal = BigDecimal("500.00"),
        category: ExpenseCategory = ExpenseCategory.PANTRY,
        notes: String? = null,
    ) = NewExpense(id, branchDayId, amount, category, notes)
}
