package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.DayStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

object BranchDayReportRepository {
    fun findByBranchAndDateRange(
        branchId: UUID,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<BranchDay> =
        transaction {
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq fromDate) and
                        (BranchDayTable.date lessEq toDate)
                }.map { row ->
                    BranchDay(
                        id = row[BranchDayTable.id],
                        branchId = row[BranchDayTable.branchId],
                        date = row[BranchDayTable.date],
                        status = row[BranchDayTable.status],
                    )
                }
        }

    fun findRemittedByBranchAndMonth(
        branchId: UUID,
        yearMonth: YearMonth,
    ): List<BranchDay> =
        transaction {
            val from = yearMonth.atDay(1)
            val to = yearMonth.atEndOfMonth()
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.status eq DayStatus.REMITTED) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to)
                }.map { row ->
                    BranchDay(
                        id = row[BranchDayTable.id],
                        branchId = row[BranchDayTable.branchId],
                        date = row[BranchDayTable.date],
                        status = row[BranchDayTable.status],
                    )
                }
        }
}
