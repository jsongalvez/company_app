package com.companyb.companyapp.remittance

import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class CreateDraftParams(
    val id: UUID,
    val type: RemittanceType,
    val branchId: UUID,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val submittedDate: LocalDate,
    val submittedBy: UUID,
)

data class RemittanceCreateResult(
    val remittance: Remittance,
    val created: Boolean,
)

data class UpdateHeaderParams(
    val remittanceId: UUID,
    val expectedVersion: Int,
    val type: RemittanceType,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
)

data class RemittanceWithNet(
    val remittance: Remittance,
    val netIncome: BigDecimal?,
)

data class RemittanceSessionPickerEntry(
    val id: UUID,
    val clientName: String?,
    val bookedAt: OffsetDateTime?,
    val sessionStatus: SessionStatus,
    val finalPrice: BigDecimal,
)

data class RemittanceProductSalePickerEntry(
    val id: UUID,
    val productName: String,
    val quantity: Int,
    val totalAmountAtTime: BigDecimal,
    val soldAt: OffsetDateTime,
)

/**
 * Remittance persistence store (#320, ADR-0024).
 *
 * Mutating functions are **in-transaction store operations**: they open no transaction of their
 * own and take no audit callback — they execute on the caller's (command-owned) transaction,
 * which the feature command also uses to insert the audit rows atomically. They keep their row
 * locks (`forUpdate()`), optimistic-version predicates, idempotency guards, and atomic
 * check+write WHERE clauses. Read helpers may still open their own convenient transaction
 * wrappers.
 */
internal object RemittanceRepository {
    fun findById(id: UUID): Remittance? =
        transaction {
            findByIdInTransaction(id)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Remittance? =
        RemittanceTable
            .selectAll()
            .where { RemittanceTable.id eq id }
            .singleOrNull()
            ?.toRemittance()

    /**
     * Locks the remittance row (`FOR UPDATE`) on the caller's transaction and returns its current
     * state — the serialization anchor for submit/undo. Null when the remittance does not exist.
     *
     * #506 — every header/line/day mutation acquires this lock before draft validation, with
     * the parent always first in the lock order, so draft checks and child writes serialize
     * with submit/undo and with each other.
     */
    fun lockByIdInTransaction(id: UUID): Remittance? =
        RemittanceTable
            .selectAll()
            .where { RemittanceTable.id eq id }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toRemittance()

    /**
     * #506 — bumps the parent version for an actual content change. The caller must already
     * hold the parent `FOR UPDATE` lock; the version predicate closes any residual race and
     * surfaces a lost update as [VersionMismatchException]. No-op retries must not call this.
     */
    fun bumpVersionInTransaction(
        remittanceId: UUID,
        expectedVersion: Int,
    ) {
        val updated =
            RemittanceTable.update({
                (RemittanceTable.id eq remittanceId) and
                    (RemittanceTable.version eq expectedVersion)
            }) {
                it[RemittanceTable.version] = expectedVersion + 1
            }
        if (updated == 0) {
            throw remittanceVersionMismatch(remittanceId)
        }
    }

    fun createDraftInTransaction(params: CreateDraftParams): RemittanceCreateResult {
        val existing = findByIdInTransaction(params.id)
        if (existing != null) {
            if (existing.branchId != params.branchId) {
                throw ConflictException("Remittance UUID belongs to another branch")
            }
            return RemittanceCreateResult(existing, created = false)
        }

        val inserted =
            RemittanceTable
                .insertIgnore {
                    it[RemittanceTable.id] = params.id
                    it[RemittanceTable.type] = params.type
                    it[RemittanceTable.branchId] = params.branchId
                    it[RemittanceTable.method] = params.method
                    it[RemittanceTable.dateRangeStart] = params.dateRangeStart
                    it[RemittanceTable.dateRangeEnd] = params.dateRangeEnd
                    it[RemittanceTable.submittedDate] = params.submittedDate
                    it[RemittanceTable.submittedBy] = params.submittedBy
                }.insertedCount > 0

        val created =
            findByIdInTransaction(params.id) ?: error("remittance not found after insert for ${params.id}")
        if (created.branchId != params.branchId) {
            throw ConflictException("Remittance UUID belongs to another branch")
        }
        // #601 max-2: raced-replay and fresh-draft share one exit.
        return if (!inserted) {
            RemittanceCreateResult(created, created = false)
        } else {
            logger.info {
                "[CREATE-REMITTANCE-DRAFT] Remittance ${created.id.toString().maskUUID()} created=true"
            }
            RemittanceCreateResult(created, created = true)
        }
    }

    /**
     * Atomically finalizes the submission: status → SUBMITTED, version bump, operational-date
     * stamp, DB-clock `submitted_at`, submitter identity. The version predicate closes the
     * concurrent-submit race; zero updated rows means a lost race (VersionMismatchException).
     */
    fun markSubmittedInTransaction(
        remittanceId: UUID,
        expectedVersion: Int,
        callerId: UUID,
        submittedDate: LocalDate,
    ) {
        val updated =
            RemittanceTable.update({
                (RemittanceTable.id eq remittanceId) and
                    (RemittanceTable.version eq expectedVersion)
            }) {
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.version] = expectedVersion + 1
                it[RemittanceTable.submittedDate] = submittedDate
                it[RemittanceTable.submittedAt] = CurrentTimestampWithTimeZone
                it[RemittanceTable.submittedBy] = callerId
            }

        if (updated == 0) {
            throw remittanceVersionMismatch(remittanceId)
        }
    }

