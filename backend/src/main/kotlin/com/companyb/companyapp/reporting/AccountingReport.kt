package com.companyb.companyapp.reporting

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One cell of a report table, typed so Excel keeps numbers and dates as numbers and dates. */
sealed interface ReportCell {
    data class Text(
        val value: String,
    ) : ReportCell

    data class Money(
        val value: BigDecimal,
    ) : ReportCell

    data class Count(
        val value: Long,
    ) : ReportCell

    data class Day(
        val value: LocalDate,
    ) : ReportCell

    data class Time(
        val value: LocalTime,
    ) : ReportCell

    data object Blank : ReportCell
}

/** A heading row inside a table (Summary sections). */
data class ReportTable(
    val name: String,
    val title: String,
    val headers: List<String>,
    val rows: List<List<ReportCell>>,
    val totals: List<ReportCell>? = null,
    val headingRows: Set<Int> = emptySet(),
    val widths: List<Double> = emptyList(),
)

data class AccountingReport(
    val title: String,
    val lines: List<String>,
    val tables: List<ReportTable>,
)

/**
 * Builds the accounting report from line-level ledger rows. Pure: every number here is
 * derived from the [Ledger] rows, using the same rules as the daily summary
 * (session income = done, not voided; pay counts on the paying day; deleted expenses and
 * voided sessions are listed but not counted; net = session income - pay - expenses).
 */
object AccountingReportBuilder {
    private val zero: BigDecimal = BigDecimal.ZERO.setScale(2)

    fun build(
        branchLabel: String,
        from: LocalDate,
        to: LocalDate,
        ledger: Ledger,
        zone: ZoneId,
    ): AccountingReport {
        val day = ledger.days.associateBy { it.branchDayId }
        val multi =
            ledger.days
                .map { it.branchId }
                .distinct()
                .size > 1
        val tables =
            buildList {
                add(summary(ledger))
                if (multi) add(branches(ledger))
                add(days(ledger))
                add(sessions(ledger, day, zone))
                add(sales(ledger, day, zone))
                add(expenses(ledger, day, zone))
                add(payouts(ledger, day))
                add(remittances(ledger))
            }
        return AccountingReport(
            title = "Accounting report",
            lines = listOf(branchLabel, "$from to $to"),
            tables = tables,
        )
    }

    private fun sum(values: Iterable<BigDecimal>): BigDecimal = values.fold(zero) { a, b -> a + b }

    private fun counted(s: LedgerSession) = s.status == SessionStatus.COMPLETED && !s.isVoided

    private fun money(v: BigDecimal) = ReportCell.Money(v.setScale(2, java.math.RoundingMode.HALF_UP))

    private fun count(n: Int) = ReportCell.Count(n.toLong())

    private fun text(s: String?) = if (s.isNullOrEmpty()) ReportCell.Blank else ReportCell.Text(s)

    private data class Totals(
        val sessionIncome: BigDecimal,
        val sessionsDone: Int,
        val productSales: BigDecimal,
        val pay: BigDecimal,
        val expenses: BigDecimal,
        val commission: BigDecimal,
        val allowances: BigDecimal,
    ) {
        val net: BigDecimal get() = sessionIncome - pay - expenses
    }

    private fun totals(
        ledger: Ledger,
        dayIds: Set<java.util.UUID>,
    ): Totals {
        val sessions = ledger.sessions.filter { it.branchDayId in dayIds && counted(it) }
        val payouts = ledger.payouts.filter { it.branchDayId in dayIds }
        return Totals(
            sessionIncome = sum(sessions.map { it.charged }),
            sessionsDone = sessions.size,
            productSales = sum(ledger.sales.filter { it.branchDayId in dayIds && !it.isVoided }.map { it.total }),
            pay = sum(payouts.filter { it.kind == PayoutKind.PAY }.map { it.amount }),
            expenses = sum(ledger.expenses.filter { it.branchDayId in dayIds && !it.isDeleted }.map { it.amount }),
            commission = sum(payouts.filter { it.kind == PayoutKind.COMMISSION }.map { it.amount }),
            allowances = sum(payouts.filter { it.kind == PayoutKind.ALLOWANCE }.map { it.amount }),
        )
    }

    private fun summary(ledger: Ledger): ReportTable {
        val sheet = SummarySheet(ledger, totals(ledger, ledger.days.map { it.branchDayId }.toSet()))
        sheet.moneyInAndOut()
        sheet.notCounted()
        sheet.sessionCounts()
        sheet.remittances()
        return ReportTable(
            name = "Summary",
            title = "Summary",
            headers = listOf("Item", "Count", "Amount"),
            rows = sheet.rows,
            headingRows = sheet.headings,
            widths = listOf(W.ITEM, W.COUNT, W.MONEY),
        )
    }

