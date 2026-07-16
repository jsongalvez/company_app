package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.DailySalesSummary
import com.companyb.companyapp.repository.model.DailySalesSummaryView
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

object DailySalesSummaryRepository {
    fun findByBranchAndDate(
        branchId: UUID,
        date: LocalDate,
    ): DailySalesSummary? =
        transaction {
            DailySalesSummaryView
                .selectAll()
                .where {
                    (DailySalesSummaryView.branchId eq branchId) and
                        (DailySalesSummaryView.date eq date)
                }.singleOrNull()
                ?.toDailySalesSummary()
        }

    private fun ResultRow.toDailySalesSummary(): DailySalesSummary {
        val gross = this[DailySalesSummaryView.grossIncome]
        val comp = this[DailySalesSummaryView.totalCompensation]
        val exp = this[DailySalesSummaryView.totalExpenses]
        return DailySalesSummary(
            branchDayId = this[DailySalesSummaryView.branchDayId],
            branchId = this[DailySalesSummaryView.branchId],
            date = this[DailySalesSummaryView.date],
            grossIncome = gross,
            totalCompensation = comp,
            totalExpenses = exp,
            netIncome = gross - comp - exp,
            totalProductSales = this[DailySalesSummaryView.totalProductSales],
            totalCommission = this[DailySalesSummaryView.totalCommission],
        )
    }
}
