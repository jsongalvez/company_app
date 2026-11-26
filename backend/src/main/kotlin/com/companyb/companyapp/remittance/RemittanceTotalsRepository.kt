package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.util.UUID

internal object RemittanceTotalsRepository {
    /**
     * Snapshot gross: SESSION line amounts for live lines whose session is still
     * COMPLETED and non-voided — the same membership as
     * `daily_sales_summary.gross_income`. The addLine guard admits only COMPLETED sessions
     * and COMPLETED is immutable outside void/unvoid, so the status join is defense-in-depth
     * for legacy rows and status races; voided lines stay silently excluded.
     */
    fun sumGrossIncomeInTransaction(remittanceId: UUID): BigDecimal =
        RemittancePolicy.sum(
            RemittanceLineTable
                .innerJoin(SessionTable, { RemittanceLineTable.sessionId }, { SessionTable.id })
                .leftJoin(
                    ActiveSessionVoidsView,
                    { RemittanceLineTable.sessionId },
                    { ActiveSessionVoidsView.sessionId },
                ).selectAll()
                .where {
                    (RemittanceLineTable.remittanceId eq remittanceId) and
                        (RemittanceLineTable.type eq RemittanceLineType.SESSION) and
                        RemittanceLineTable.deletedAt.isNull() and
                        (ActiveSessionVoidsView.sessionId.isNull()) and
                        (SessionTable.sessionStatus eq SessionStatus.COMPLETED)
                }.map { it[RemittanceLineTable.amount] },
        )

    fun sumCompensationsInTransaction(breakdownIds: List<UUID>): BigDecimal {
        if (breakdownIds.isEmpty()) return BigDecimal.ZERO
        return RemittancePolicy.sum(
            CompensationTable
                .selectAll()
                .where { CompensationTable.payingBranchDayId inList breakdownIds }
                .map { it[CompensationTable.amount] },
        )
    }

    fun sumExpensesInTransaction(breakdownIds: List<UUID>): BigDecimal {
        if (breakdownIds.isEmpty()) return BigDecimal.ZERO
        return RemittancePolicy.sum(
            ExpenseTable
                .selectAll()
                .where {
                    (ExpenseTable.branchDayId inList breakdownIds) and
                        ExpenseTable.deletedAt.isNull()
                }.map { it[ExpenseTable.amount] },
        )
    }
}