    private class SummarySheet(
        val ledger: Ledger,
        val all: Totals,
    ) {
        val rows = mutableListOf<List<ReportCell>>()
        val headings = mutableSetOf<Int>()

        fun heading(label: String) {
            headings += rows.size
            rows += listOf(ReportCell.Text(label), ReportCell.Blank, ReportCell.Blank)
        }

        fun line(
            label: String,
            n: Int?,
            amount: BigDecimal?,
        ) {
            rows +=
                listOf(
                    ReportCell.Text(label),
                    n?.let { count(it) } ?: ReportCell.Blank,
                    amount?.let { money(it) } ?: ReportCell.Blank,
                )
        }

        fun moneyInAndOut() {
            val liveSales = ledger.sales.filter { !it.isVoided }
            val liveExpenses = ledger.expenses.filter { !it.isDeleted }
            heading("Money in")
            line("Sessions done", all.sessionsDone, all.sessionIncome)
            line("Product sales", liveSales.size, all.productSales)
            line("Total taken in", null, all.sessionIncome + all.productSales)
            heading("Money out")
            line("Pay", ledger.payouts.count { it.kind == PayoutKind.PAY }, all.pay)
            line("Expenses", liveExpenses.size, all.expenses)
            liveExpenses.groupBy { it.category }.toSortedMap().forEach { (c, xs) ->
                line("    ${ReportWords.category(c)}", xs.size, sum(xs.map { it.amount }))
            }
            heading("Net")
            line("Net from sessions (sessions done - pay - expenses)", null, all.net)
        }

        fun notCounted() {
            heading("Not counted in net")
            line("Product sales (sent as their own remittance)", ledger.sales.count { !it.isVoided }, all.productSales)
            line(
                "Product commission shared with staff",
                ledger.payouts.count { it.kind == PayoutKind.COMMISSION },
                all.commission,
            )
            line(
                "Allowances (paid by the owner)",
                ledger.payouts.count { it.kind == PayoutKind.ALLOWANCE },
                all.allowances,
            )
            val voided = ledger.sessions.filter { it.isVoided }
            line("Voided sessions", voided.size, sum(voided.map { it.charged }))
            val deleted = ledger.expenses.filter { it.isDeleted }
            line("Deleted expenses", deleted.size, sum(deleted.map { it.amount }))
        }

        fun sessionCounts() {
            heading("Sessions by result")
            val live = ledger.sessions.filter { !it.isVoided }
            SessionStatus.entries.filter { it != SessionStatus.UNKNOWN }.forEach { st ->
                val xs = live.filter { it.status == st }
                line(ReportWords.status(st), xs.size, sum(xs.map { it.charged }))
            }
            heading("Done sessions by visit type")
            ledger.sessions.filter { counted(it) }.groupBy { it.sessionType }.toSortedMap().forEach { (t, xs) ->
                line(ReportWords.type(t), xs.size, sum(xs.map { it.charged }))
            }
        }

        fun remittances() {
            heading("Remittances")
            val sent = ledger.remittances.filter { it.status == RemittanceStatus.SUBMITTED }
            listOf(RemittanceType.SESSION, RemittanceType.PRODUCT).forEach { t ->
                val xs = sent.filter { it.type == t }
                line("${ReportWords.remittanceType(t)} sent", xs.size, sum(xs.map { it.amount }))
            }
            val drafts = ledger.remittances.filter { it.status == RemittanceStatus.DRAFT }
            line("Drafts not sent yet", drafts.size, sum(drafts.map { it.amount }))
            line("Days not remitted yet", ledger.days.count { it.status != DayStatus.REMITTED }, null)
        }
    }

    /** One column: header, width, the cell for each item, and an optional total. */
    private class Col<T>(
        val header: String,
        val width: Double,
        val total: ReportCell = ReportCell.Blank,
        val cell: (T) -> ReportCell,
    )

    private fun <T> table(
        name: String,
        title: String,
        items: List<T>,
        totalLabel: String,
        cols: List<Col<T>>,
    ): ReportTable =
        ReportTable(
            name = name,
            title = title,
            headers = cols.map { it.header },
            rows = items.map { item -> cols.map { it.cell(item) } },
            totals = listOf(ReportCell.Text(totalLabel)) + cols.drop(1).map { it.total },
            widths = cols.map { it.width },
        )

