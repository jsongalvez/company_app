package com.companyb.companyapp.reporting

import com.companyb.companyapp.reporting.MonthlyRemittanceSummaryMappers.toMonthlyRemittanceSummary
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

internal object MonthlyRemittanceSummaryRepository {
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
}