    /**
     * Atomically reverts a SUBMITTED remittance to DRAFT (undo): version predicate + status
     * predicate in the WHERE close both the concurrent-edit and double-undo races; `submitted_at`
     * is cleared (a reverted draft has no submission instant).
     */
    fun markRevertedToDraftInTransaction(
        remittanceId: UUID,
        expectedVersion: Int,
    ) {
        val updated =
            RemittanceTable.update({
                (RemittanceTable.id eq remittanceId) and
                    (RemittanceTable.version eq expectedVersion) and
                    (RemittanceTable.status eq RemittanceStatus.SUBMITTED)
            }) {
                it[RemittanceTable.status] = RemittanceStatus.DRAFT
                it[RemittanceTable.version] = expectedVersion + 1
                it[RemittanceTable.submittedAt] = null
            }
        if (updated == 0) {
            throw remittanceVersionMismatch(remittanceId)
        }
    }

    fun updateHeaderInTransaction(params: UpdateHeaderParams): Remittance {
        val updated =
            RemittanceTable.update({
                (RemittanceTable.id eq params.remittanceId) and
                    (RemittanceTable.version eq params.expectedVersion) and
                    (RemittanceTable.status eq RemittanceStatus.DRAFT)
            }) {
                it[RemittanceTable.type] = params.type
                it[RemittanceTable.method] = params.method
                it[RemittanceTable.dateRangeStart] = params.dateRangeStart
                it[RemittanceTable.dateRangeEnd] = params.dateRangeEnd
                it[RemittanceTable.version] = params.expectedVersion + 1
            }
        if (updated == 0) {
            throw remittanceVersionMismatch(params.remittanceId)
        }

        return findByIdInTransaction(params.remittanceId)
            ?: error("remittance not found after header update for ${params.remittanceId}")
    }

    fun findBreakdownDayIdsInTransaction(remittanceId: UUID): List<UUID> =
        RemittanceDayBreakdownTable
            .selectAll()
            .where { RemittanceDayBreakdownTable.remittanceId eq remittanceId }
            .map { it[RemittanceDayBreakdownTable.branchDayId] }