    private fun branches(ledger: Ledger): ReportTable {
        val all = totals(ledger, ledger.days.map { it.branchDayId }.toSet())
        val groups =
            ledger.days
                .groupBy { it.branchId }
                .values
                .sortedBy { it.first().branchName }
                .map { ds -> ds.first().branchName to totals(ledger, ds.map { it.branchDayId }.toSet()) }
        return table(
            "Branches",
            "By branch",
            groups,
            "${groups.size} branches",
            listOf(
                Col("Branch", W.NAME) { ReportCell.Text(it.first) },
                Col("Sessions done", W.COUNT, count(all.sessionsDone)) { count(it.second.sessionsDone) },
                Col("Session income", W.MONEY, money(all.sessionIncome)) { money(it.second.sessionIncome) },
                Col("Product sales", W.MONEY, money(all.productSales)) { money(it.second.productSales) },
                Col("Pay", W.MONEY, money(all.pay)) { money(it.second.pay) },
                Col("Expenses", W.MONEY, money(all.expenses)) { money(it.second.expenses) },
                Col("Net", W.MONEY, money(all.net)) { money(it.second.net) },
            ),
        )
    }

    private fun days(ledger: Ledger): ReportTable {
        val all = totals(ledger, ledger.days.map { it.branchDayId }.toSet())
        val items = ledger.days.map { it to totals(ledger, setOf(it.branchDayId)) }
        return table(
            "Days",
            "Day by day",
            items,
            "${items.size} days",
            listOf(
                Col("Date", W.DATE) { ReportCell.Day(it.first.date) },
                Col("Branch", W.NAME) { ReportCell.Text(it.first.branchName) },
                Col("Day", W.STATUS) { ReportCell.Text(ReportWords.dayStatus(it.first.status)) },
                Col("Sessions done", W.COUNT, count(all.sessionsDone)) { count(it.second.sessionsDone) },
                Col("Session income", W.MONEY, money(all.sessionIncome)) { money(it.second.sessionIncome) },
                Col("Product sales", W.MONEY, money(all.productSales)) { money(it.second.productSales) },
                Col("Pay", W.MONEY, money(all.pay)) { money(it.second.pay) },
                Col("Expenses", W.MONEY, money(all.expenses)) { money(it.second.expenses) },
                Col("Net", W.MONEY, money(all.net)) { money(it.second.net) },
                Col("Commission", W.MONEY, money(all.commission)) { money(it.second.commission) },
                Col("Allowances", W.MONEY, money(all.allowances)) { money(it.second.allowances) },
            ),
        )
    }

    private fun sessions(
        ledger: Ledger,
        day: Map<java.util.UUID, LedgerDay>,
        zone: ZoneId,
    ): ReportTable {
        val done = ledger.sessions.filter { counted(it) }
        return table(
            "Sessions",
            "Sessions",
            ledger.sessions,
            "${ledger.sessions.size} sessions",
            listOf(
                Col<LedgerSession>("Date", W.DATE) { ReportCell.Day(day.getValue(it.branchDayId).date) },
                Col("Time", W.TIME) { ReportCell.Time(it.createdAt.atZoneSameInstant(zone).toLocalTime()) },
                Col("Branch", W.NAME) { ReportCell.Text(day.getValue(it.branchDayId).branchName) },
                Col("Client", W.NAME) { ReportCell.Text(it.clientName) },
                Col("Visit type", W.LABEL) { ReportCell.Text(ReportWords.type(it.sessionType)) },
                Col("Booked", W.COUNT) { ReportCell.Text(if (it.isWalkIn) "Walk-in" else "Booked") },
                Col("Result", W.STATUS, ReportCell.Text("${done.size} done")) {
                    ReportCell.Text(if (it.isVoided) "Voided" else ReportWords.status(it.status))
                },
                Col("List price", W.MONEY) { money(it.listPrice) },
                Col("Charged", W.MONEY) { money(it.charged) },
                Col("Discount", W.MONEY) { money(it.listPrice - it.charged) },
                Col("Counted as income", W.MONEY, money(sum(done.map { it.charged }))) {
                    money(if (counted(it)) it.charged else zero)
                },
                Col("Practitioners", W.NAME) { text(it.practitioners) },
                Col("Remittance", W.LABEL) { text(it.remittance) },
            ),
        )
    }

