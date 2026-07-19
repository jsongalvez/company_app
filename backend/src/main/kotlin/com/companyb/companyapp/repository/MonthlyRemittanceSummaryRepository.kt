package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummaryView
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

object MonthlyRemittanceSummaryRepository {
    fun findByBranchYearMonth(
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary? =
        transaction {
            MonthlyRemittanceSummaryView
                .selectAll()
                .where {
                    (MonthlyRemittanceSummaryView.branchId eq branchId) and
                        (MonthlyRemittanceSummaryView.year eq year) and
                        (MonthlyRemittanceSummaryView.month eq month)
                }.singleOrNull()
                ?.toMonthlyRemittanceSummary()
        }

    private fun ResultRow.toMonthlyRemittanceSummary(): MonthlyRemittanceSummary {
        val gross = this[MonthlyRemittanceSummaryView.grossIncome]
        val comp = this[MonthlyRemittanceSummaryView.totalCompensation]
        val exp = this[MonthlyRemittanceSummaryView.totalExpenses]
        return MonthlyRemittanceSummary(
            branchId = this[MonthlyRemittanceSummaryView.branchId],
            year = this[MonthlyRemittanceSummaryView.year],
            month = this[MonthlyRemittanceSummaryView.month],
            totalRemittances = this[MonthlyRemittanceSummaryView.totalRemittances],
            sessionCount = this[MonthlyRemittanceSummaryView.sessionCount],
            productCount = this[MonthlyRemittanceSummaryView.productCount],
            grossIncome = gross,
            totalCompensation = comp,
            totalExpenses = exp,
            netIncome = this[MonthlyRemittanceSummaryView.netIncome],
        )
    }
}
