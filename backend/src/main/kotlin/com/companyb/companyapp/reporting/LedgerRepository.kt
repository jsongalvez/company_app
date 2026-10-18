package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commission.CommissionSplitTable
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.finance.AllowanceTable
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.remittance.RemittanceLineTable
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** One branch-day in the report window. */
data class LedgerDay(
    val branchDayId: UUID,
    val branchId: UUID,
    val branchName: String,
    val date: LocalDate,
    val status: DayStatus,
)

data class LedgerSession(
    val id: UUID,
    val branchDayId: UUID,
    val createdAt: OffsetDateTime,
    val clientName: String,
    val sessionType: SessionType,
    val isWalkIn: Boolean,
    val status: SessionStatus,
    val listPrice: BigDecimal,
    val charged: BigDecimal,
    val isVoided: Boolean,
    val practitioners: String,
    val remittance: String,
)

data class LedgerSale(
    val id: UUID,
    val branchDayId: UUID,
    val soldAt: OffsetDateTime,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val total: BigDecimal,
    val commission: BigDecimal,
    val handledBy: String,
    val withSession: Boolean,
    val isVoided: Boolean,
    val remittance: String,
)

data class LedgerExpense(
    val id: UUID,
    val branchDayId: UUID,
    val createdAt: OffsetDateTime,
    val category: ExpenseCategory,
    val notes: String?,
    val amount: BigDecimal,
    val recordedBy: String,
    val deletedReason: String?,
    val isDeleted: Boolean,
)

/** Pay, allowance or commission share paid out on [branchDayId]. */
data class LedgerPayout(
    val branchDayId: UUID,
    val kind: PayoutKind,
    val person: String,
    val amount: BigDecimal,
    val workDayId: UUID?,
    val note: String?,
)

enum class PayoutKind { PAY, ALLOWANCE, COMMISSION }

data class LedgerRemittance(
    val id: UUID,
    val branchId: UUID,
    val type: RemittanceType,
    val status: RemittanceStatus,
    val method: RemittanceMethod,
    val submittedDate: LocalDate,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val amount: BigDecimal,
    val sentBy: String,
)

data class Ledger(
    val days: List<LedgerDay>,
    val sessions: List<LedgerSession>,
    val sales: List<LedgerSale>,
    val expenses: List<LedgerExpense>,
    val payouts: List<LedgerPayout>,
    val remittances: List<LedgerRemittance>,
    /** Work days referenced by pay rows that fall outside [days] (pay for work at another branch). */
    val otherDays: Map<UUID, LedgerDay>,
)

/**
 * Line-level reads behind the accounting report: every session, product sale, expense,
 * payout and remittance of the given branches between two operational dates.
 */
internal object LedgerRepository {
    fun load(
        branchIds: List<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): Ledger =
        transaction {
            val days = findDays(branchIds, from, to)
            val dayIds = days.map { it.branchDayId }
            val payouts = findPayouts(dayIds)
            val known = dayIds.toSet()
            val otherIds = payouts.mapNotNull { it.workDayId }.filter { it !in known }.distinct()
            Ledger(
                days = days,
                sessions = findSessions(dayIds),
                sales = findSales(dayIds),
                expenses = findExpenses(dayIds),
                payouts = payouts,
                remittances = findRemittances(branchIds, from, to),
                otherDays = findDaysById(otherIds).associateBy { it.branchDayId },
            )
        }

    private fun dayQuery() =
        BranchDayTable
            .innerJoin(BranchTable, {
                BranchDayTable.branchId
            }, { BranchTable.id })
            .selectAll()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toDay() =
        LedgerDay(
            branchDayId = this[BranchDayTable.id],
            branchId = this[BranchDayTable.branchId],
            branchName = this[BranchTable.name],
            date = this[BranchDayTable.date],
            status = this[BranchDayTable.status],
        )