    /**
     * #507 — surviving SUBMITTED coverage for [dayIds] excluding [excludeRemittanceId].
     * The undo path retains REMITTED on every day in the returned set; all reads run on
     * the caller's open transaction so the check joins the same SERIALIZABLE order as
     * submit/undo day locks.
     */
    fun findDaysCoveredByOtherSubmittedInTransaction(
        dayIds: List<UUID>,
        excludeRemittanceId: UUID,
    ): Set<UUID> {
        if (dayIds.isEmpty()) return emptySet()
        return RemittanceDayBreakdownTable
            .innerJoin(RemittanceTable, { RemittanceDayBreakdownTable.remittanceId }, { RemittanceTable.id })
            .select(RemittanceDayBreakdownTable.branchDayId)
            .where {
                (RemittanceDayBreakdownTable.branchDayId inList dayIds) and
                    (RemittanceTable.status eq RemittanceStatus.SUBMITTED) and
                    (RemittanceTable.id neq excludeRemittanceId)
            }.map { it[RemittanceDayBreakdownTable.branchDayId] }
            .toSet()
    }

    fun sumGrossIncomeInTransaction(remittanceId: UUID): BigDecimal =
        RemittancePolicy.sum(
            RemittanceLineTable
                .leftJoin(
                    ActiveSessionVoidsView,
                    { RemittanceLineTable.sessionId },
                    { ActiveSessionVoidsView.sessionId },
                ).selectAll()
                .where {
                    (RemittanceLineTable.remittanceId eq remittanceId) and
                        (RemittanceLineTable.type eq RemittanceLineType.SESSION) and
                        RemittanceLineTable.deletedAt.isNull() and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.map { it[RemittanceLineTable.amount] },
        )

    /** Read wrapper for drift reads — delegates to the in-transaction sum. */
    fun calculateCompensationSum(breakdownIds: List<UUID>): BigDecimal =
        transaction { sumCompensationsInTransaction(breakdownIds) }

    /** Read wrapper for drift reads — delegates to the in-transaction sum. */
    fun calculateExpenseSum(breakdownIds: List<UUID>): BigDecimal =
        transaction { sumExpensesInTransaction(breakdownIds) }

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

    /** The database server's current time — the clock that stamps `submitted_at` (#103). */
    fun currentDatabaseTimeInTransaction(): OffsetDateTime =
        RemittanceTable
            .select(CurrentTimestampWithTimeZone)
            .first()[CurrentTimestampWithTimeZone]

    /**
     * Maps the submitted-range exclusion/unique violations to the domain conflict; null for any
     * other database error so the caller can rethrow it unchanged.
     */
    internal fun remittanceOverlapError(error: ExposedSQLException): ConflictException? {
        val databaseMessage = error.message.orEmpty()
        val isRemittanceOverlap =
            databaseMessage.contains(SUBMITTED_DATE_INDEX_NAME) ||
                databaseMessage.contains(REMITTANCE_OVERLAP_CONSTRAINT_NAME)
        return if (
            isRemittanceOverlap &&
            (error.sqlState == UNIQUE_VIOLATION_SQL_STATE || error.sqlState == EXCLUSION_VIOLATION_SQL_STATE)
        ) {
            ConflictException("Remittance overlaps an already submitted remittance")
        } else {
            null
        }
    }

    private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    private const val EXCLUSION_VIOLATION_SQL_STATE = "23P01"
    private const val SUBMITTED_DATE_INDEX_NAME = "idx_remittance_submitted_date"
    private const val REMITTANCE_OVERLAP_CONSTRAINT_NAME = "no_remittance_overlap"

    fun findByBranchId(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> =
        transaction {
            val query =
                RemittanceTable
                    .leftJoin(
                        RemittanceFinancialSnapshotTable,
                        { RemittanceTable.id },
                        { RemittanceFinancialSnapshotTable.remittanceId },
                    ).selectAll()
            val rows =
                if (status == null) {
                    query.where { RemittanceTable.branchId eq branchId }
                } else {
                    query.where {
                        (RemittanceTable.branchId eq branchId) and (RemittanceTable.status eq status)
                    }
                }
            rows
                .orderBy(
                    RemittanceTable.createdAt to SortOrder.DESC,
                    RemittanceTable.id to SortOrder.DESC,
                ).map { row ->
                    RemittanceWithNet(
                        remittance = row.toRemittance(),
                        netIncome = row.getOrNull(RemittanceFinancialSnapshotTable.netIncome),
                    )
                }
        }

    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> =
        transaction {
            SessionTable
                .innerJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
                .innerJoin(ClientTable, { SessionTable.clientId }, { ClientTable.id })
                .leftJoin(ActiveSessionVoidsView, { SessionTable.id }, { ActiveSessionVoidsView.sessionId })
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    SessionTable.createdAt to SortOrder.ASC,
                    SessionTable.id to SortOrder.ASC,
                ).map { row ->
                    RemittanceSessionPickerEntry(
                        id = row[SessionTable.id],
                        clientName =
                            buildClientName(
                                firstName = row[ClientTable.firstName],
                                middleName = row[ClientTable.middleName],
                                lastName = row[ClientTable.lastName],
                                suffix = row[ClientTable.suffix],
                            ),
                        bookedAt = row[SessionTable.bookedAt],
                        sessionStatus = row[SessionTable.sessionStatus],
                        finalPrice = row[SessionTable.finalPrice],
                    )
                }
        }

    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> =
        transaction {
            ProductSaleTable
                .innerJoin(BranchDayTable, { ProductSaleTable.branchDayId }, { BranchDayTable.id })
                // #858 — voided-session sales are excluded from all financial
                // calculations: a null session link never joins, so walk-in sales
                // without a session pass the IS NULL filter; an unvoided session
                // drops out of the view and its sales return to the picker.
                .leftJoin(ActiveSessionVoidsView, { ProductSaleTable.sessionId }, { ActiveSessionVoidsView.sessionId })
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    ProductSaleTable.soldAt to SortOrder.ASC,
                    ProductSaleTable.id to SortOrder.ASC,
                ).map { row ->
                    RemittanceProductSalePickerEntry(
                        id = row[ProductSaleTable.id],
                        productName = row[ProductSaleTable.productName],
                        quantity = row[ProductSaleTable.quantity],
                        totalAmountAtTime = row[ProductSaleTable.totalAmountAtTime],
                        soldAt = row[ProductSaleTable.soldAt],
                    )
                }
        }

    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<BranchDay> =
        transaction {
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to)
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    BranchDayTable.id to SortOrder.ASC,
                ).map { it.toBranchDay() }
        }

    private fun buildClientName(
        firstName: String?,
        middleName: String?,
        lastName: String?,
        suffix: String?,
    ): String? {
        val parts = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
        val suffixPart = suffix?.takeIf { it.isNotBlank() }
        val joined = parts.joinToString(" ").ifBlank { null }
        return when {
            joined == null -> suffixPart
            suffixPart == null -> joined
            else -> "$joined $suffixPart"
        }
    }

    private fun ResultRow.toBranchDay(): BranchDay =
        BranchDay(
            id = this[BranchDayTable.id],
            branchId = this[BranchDayTable.branchId],
            date = this[BranchDayTable.date],
            status = this[BranchDayTable.status],
        )

    private fun ResultRow.toRemittance(): Remittance =
        Remittance(
            id = this[RemittanceTable.id],
            type = this[RemittanceTable.type],
            status = this[RemittanceTable.status],
            branchId = this[RemittanceTable.branchId],
            method = this[RemittanceTable.method],
            submittedDate = this[RemittanceTable.submittedDate],
            submittedAt = this[RemittanceTable.submittedAt],
            submittedBy = this[RemittanceTable.submittedBy],
            dateRangeStart = this[RemittanceTable.dateRangeStart],
            dateRangeEnd = this[RemittanceTable.dateRangeEnd],
            createdAt = this[RemittanceTable.createdAt],
            version = this[RemittanceTable.version],
        )
}

data class RemittanceSubmissionResult(
    val remittance: Remittance,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)