    private fun sales(
        ledger: Ledger,
        day: Map<java.util.UUID, LedgerDay>,
        zone: ZoneId,
    ): ReportTable {
        val live = ledger.sales.filter { !it.isVoided }
        return table(
            "Product sales",
            "Product sales",
            ledger.sales,
            "${ledger.sales.size} sales",
            listOf(
                Col<LedgerSale>("Date", W.DATE) { ReportCell.Day(day.getValue(it.branchDayId).date) },
                Col("Time", W.TIME) { ReportCell.Time(it.soldAt.atZoneSameInstant(zone).toLocalTime()) },
                Col("Branch", W.NAME) { ReportCell.Text(day.getValue(it.branchDayId).branchName) },
                Col("Product", W.NAME) { ReportCell.Text(it.productName) },
                Col("Qty", W.QTY, count(live.sumOf { it.quantity })) { count(it.quantity) },
                Col("Unit price", W.MONEY) { money(it.unitPrice) },
                Col("Total", W.MONEY) { money(it.total) },
                Col("Commission", W.MONEY, money(sum(live.map { it.commission }))) { money(it.commission) },
                Col("Sold by", W.NAME) { text(it.handledBy) },
                Col("Sale", W.LABEL) { ReportCell.Text(if (it.withSession) "During a session" else "Walk-in") },
                Col("Counted as sales", W.MONEY, money(sum(live.map { it.total }))) {
                    money(if (it.isVoided) zero else it.total)
                },
                Col("Remittance", W.LABEL) { text(if (it.isVoided) "Session voided" else it.remittance) },
            ),
        )
    }

    private fun expenses(
        ledger: Ledger,
        day: Map<java.util.UUID, LedgerDay>,
        zone: ZoneId,
    ): ReportTable {
        val live = ledger.expenses.filter { !it.isDeleted }
        return table(
            "Expenses",
            "Expenses",
            ledger.expenses,
            "${ledger.expenses.size} expenses",
            listOf(
                Col<LedgerExpense>("Date", W.DATE) { ReportCell.Day(day.getValue(it.branchDayId).date) },
                Col("Time", W.TIME) { ReportCell.Time(it.createdAt.atZoneSameInstant(zone).toLocalTime()) },
                Col("Branch", W.NAME) { ReportCell.Text(day.getValue(it.branchDayId).branchName) },
                Col("Category", W.LABEL) { ReportCell.Text(ReportWords.category(it.category)) },
                Col("Notes", W.TEXT) { text(it.notes) },
                Col("Recorded by", W.NAME) { text(it.recordedBy) },
                Col("Amount", W.MONEY) { money(it.amount) },
                Col(
                    "Counted",
                    W.MONEY,
                    money(sum(live.map { it.amount })),
                ) { money(if (it.isDeleted) zero else it.amount) },
                Col("Status", W.TEXT) { text(if (it.isDeleted) "Deleted: ${it.deletedReason.orEmpty()}" else null) },
            ),
        )
    }

    private fun payouts(
        ledger: Ledger,
        day: Map<java.util.UUID, LedgerDay>,
    ): ReportTable {
        fun total(k: PayoutKind) = money(sum(ledger.payouts.filter { it.kind == k }.map { it.amount }))

        fun amount(
            p: LedgerPayout,
            k: PayoutKind,
        ) = money(if (p.kind == k) p.amount else zero)

        fun work(p: LedgerPayout) = p.workDayId?.let { day[it] ?: ledger.otherDays[it] }
        val sorted =
            ledger.payouts.sortedWith(
                compareBy({
                    day.getValue(it.branchDayId).date
                }, { day.getValue(it.branchDayId).branchName }, { it.kind }, { it.person }),
            )
        return table(
            "Pay",
            "Pay, commission and allowances",
            sorted,
            "${sorted.size} lines",
            listOf(
                Col<LedgerPayout>("Paid on", W.DATE) { ReportCell.Day(day.getValue(it.branchDayId).date) },
                Col("Paid by branch", W.NAME) { ReportCell.Text(day.getValue(it.branchDayId).branchName) },
                Col("Person", W.NAME) { text(it.person) },
                Col("Kind", W.STATUS) { ReportCell.Text(ReportWords.payout(it.kind)) },
                Col("For work on", W.DATE) { p -> work(p)?.let { ReportCell.Day(it.date) } ?: ReportCell.Blank },
                Col("Work branch", W.NAME) { text(work(it)?.branchName) },
                Col("Pay", W.MONEY, total(PayoutKind.PAY)) { amount(it, PayoutKind.PAY) },
                Col("Commission", W.MONEY, total(PayoutKind.COMMISSION)) { amount(it, PayoutKind.COMMISSION) },
                Col("Allowance", W.MONEY, total(PayoutKind.ALLOWANCE)) { amount(it, PayoutKind.ALLOWANCE) },
                Col("Note", W.TEXT) { text(it.note) },
            ),
        )
    }

