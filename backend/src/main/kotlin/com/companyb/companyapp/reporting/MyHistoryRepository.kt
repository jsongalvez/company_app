package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.commission.CommissionSplitTable
import com.companyb.companyapp.contracts.reporting.EarningKind
import com.companyb.companyapp.contracts.reporting.MyEarning
import com.companyb.companyapp.contracts.reporting.MyHistoryResponse
import com.companyb.companyapp.contracts.reporting.MyShift
import com.companyb.companyapp.finance.AllowanceTable
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** The caller's own shifts and payouts: "where can I see my clock-ins and the money I got". */
internal object MyHistoryRepository {
    fun load(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): MyHistoryResponse =
        transaction {
            val shifts = findShifts(userId, from, to)
            val range = Range(userId, from, to)
            val earnings =
                (
                    findEarnings(
                        CompensationTable,
                        CompensationTable.payingBranchDayId,
                        CompensationTable.userId,
                        range,
                    ) {
                        MyEarningRow(EarningKind.PAY, it[CompensationTable.amount], it[CompensationTable.note])
                    } +
                        findEarnings(
                            AllowanceTable,
                            AllowanceTable.branchDayId,
                            AllowanceTable.userId,
                            range,
                        ) {
                            MyEarningRow(EarningKind.ALLOWANCE, it[AllowanceTable.amount], null)
                        } +
                        findEarnings(
                            CommissionSplitTable,
                            CommissionSplitTable.branchDayId,
                            CommissionSplitTable.userId,
                            range,
                        ) {
                            MyEarningRow(EarningKind.COMMISSION, it[CommissionSplitTable.amount], null)
                        }
                ).sortedWith(compareByDescending<Pair<LocalDate, MyEarning>> { it.first }.thenBy { it.second.kind })
            val total = earnings.fold(BigDecimal.ZERO) { sum, e -> sum + BigDecimal(e.second.amount) }
            MyHistoryResponse(
                from = from.toString(),
                to = to.toString(),
                shifts = shifts,
                earnings = earnings.map { it.second },
                totalEarned = money(total),
            )
        }

    private data class MyEarningRow(
        val kind: EarningKind,
        val amount: BigDecimal,
        val note: String?,
    )

    private fun findShifts(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<MyShift> =
        AttendanceTable
            .innerJoin(BranchDayTable, { AttendanceTable.branchDayId }, { BranchDayTable.id })
            .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
            .selectAll()
            .where {
                (AttendanceTable.userId eq userId) and
                    (BranchDayTable.date greaterEq from) and
                    (BranchDayTable.date lessEq to)
            }.orderBy(AttendanceTable.clockIn to SortOrder.DESC)
            .map {
                MyShift(
                    date = it[BranchDayTable.date].toString(),
                    branchName = it[BranchTable.name],
                    clockIn = it[AttendanceTable.clockIn].toInstant().toString(),
                    clockOut = it[AttendanceTable.clockOut]?.toInstant()?.toString(),
                )
            }

    private class Range(
        val userId: UUID,
        val from: LocalDate,
        val to: LocalDate,
    )

    private fun findEarnings(
        table: Table,
        dayColumn: Column<UUID>,
        userColumn: Column<UUID>,
        range: Range,
        read: (org.jetbrains.exposed.v1.core.ResultRow) -> MyEarningRow,
    ): List<Pair<LocalDate, MyEarning>> =
        table
            .innerJoin(BranchDayTable, { dayColumn }, { BranchDayTable.id })
            .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
            .selectAll()
            .where {
                (userColumn eq range.userId) and
                    (BranchDayTable.date greaterEq range.from) and
                    (BranchDayTable.date lessEq range.to)
            }.map {
                val row = read(it)
                val date = it[BranchDayTable.date]
                date to
                    MyEarning(
                        date = date.toString(),
                        branchName = it[BranchTable.name],
                        kind = row.kind,
                        amount = money(row.amount),
                        note = row.note,
                    )
            }

    private fun money(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
