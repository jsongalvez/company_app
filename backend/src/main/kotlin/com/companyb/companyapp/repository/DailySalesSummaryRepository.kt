package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.DailySalesSummary
import com.companyb.companyapp.repository.model.DailySalesSummaryView
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/** Keyset cursor for daily-summary browse: strictly-before position on `(date, branch_day_id) DESC`. */
data class DailySummaryBrowseCursor(
    val date: LocalDate,
    val branchDayId: UUID,
)

/** Opaque URL-safe cursor for [DailySummaryBrowseCursor]: `date|branchDayId`, base64url. */
fun encodeDailySummaryCursor(cursor: DailySummaryBrowseCursor): String =
    encodeOpaqueCursor(cursor.date.toString(), cursor.branchDayId.toString())

fun decodeDailySummaryCursor(raw: String): DailySummaryBrowseCursor {
    val parts =
        runCatching { decodeOpaqueCursor(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid daily-summary cursor") }
    require(parts.size == 2) { "Invalid daily-summary cursor" }
    return DailySummaryBrowseCursor(
        date = LocalDate.parse(parts[0]),
        branchDayId = UUID.fromString(parts[1]),
    )
}

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

    fun findRangeByBranch(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<DailySalesSummary> =
        transaction {
            DailySalesSummaryView
                .selectAll()
                .where {
                    (DailySalesSummaryView.branchId eq branchId) and
                        (DailySalesSummaryView.date greaterEq from) and
                        (DailySalesSummaryView.date lessEq to)
                }.orderBy(DailySalesSummaryView.date to SortOrder.ASC)
                .map { it.toDailySalesSummary() }
        }

    /**
     * Keyset browse over `(date DESC, branch_day_id DESC)`. [cursor] is the
     * strictly-before position (exclusive). [limit] rows are returned; the
     * caller decides pagination via [encodeDailySummaryCursor] on the last row.
     * [from]/[to] bound the window inclusively (null = unbounded) — the #105 D4
     * feed modes (monthly month, date-range window, calendar-jump month) filter
     * server-side so pagination stays keyset-correct inside the window.
     */
    fun findPagedByBranch(
        branchId: UUID,
        cursor: DailySummaryBrowseCursor?,
        limit: Int,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): List<DailySalesSummary> =
        transaction {
            DailySalesSummaryView
                .selectAll()
                .where {
                    (DailySalesSummaryView.branchId eq branchId) and
                        window(from, to) and
                        keyset(cursor)
                }.orderBy(
                    DailySalesSummaryView.date to SortOrder.DESC,
                    DailySalesSummaryView.branchDayId to SortOrder.DESC,
                ).limit(limit)
                .map { it.toDailySalesSummary() }
        }

    private fun window(
        from: LocalDate?,
        to: LocalDate?,
    ): Op<Boolean> {
        val fromOp = if (from != null) DailySalesSummaryView.date greaterEq from else Op.TRUE
        val toOp = if (to != null) DailySalesSummaryView.date lessEq to else Op.TRUE
        return fromOp and toOp
    }

    private fun keyset(cursor: DailySummaryBrowseCursor?): Op<Boolean> {
        if (cursor == null) return Op.TRUE
        return (DailySalesSummaryView.date less cursor.date) or
            (
                (DailySalesSummaryView.date eq cursor.date) and
                    (DailySalesSummaryView.branchDayId less cursor.branchDayId)
            )
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
