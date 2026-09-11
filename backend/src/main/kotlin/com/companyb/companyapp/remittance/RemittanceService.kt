package com.companyb.companyapp.remittance

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.CommerceReads
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.session.SessionReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Remittance feature commands (#320, ADR-0024). Each mutating command owns exactly one business
 * transaction — SERIALIZABLE for the financial submit/undo workflow — choosing its isolation
 * there. Persistence runs on that transaction via the internal `*InTransaction` stores, Branch
 * Day transitions go through the [BranchDayService] boundary (which writes its own domain audit
 * on that same transaction, #603), and the remittance audit rows are inserted directly into
 * the same transaction ([RemittanceAudit]), so every mutation commits together with its audit
 * trail or not at all. Pure state/financial rules live in [RemittancePolicy].
 */
object RemittanceService {
    private val logger = KotlinLogging.logger {}

    private const val SERIALIZABLE_ISOLATION = Connection.TRANSACTION_SERIALIZABLE

    // #595: SERIALIZABLE submit workflow stays in one command-owned transaction (ADR-0024);
    // splitting the snapshot/day-transition sequence would break atomicity.

    /**
     * Freezes the draft into a SUBMITTED remittance: locks the row, validates the transition,
     * computes the financial totals from live lines/compensations/expenses, writes the immutable
     * SESSION snapshot, stamps the submission, marks every covered day REMITTED through the
     * Branch Day boundary, and audits all of it inside one SERIALIZABLE transaction.
     */
    @Suppress("LongMethod") // #595
    fun submit(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
    ): RemittanceSubmissionResult {
        val result =
            try {
                transaction(transactionIsolation = SERIALIZABLE_ISOLATION) {
                    val before =
                        RemittanceRepository.lockByIdInTransaction(remittanceId)
                            ?: return@transaction null

                    RemittancePolicy.assertSubmittable(before.status, before.version, expectedVersion, remittanceId)

                    val breakdownIds = RemittanceRepository.findBreakdownDayIdsInTransaction(remittanceId)
                    // #517 — lock covered days before the aggregate reads so a concurrent
                    // day-gated write cannot commit between the sums and the REMITTED
                    // transition (same sorted order as #507 undo; the later
                    // markDaysRemittedInTransaction re-lock is a no-op).
                    BranchDayService.lockDaysInTransaction(breakdownIds)
                    // #866 — lines and breakdowns must describe the same days before the
                    // immutable snapshot freezes gross (lines) with comp/expenses
                    // (breakdowns); all reads stay `*InTransaction` on this SERIALIZABLE
                    // transaction and run before any state change.
                    assertSubmitCoverageInTransaction(remittanceId, breakdownIds)
                    val grossIncome = RemittanceRepository.sumGrossIncomeInTransaction(remittanceId)
                    val totalCompensation = RemittanceRepository.sumCompensationsInTransaction(breakdownIds)
                    val totalExpenses = RemittanceRepository.sumExpensesInTransaction(breakdownIds)
                    val netIncome = RemittancePolicy.netOf(grossIncome, totalCompensation, totalExpenses)

                    val insertedSnapshot =
                        if (RemittancePolicy.requiresSnapshot(before.type)) {
                            RemittanceFinancialSnapshotRepository.insertInTransaction(
                                RemittanceFinancialSnapshotCreateParams(
                                    remittanceId = remittanceId,
                                    grossIncome = grossIncome,
                                    totalCompensation = totalCompensation,
                                    totalExpenses = totalExpenses,
                                    netIncome = netIncome,
                                ),
                            )
                        } else {
                            null
                        }

                    RemittanceRepository.markSubmittedInTransaction(
                        remittanceId,
                        expectedVersion,
                        callerId,
                        submittedDate = BranchDayService.currentOperationalDate(),
                    )
                    BranchDayService.markDaysRemittedInTransaction(breakdownIds, changedBy = callerId)

                    val after =
                        RemittanceRepository.findByIdInTransaction(remittanceId)
                            ?: error("remittance not found after submit for $remittanceId")

                    RemittanceAudit.remittanceUpdated(callerId, before, after)
                    insertedSnapshot?.let { snapshot ->
                        RemittanceAudit.snapshotInserted(callerId, after.branchId, snapshot)
                    }

                    RemittanceSubmissionResult(
                        remittance = after,
                        grossIncome = grossIncome,
                        totalCompensation = totalCompensation,
                        totalExpenses = totalExpenses,
                        netIncome = netIncome,
                    )
                }
            } catch (error: ExposedSQLException) {
                throw RemittanceRepository.remittanceOverlapError(error) ?: error
            } ?: throw NotFoundException("Remittance not found")

        logger.info {
            "[SUBMIT-REMITTANCE] Remittance $remittanceId submitted. Gross=${result.grossIncome} " +
                "Net=${result.netIncome}"
        }
        return result
    }

    // #595: 7-param creation command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #595
    fun createDraft(
        callerId: UUID,
        id: UUID,
        type: RemittanceType,
        branchId: UUID,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
    ): Remittance {
        BranchService.findById(branchId)

        val today = BranchDayService.currentOperationalDate()
        val result =
            transaction {
                val createResult =
                    RemittanceRepository.createDraftInTransaction(
                        CreateDraftParams(
                            id = id,
                            type = type,
                            branchId = branchId,
                            method = method,
                            dateRangeStart = dateRangeStart,
                            dateRangeEnd = dateRangeEnd,
                            submittedDate = today,
                            submittedBy = callerId,
                        ),
                    )
                if (createResult.created) {
                    RemittanceAudit.draftInserted(callerId, createResult.remittance)
                }
                createResult
            }
        logger.info { "[CREATE-REMITTANCE-DRAFT] Remittance ${result.remittance.id} created=${result.created}" }
        return result.remittance
    }

    fun undo(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
        reason: String,
    ): Remittance =
        undoAt(
            callerId = callerId,
            remittanceId = remittanceId,
            expectedVersion = expectedVersion,
            reason = reason,
        )

    // #595: SERIALIZABLE undo workflow stays in one command-owned transaction (ADR-0024);
    // splitting the snapshot-delete/day-release sequence would break atomicity.

    /**
     * Reverts a SUBMITTED remittance to DRAFT within the server-enforced 48-hour window
     * ([RemittancePolicy.assertWithinUndoWindow], database clock), deletes the immutable
     * snapshot (V13 trigger carve-out), releases every covered day back through the Branch Day
     * boundary, and audits all of it inside one SERIALIZABLE transaction.
     *
     * [now] remains an explicit test seam for deterministic boundary tests.
     */
    @Suppress("LongMethod") // #595
    internal fun undoAt(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
        reason: String,
        now: OffsetDateTime? = null,
    ): Remittance {
        val remittance =
            transaction(transactionIsolation = SERIALIZABLE_ISOLATION) {
                val before =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: return@transaction null

                RemittancePolicy.assertUndoable(before.status, before.version, expectedVersion, remittanceId)

                val submissionInstant =
                    before.submittedAt
                        ?: RemittanceFinancialSnapshotRepository
                            .findByRemittanceIdInTransaction(remittanceId)
                            ?.snapshottedAt
                        ?: throw ValidationException("No submission timestamp — cannot undo this remittance")
                RemittancePolicy.assertWithinUndoWindow(
                    submissionInstant,
                    comparisonInstant = now ?: RemittanceRepository.currentDatabaseTimeInTransaction(),
                )

                val breakdownIds = RemittanceRepository.findBreakdownDayIdsInTransaction(remittanceId)

                RemittanceRepository.markRevertedToDraftInTransaction(remittanceId, expectedVersion)
                val snapshotBefore =
                    RemittanceFinancialSnapshotRepository.deleteByRemittanceIdInTransaction(remittanceId)
                // #507 — surviving SUBMITTED coverage keeps REMITTED. Lock days in stable
                // order before the coverage read so cross-type submit/undo serialize.
                BranchDayService.lockDaysInTransaction(breakdownIds)
                val retainRemitted =
                    RemittanceRepository.findDaysCoveredByOtherSubmittedInTransaction(
                        breakdownIds,
                        remittanceId,
                    )
                BranchDayService.releaseDaysFromRemittanceInTransaction(
                    breakdownIds,
                    today = BranchDayService.currentOperationalDate(),
                    retainRemitted = retainRemitted,
                    changedBy = callerId,
                    reason = reason,
                )

                val after =
                    RemittanceRepository.findByIdInTransaction(remittanceId)
                        ?: error("remittance not found after undo for $remittanceId")

                RemittanceAudit.remittanceUpdated(callerId, before, after, reason)
                snapshotBefore?.let { snapshot ->
                    RemittanceAudit.snapshotDeleted(callerId, after.branchId, snapshot, reason)
                }

                after
            } ?: throw NotFoundException("Remittance not found")

        logger.info {
            "[UNDO-REMITTANCE] Remittance ${remittanceId.toString().maskUUID()} undone"
        }
        return remittance
    }

    // #595: 7-param header-update command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #595
    fun updateHeader(
        callerId: UUID,
        remittanceId: UUID,
        type: RemittanceType,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
        expectedVersion: Int,
    ): Remittance {
        val remittance =
            transaction {
                // #506 — parent lock first, then draft + content checks under it so a
                // concurrent breakdown insert cannot orphan content outside the new range.
                val locked =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: throw NotFoundException("Remittance not found")

                RemittancePolicy.assertDraft(locked.status, "update header of")

                assertExistingContentInRange(
                    remittanceId = remittanceId,
                    rangeStart = dateRangeStart,
                    rangeEnd = dateRangeEnd,
                )

                val after =
                    RemittanceRepository.updateHeaderInTransaction(
                        UpdateHeaderParams(
                            remittanceId = remittanceId,
                            expectedVersion = expectedVersion,
                            type = type,
                            method = method,
                            dateRangeStart = dateRangeStart,
                            dateRangeEnd = dateRangeEnd,
                        ),
                    )

                RemittanceAudit.remittanceUpdated(callerId, locked, after)
                after
            }

        logger.info {
            "[UPDATE-REMITTANCE-HEADER] Remittance ${remittanceId.toString().maskUUID()} header updated"
        }
        return remittance
    }

    // #595: 7-param line-add command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #595
    fun addLine(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        amount: BigDecimal,
    ): RemittanceLine {
        val line =
            transaction {
                // #506 — parent lock first so the draft check, range check, and version
                // bump serialize with submit/undo and sibling mutations.
                val locked =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: throw NotFoundException("Remittance not found")

                RemittancePolicy.assertDraft(locked.status, "add lines to")

                val params =
                    AddLineParams(
                        id = id,
                        remittanceId = remittanceId,
                        type = type,
                        sessionId = sessionId,
                        productSaleId = productSaleId,
                        amount = amount,
                        createdBy = callerId,
                        expectedVersion = locked.version,
                    )
                RemittanceLineRepository.findExistingRequestInTransaction(params)?.let { return@transaction it }

                requireSourceInRange(type, sessionId, productSaleId, locked)

                val addResult = RemittanceLineRepository.addLineInTransaction(params)
                if (addResult.created) {
                    RemittanceAudit.lineInserted(callerId, locked.branchId, addResult.line)
                }
                addResult.line
            }

        logger.info { "[ADD-REMITTANCE-LINE] Line ${line.id} added to remittance $remittanceId" }
        return line
    }

    private fun requireSourceInRange(
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        remittance: Remittance,
    ) {
        val sourceBranchDayId =
            when (type) {
                RemittanceLineType.SESSION -> {
                    val sourceId = sessionId ?: throw ValidationException("sessionId is required for SESSION line type")
                    val session =
                        SessionReads.findByIdInTransaction(sourceId)
                            ?: throw NotFoundException("Session not found")
                    // #750 — voided sessions stay visible but are excluded from financial
                    // calculations, so attaching one is an explicit 400 (branch mismatch stays 404).
                    if (SessionReads.isVoidedInTransaction(sourceId)) {
                        throw ValidationException("Session is voided")
                    }
                    // #857 — PENDING income is unrealized and NO_SHOW/CANCELLED sessions
                    // produce no income, while daily_sales_summary.gross_income counts only
                    // COMPLETED sessions: attaching any other status is an explicit 400 so
                    // the frozen snapshot can never diverge from the reporting view.
                    if (session.sessionStatus != SessionStatus.COMPLETED) {
                        throw ValidationException("Only COMPLETED sessions can be added to a remittance")
                    }
                    session.branchDayId
                }

                RemittanceLineType.PRODUCT_SALE -> {
                    val sourceId =
                        productSaleId
                            ?: throw ValidationException("productSaleId is required for PRODUCT_SALE line type")
                    val sale =
                        CommerceReads.findSaleByIdInTransaction(sourceId)
                            ?: throw NotFoundException("Product sale not found")
                    // #858 — voided-session sales are excluded from all financial
                    // calculations, so attaching one is an explicit 400 (same as
                    // SESSION #750; unknown sale stays 404 above).
                    val linkedSessionId = sale.sessionId
                    if (linkedSessionId != null && SessionReads.isVoidedInTransaction(linkedSessionId)) {
                        throw ValidationException("Product sale's session is voided")
                    }
                    sale.branchDayId
                }

                // #876 — the forward-compat sentinel is never valid input.
                RemittanceLineType.UNKNOWN -> {
                    throw ValidationException("Unknown remittance line type")
                }
            }
        // #483 — the pickers only offer the loaded range, so an out-of-range source is a
        // stale-client or forged write; the branch check stays 404 (indistinguishable),
        // the range check is an explicit 400.
        val day =
            BranchDayService.findByIdInTransaction(sourceBranchDayId)
                ?: throw NotFoundException("Branch day not found")
        if (day.branchId != remittance.branchId) {
            throw NotFoundException("Source does not belong to remittance branch")
        }
        RemittancePolicy.assertDateInRange(
            day.date,
            remittance.dateRangeStart,
            remittance.dateRangeEnd,
            "Source",
        )
    }

    fun removeLine(
        callerId: UUID,
        remittanceId: UUID,
        lineId: UUID,
    ): RemittanceLine {
        val line =
            transaction {
                // #506 — parent lock first so the draft check and version bump join the
                // same serialization order as every other aggregate mutation.
                val locked =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: throw NotFoundException("Remittance not found")

                RemittancePolicy.assertDraft(locked.status, "delete lines from")

                val (before, after) =
                    RemittanceLineRepository.softDeleteLineInTransaction(
                        lineId,
                        remittanceId,
                        callerId,
                        locked.version,
                    ) ?: throw NotFoundException("Remittance line not found")

                // An idempotent retry of an already-deleted line returns it unchanged, no new audit.
                if (before.deletedAt == null) {
                    RemittanceAudit.lineUpdated(callerId, locked.branchId, before, after)
                }
                after
            }

        logger.info { "[DELETE-REMITTANCE-LINE] Line $lineId deleted from remittance $remittanceId" }
        return line
    }

    fun addDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        branchDayId: UUID,
    ): RemittanceDayBreakdown {
        val breakdown =
            transaction {
                // #506 — parent lock first, then draft + range checks; the version bump
                // makes this content change visible to submit/header stale-version guards.
                val locked =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: throw NotFoundException("Remittance not found")

                RemittancePolicy.assertDraft(locked.status, "add day breakdowns to")

                // #483 — same range contract as lines: the day picker only offers the loaded
                // range, so an out-of-range day is a stale-client or forged write (400).
                val day = BranchDayService.requireBranchDayForBranch(branchDayId, locked.branchId)
                RemittancePolicy.assertDateInRange(
                    day.date,
                    locked.dateRangeStart,
                    locked.dateRangeEnd,
                    "Branch day",
                )

                val addResult =
                    RemittanceDayBreakdownRepository.addDayBreakdownInTransaction(
                        id = id,
                        remittanceId = remittanceId,
                        branchDayId = branchDayId,
                    )
                if (addResult.created) {
                    RemittanceRepository.bumpVersionInTransaction(remittanceId, locked.version)
                    RemittanceAudit.breakdownInserted(callerId, locked.branchId, addResult.breakdown)
                }
                addResult.breakdown
            }

        logger.info { "[ADD-REMITTANCE-BREAKDOWN] Day breakdown ${breakdown.id} added to remittance $remittanceId" }
        return breakdown
    }

    fun removeDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        breakdownId: UUID,
    ): RemittanceDayBreakdown {
        val breakdown =
            transaction {
                // #506 — parent lock first; the version bump keeps submit/header guards
                // honest about this content change. No-op misses bump nothing.
                val locked =
                    RemittanceRepository.lockByIdInTransaction(remittanceId)
                        ?: throw NotFoundException("Remittance not found")

                RemittancePolicy.assertDraft(locked.status, "remove day breakdowns from")

                val deleted =
                    RemittanceDayBreakdownRepository.deleteDayBreakdownInTransaction(
                        breakdownId = breakdownId,
                        remittanceId = remittanceId,
                    ) ?: throw NotFoundException("Day breakdown not found")

                RemittanceRepository.bumpVersionInTransaction(remittanceId, locked.version)
                RemittanceAudit.breakdownDeleted(callerId, locked.branchId, deleted)
                deleted
            }

        logger.info { "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown $breakdownId removed from remittance $remittanceId" }
        return breakdown
    }

    /**
     * #866 — submit-time reconciliation: gross is summed over lines while comp/expenses are
     * summed over day breakdowns, so every active line's source branch day must sit in the
     * breakdown set and the set itself must be non-empty. All reads are `*InTransaction`
     * on the caller's open SERIALIZABLE transaction and run before any state change.
     */
    private fun assertSubmitCoverageInTransaction(
        remittanceId: UUID,
        breakdownIds: List<UUID>,
    ) {
        RemittancePolicy.assertNonEmptyCoverage(breakdownIds)
        val lineSourceDayIds =
            RemittanceLineRepository.findByRemittanceIdInTransaction(remittanceId).map { line ->
                lineSourceBranchDayIdInTransaction(line)
            }
        RemittancePolicy.assertLinesCoveredByBreakdowns(breakdownIds, lineSourceDayIds)
    }

    /**
     * Line-to-day dispatcher extracted so [assertSubmitCoverageInTransaction] stays
     * under the ThrowsCount gate (RED repair on 84079f7). All reads stay
     * `*InTransaction` on the caller's open transaction.
     */
    private fun lineSourceBranchDayIdInTransaction(line: RemittanceLine): UUID =
        when (line.type) {
            RemittanceLineType.SESSION -> sessionLineBranchDayIdInTransaction(line.sessionId)

            RemittanceLineType.PRODUCT_SALE -> productSaleLineBranchDayIdInTransaction(line.productSaleId)

            // #876 — unreachable: the Postgres enum column carries no UNKNOWN label,
            // so row mapping fails before this runs. Fail closed, never silently skip.
            RemittanceLineType.UNKNOWN -> throw ValidationException("Unknown remittance line type")
        }

    private fun sessionLineBranchDayIdInTransaction(sessionId: UUID?): UUID {
        val id = sessionId ?: throw ValidationException("sessionId is required for SESSION line type")
        return SessionReads.findByIdInTransaction(id)?.branchDayId ?: throw NotFoundException("Session not found")
    }

    private fun productSaleLineBranchDayIdInTransaction(productSaleId: UUID?): UUID {
        val id =
            productSaleId
                ?: throw ValidationException("productSaleId is required for PRODUCT_SALE line type")
        return CommerceReads.findSaleByIdInTransaction(id)?.branchDayId
            ?: throw NotFoundException("Product sale not found")
    }

    /**
     * #483 — a header range edit must not orphan already-added content: every active line's
     * source date and every covered day must sit inside the new range, else 400. All reads
     * here are `*InTransaction` on the caller's open transaction.
     */
    private fun assertExistingContentInRange(
        remittanceId: UUID,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ) {
        RemittanceLineRepository.findByRemittanceIdInTransaction(remittanceId).forEach { line ->
            val sourceBranchDayId = lineSourceBranchDayIdInTransaction(line)
            val date =
                BranchDayService.findByIdInTransaction(sourceBranchDayId)?.date
                    ?: throw NotFoundException("Branch day not found")
            RemittancePolicy.assertDateInRange(date, rangeStart, rangeEnd, "Source")
        }
        RemittanceDayBreakdownRepository.findByRemittanceIdInTransaction(remittanceId).forEach { breakdown ->
            val date =
                BranchDayService.findByIdInTransaction(breakdown.branchDayId)?.date
                    ?: throw NotFoundException("Branch day not found")
            RemittancePolicy.assertDateInRange(date, rangeStart, rangeEnd, "Branch day")
        }
    }

    fun getRemittance(remittanceId: UUID): RemittanceDetail {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        val lines = RemittanceLineRepository.findByRemittanceId(remittanceId)
        val totalAmount = RemittanceLineRepository.sumAmountsByRemittanceId(remittanceId)
        val dayBreakdowns = RemittanceDayBreakdownRepository.findByRemittanceId(remittanceId)
        val snapshot = RemittanceFinancialSnapshotRepository.findByRemittanceId(remittanceId)

        return RemittanceDetail(
            remittance = remittance,
            lines = lines,
            totalAmount = totalAmount,
            dayBreakdowns = dayBreakdowns,
            snapshot = snapshot,
        )
    }

    fun listRemittances(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> {
        BranchService.findById(branchId)
        return RemittanceRepository.findByBranchId(branchId, status)
    }

    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> {
        BranchService.findById(branchId)
        return RemittanceRepository.findSessionsInRange(branchId, from, to)
    }

    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> {
        BranchService.findById(branchId)
        return RemittanceRepository.findProductSalesInRange(branchId, from, to)
    }

    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceDayPickerEntry> {
        BranchService.findById(branchId)
        val today = BranchDayService.currentOperationalDate()
        return RemittanceRepository
            .findBranchDaysInRange(branchId, from, to)
            .map { day ->
                RemittanceDayPickerEntry(
                    id = day.id,
                    date = day.date,
                    status = BranchDayService.evaluateStatus(day.status, day.date, today),
                )
            }
    }

    fun getDrift(remittanceId: UUID): RemittanceDrift {
        RemittanceRepository.findById(remittanceId)
            ?: throw NotFoundException("Remittance not found")

        val snapshot =
            RemittanceFinancialSnapshotRepository.findByRemittanceId(remittanceId)
                ?: throw NotFoundException("No financial snapshot for this remittance")

        val breakdownIds =
            RemittanceDayBreakdownRepository
                .findByRemittanceId(remittanceId)
                .map { it.branchDayId }
        val currentCompensation = RemittanceRepository.calculateCompensationSum(breakdownIds)
        val currentExpenses = RemittanceRepository.calculateExpenseSum(breakdownIds)
        val currentNet = RemittancePolicy.netOf(snapshot.grossIncome, currentCompensation, currentExpenses)

        return RemittanceDrift(
            frozen = snapshot,
            currentCompensation = currentCompensation,
            currentExpenses = currentExpenses,
            currentNet = currentNet,
        )
    }
}

data class RemittanceDetail(
    val remittance: Remittance,
    val lines: List<RemittanceLine>,
    val totalAmount: BigDecimal,
    val dayBreakdowns: List<RemittanceDayBreakdown>,
    val snapshot: RemittanceFinancialSnapshot? = null,
)

data class RemittanceDrift(
    val frozen: RemittanceFinancialSnapshot,
    val currentCompensation: BigDecimal,
    val currentExpenses: BigDecimal,
    val currentNet: BigDecimal,
)

/** One pickable branch day for the remittance day-range picker (#343) — no Branch Day record crosses the seam. */
data class RemittanceDayPickerEntry(
    val id: UUID,
    val date: LocalDate,
    val status: DayStatus,
)