    private fun remittances(ledger: Ledger): ReportTable {
        val names = ledger.days.associate { it.branchId to it.branchName }
        val sent = ledger.remittances.filter { it.status == RemittanceStatus.SUBMITTED }

        fun isSent(r: LedgerRemittance) = r.status == RemittanceStatus.SUBMITTED
        return table(
            "Remittances",
            "Remittances covering these dates",
            ledger.remittances,
            "${ledger.remittances.size} remittances",
            listOf(
                Col<LedgerRemittance>("Branch", W.NAME) { ReportCell.Text(names[it.branchId].orEmpty()) },
                Col("Kind", W.LABEL) { ReportCell.Text(ReportWords.remittanceType(it.type)) },
                Col(
                    "Status",
                    W.SHORT,
                    ReportCell.Text("${sent.size} sent"),
                ) { ReportCell.Text(if (isSent(it)) "Sent" else "Draft") },
                Col("From", W.DATE) { ReportCell.Day(it.rangeStart) },
                Col("To", W.DATE) { ReportCell.Day(it.rangeEnd) },
                Col("Sent on", W.DATE) { if (isSent(it)) ReportCell.Day(it.submittedDate) else ReportCell.Blank },
                Col("How", W.NAME) { ReportCell.Text(ReportWords.method(it.method)) },
                Col("By", W.NAME, ReportCell.Text("Sent total")) { text(it.sentBy) },
                Col("Amount", W.MONEY, money(sum(sent.map { it.amount }))) { money(it.amount) },
            ),
        )
    }
}

/** Column widths, in Excel character units. */
private object W {
    const val QTY = 6.0
    const val TIME = 7.0
    const val SHORT = 8.0
    const val COUNT = 10.0
    const val STATUS = 10.0
    const val DATE = 11.0
    const val MONEY = 13.0
    const val LABEL = 15.0
    const val NAME = 20.0
    const val TEXT = 28.0
    const val ITEM = 52.0
}

/** Plain words for codes, matching the web app's labels. */
internal object ReportWords {
    fun status(s: SessionStatus) =
        when (s) {
            SessionStatus.PENDING -> "Waiting"
            SessionStatus.COMPLETED -> "Done"
            SessionStatus.NO_SHOW -> "No-show"
            SessionStatus.CANCELLED -> "Cancelled"
            SessionStatus.UNKNOWN -> "Unknown"
        }

    fun type(t: SessionType) =
        when (t) {
            SessionType.REGULAR -> "First visit"
            SessionType.PROVINCIAL_FIRST -> "First visit (tour)"
            SessionType.SECOND_SESSION -> "Second visit"
            SessionType.SUBSEQUENT -> "Return visit"
            SessionType.MEDICAL_MISSION -> "Medical mission"
            SessionType.UNKNOWN -> "Unknown"
        }

    fun dayStatus(s: DayStatus) =
        when (s) {
            DayStatus.OPEN -> "Open"
            DayStatus.PAST -> "Closed"
            DayStatus.REMITTED -> "Remitted"
            DayStatus.UNKNOWN -> "Unknown"
        }

    fun remittanceType(t: RemittanceType) =
        when (t) {
            RemittanceType.SESSION -> "Sessions"
            RemittanceType.PRODUCT -> "Product sales"
            RemittanceType.UNKNOWN -> "Unknown"
        }

    fun method(m: RemittanceMethod) =
        when (m) {
            RemittanceMethod.BANK_TRANSFER -> "Bank transfer"
            RemittanceMethod.HANDED_TO_ACCOUNTANT -> "Handed to accountant"
            RemittanceMethod.UNKNOWN -> "Unknown"
        }

    fun payout(k: PayoutKind) =
        when (k) {
            PayoutKind.PAY -> "Pay"
            PayoutKind.COMMISSION -> "Commission"
            PayoutKind.ALLOWANCE -> "Allowance"
        }

    fun category(c: ExpenseCategory) =
        if (c == ExpenseCategory.FURNITURE_FIXTURES) {
            "Furniture and fixtures"
        } else {
            c.name
                .lowercase()
                .split("_")
                .joinToString(" ")
                .replaceFirstChar { it.uppercase() }
        }
}