    private fun findDays(
        branchIds: List<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<LedgerDay> {
        if (branchIds.isEmpty()) return emptyList()
        return dayQuery()
            .where {
                (BranchDayTable.branchId inList branchIds) and
                    (BranchDayTable.date greaterEq from) and
                    (BranchDayTable.date lessEq to)
            }.orderBy(BranchDayTable.date to SortOrder.ASC, BranchTable.name to SortOrder.ASC)
            .map { it.toDay() }
    }

    private fun findDaysById(ids: List<UUID>): List<LedgerDay> {
        if (ids.isEmpty()) return emptyList()
        return dayQuery().where { BranchDayTable.id inList ids }.map { it.toDay() }
    }

    private fun userNames(ids: Collection<UUID>): Map<UUID, String> {
        if (ids.isEmpty()) return emptyMap()
        return AppUserTable
            .selectAll()
            .where { AppUserTable.id inList ids.distinct() }
            .associate { it[AppUserTable.id] to it[AppUserTable.displayName] }
    }

    /** "Sent 2026-10-18" / "Draft" / "" for each remitted session or sale id. */
    private fun remittanceLabels(
        sessionIds: List<UUID>,
        saleIds: List<UUID>,
    ): Map<UUID, String> {
        if (sessionIds.isEmpty() && saleIds.isEmpty()) return emptyMap()
        val labels = mutableMapOf<UUID, String>()
        val ref =
            if (sessionIds.isNotEmpty()) {
                RemittanceLineTable.sessionId inList sessionIds
            } else {
                RemittanceLineTable.productSaleId inList saleIds
            }
        RemittanceLineTable
            .innerJoin(RemittanceTable, { RemittanceLineTable.remittanceId }, { RemittanceTable.id })
            .selectAll()
            .where { RemittanceLineTable.deletedAt.isNull() and ref }
            .forEach { row ->
                val id = row[RemittanceLineTable.sessionId] ?: row[RemittanceLineTable.productSaleId] ?: return@forEach
                labels[id] =
                    when (row[RemittanceTable.status]) {
                        RemittanceStatus.SUBMITTED -> "Sent ${row[RemittanceTable.submittedDate]}"
                        else -> "In a draft"
                    }
            }
        return labels
    }

    private fun findSessions(dayIds: List<UUID>): List<LedgerSession> {
        if (dayIds.isEmpty()) return emptyList()
        val rows =
            SessionTable
                .innerJoin(ClientTable, { SessionTable.clientId }, { ClientTable.id })
                .leftJoin(ActiveSessionVoidsView, { SessionTable.id }, { ActiveSessionVoidsView.sessionId })
                .selectAll()
                .where { SessionTable.branchDayId inList dayIds }
                .orderBy(SessionTable.createdAt to SortOrder.ASC)
                .toList()
        val ids = rows.map { it[SessionTable.id] }
        val practitioners = practitionerNames(ids)
        val remitted = remittanceLabels(ids, emptyList())
        return rows.map { row ->
            val id = row[SessionTable.id]
            LedgerSession(
                id = id,
                branchDayId = row[SessionTable.branchDayId],
                createdAt = row[SessionTable.createdAt],
                clientName =
                    clientName(
                        row[ClientTable.firstName],
                        row[ClientTable.middleName],
                        row[ClientTable.lastName],
                        row[ClientTable.suffix],
                    ),
                sessionType = row[SessionTable.sessionType],
                isWalkIn = row[SessionTable.isWalkIn],
                status = row[SessionTable.sessionStatus],
                listPrice = row[SessionTable.basePrice],
                charged = row[SessionTable.finalPrice],
                isVoided = row.getOrNull(ActiveSessionVoidsView.sessionId) != null,
                practitioners = practitioners[id].orEmpty(),
                remittance = remitted[id].orEmpty(),
            )
        }
    }

    private fun practitionerNames(sessionIds: List<UUID>): Map<UUID, String> {
        if (sessionIds.isEmpty()) return emptyMap()
        return SessionPractitionerTable
            .innerJoin(AppUserTable, { SessionPractitionerTable.practitionerId }, { AppUserTable.id })
            .selectAll()
            .where { SessionPractitionerTable.sessionId inList sessionIds }
            .groupBy({ it[SessionPractitionerTable.sessionId] }, { it[AppUserTable.displayName] })
            .mapValues { (_, names) -> names.sorted().joinToString(", ") }
    }

    private fun findSales(dayIds: List<UUID>): List<LedgerSale> {
        if (dayIds.isEmpty()) return emptyList()
        val rows =
            ProductSaleTable
                .leftJoin(ActiveSessionVoidsView, { ProductSaleTable.sessionId }, { ActiveSessionVoidsView.sessionId })
                .selectAll()
                .where { ProductSaleTable.branchDayId inList dayIds }
                .orderBy(ProductSaleTable.soldAt to SortOrder.ASC)
                .toList()
        val names = userNames(rows.map { it[ProductSaleTable.handledBy] })
        val remitted = remittanceLabels(emptyList(), rows.map { it[ProductSaleTable.id] })
        return rows.map { row ->
            val id = row[ProductSaleTable.id]
            LedgerSale(
                id = id,
                branchDayId = row[ProductSaleTable.branchDayId],
                soldAt = row[ProductSaleTable.soldAt],
                productName = row[ProductSaleTable.productName],
                quantity = row[ProductSaleTable.quantity],
                unitPrice = row[ProductSaleTable.unitPriceAtTime],
                total = row[ProductSaleTable.totalAmountAtTime],
                commission = row[ProductSaleTable.commissionAmountAtTime],
                handledBy = names[row[ProductSaleTable.handledBy]].orEmpty(),
                withSession = row[ProductSaleTable.sessionId] != null,
                isVoided = row.getOrNull(ActiveSessionVoidsView.sessionId) != null,
                remittance = remitted[id].orEmpty(),
            )
        }
    }

    private fun findExpenses(dayIds: List<UUID>): List<LedgerExpense> {
        if (dayIds.isEmpty()) return emptyList()
        val rows =
            ExpenseTable
                .selectAll()
                .where { ExpenseTable.branchDayId inList dayIds }
                .orderBy(ExpenseTable.createdAt to SortOrder.ASC)
                .toList()
        val names = userNames(rows.map { it[ExpenseTable.createdBy] })
        return rows.map { row ->
            LedgerExpense(
                id = row[ExpenseTable.id],
                branchDayId = row[ExpenseTable.branchDayId],
                createdAt = row[ExpenseTable.createdAt],
                category = row[ExpenseTable.category],
                notes = row[ExpenseTable.notes],
                amount = row[ExpenseTable.amount],
                recordedBy = names[row[ExpenseTable.createdBy]].orEmpty(),
                deletedReason = row[ExpenseTable.deletedReason],
                isDeleted = row[ExpenseTable.deletedAt] != null,
            )
        }
    }

    private fun findPayouts(dayIds: List<UUID>): List<LedgerPayout> {
        if (dayIds.isEmpty()) return emptyList()
        val pay =
            CompensationTable
                .selectAll()
                .where { CompensationTable.payingBranchDayId inList dayIds }
                .toList()
        val allowances =
            AllowanceTable
                .selectAll()
                .where { AllowanceTable.branchDayId inList dayIds }
                .toList()
        val commissions =
            CommissionSplitTable
                .selectAll()
                .where { CommissionSplitTable.branchDayId inList dayIds }
                .toList()
        val names =
            userNames(
                pay.map { it[CompensationTable.userId] } +
                    allowances.map { it[AllowanceTable.userId] } +
                    commissions.map { it[CommissionSplitTable.userId] },
            )
        return pay.map { row ->
            LedgerPayout(
                branchDayId = row[CompensationTable.payingBranchDayId],
                kind = PayoutKind.PAY,
                person = names[row[CompensationTable.userId]].orEmpty(),
                amount = row[CompensationTable.amount],
                workDayId = row[CompensationTable.workBranchDayId],
                note = row[CompensationTable.note],
            )
        } +
            allowances.map { row ->
                LedgerPayout(
                    branchDayId = row[AllowanceTable.branchDayId],
                    kind = PayoutKind.ALLOWANCE,
                    person = names[row[AllowanceTable.userId]].orEmpty(),
                    amount = row[AllowanceTable.amount],
                    workDayId = null,
                    note = null,
                )
            } +
            commissions.map { row ->
                LedgerPayout(
                    branchDayId = row[CommissionSplitTable.branchDayId],
                    kind = PayoutKind.COMMISSION,
                    person = names[row[CommissionSplitTable.userId]].orEmpty(),
                    amount = row[CommissionSplitTable.amount],
                    workDayId = null,
                    note = null,
                )
            }
    }

    private fun findRemittances(
        branchIds: List<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<LedgerRemittance> {
        if (branchIds.isEmpty()) return emptyList()
        val rows =
            RemittanceTable
                .leftJoin(
                    RemittanceFinancialSnapshotTable,
                    { RemittanceTable.id },
                    { RemittanceFinancialSnapshotTable.remittanceId },
                ).selectAll()
                .where {
                    (RemittanceTable.branchId inList branchIds) and
                        (RemittanceTable.dateRangeStart lessEq to) and
                        (RemittanceTable.dateRangeEnd greaterEq from)
                }.orderBy(RemittanceTable.dateRangeStart to SortOrder.ASC)
                .toList()
        val ids = rows.map { it[RemittanceTable.id] }
        val lineTotals =
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                RemittanceLineTable
                    .selectAll()
                    .where { (RemittanceLineTable.remittanceId inList ids) and RemittanceLineTable.deletedAt.isNull() }
                    .groupBy({ it[RemittanceLineTable.remittanceId] }, { it[RemittanceLineTable.amount] })
                    .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
            }
        val names = userNames(rows.map { it[RemittanceTable.submittedBy] })
        return rows.map { row ->
            val id = row[RemittanceTable.id]
            val type = row[RemittanceTable.type]
            val snapshotNet = row.getOrNull(RemittanceFinancialSnapshotTable.netIncome)
            val amount =
                if (type == RemittanceType.SESSION &&
                    snapshotNet != null
                ) {
                    snapshotNet
                } else {
                    lineTotals[id] ?: BigDecimal.ZERO
                }
            LedgerRemittance(
                id = id,
                branchId = row[RemittanceTable.branchId],
                type = type,
                status = row[RemittanceTable.status],
                method = row[RemittanceTable.method],
                submittedDate = row[RemittanceTable.submittedDate],
                rangeStart = row[RemittanceTable.dateRangeStart],
                rangeEnd = row[RemittanceTable.dateRangeEnd],
                amount = amount,
                sentBy = names[row[RemittanceTable.submittedBy]].orEmpty(),
            )
        }
    }

    private fun clientName(
        first: String?,
        middle: String?,
        last: String?,
        suffix: String?,
    ): String {
        if (first == null && last == null) return "(details erased)"
        return listOfNotNull(first, middle?.firstOrNull()?.let { "$it." }, last, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
