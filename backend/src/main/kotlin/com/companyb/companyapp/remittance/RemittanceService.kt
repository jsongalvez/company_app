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
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.session.SessionReads
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Remittance feature commands (ADR-0024). Each mutating command owns exactly one business
 * transaction and chooses its isolation there. Persistence runs on that transaction via the
 * internal `*InTransaction` stores, Branch Day transitions go through the [BranchDayService]
 * boundary (which writes its own domain audit on that same transaction), and the remittance audit
 * rows are inserted directly into the same transaction ([RemittanceAudit]), so every mutation
 * commits together with its audit trail or not at all. Pure state/financial rules live in
 * [RemittancePolicy].
 */
object RemittanceService {
    private val logger = KotlinLogging.logger {}

    // Not SERIALIZABLE: Postgres fixes that snapshot at the first statement, before the
    // day locks are held, and day-gated writers run READ COMMITTED where SSI cannot see
    // them, so sums read after the locks would miss a write that committed while we waited.
    // Row locks order submit/undo against those writers; reads after them must be fresh.
    private const val WORKFLOW_ISOLATION = Connection.TRANSACTION_READ_COMMITTED

    /**
     * Freezes the draft into a SUBMITTED remittance: locks the row, validates the transition,
     * computes the financial totals from live lines/compensations/expenses, writes the immutable
     * SESSION snapshot, stamps the submission, marks every covered day REMITTED through the
     * Branch Day boundary, and audits all of it inside one transaction.
     */
    @Suppress("LongMethod") // #595
    fun submit(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
    ): RemittanceSubmissionResult {
        return traced("RemittanceService.submit") {
            val result =
                try {
                    transaction(transactionIsolation = WORKFLOW_ISOLATION) {
                        val before =
                            RemittanceRepository.lockByIdInTransaction(remittanceId)
                                ?: return@transaction null

                        RemittancePolicy.assertSubmittable(before.status, before.version, expectedVersion, remittanceId)

                        val breakdownIds = RemittanceRepository.findBreakdownDayIdsInTransaction(remittanceId)
                        // lock covered days before the aggregate reads so a concurrent
                        // day-gated write cannot commit between the sums and the REMITTED
                        // transition (same sorted order as undo; the later
                        // markDaysRemittedInTransaction re-lock is a no-op).
                        BranchDayService.lockDaysInTransaction(breakdownIds)
                        assertSubmitCoverageInTransaction(remittanceId, breakdownIds)
                        val grossIncome = RemittanceRepository.sumGrossIncomeInTransaction(remittanceId)
                        val totalCompensation = RemittanceRepository.sumCompensationsInTransaction(breakdownIds)
                        val totalExpenses = RemittanceRepository.sumExpensesInTransaction(breakdownIds)
                        val netIncome = RemittancePolicy.netOf(grossIncome, totalCompensation, totalExpenses)

                        val insertedSnapshot =
                            if (RemittancePolicy.requiresSnapshot(before.type)) {
                                // Rows legal one by one can still sum past NUMERIC(10,2).
                                validateMoneyAmount(grossIncome, "grossIncome")
                                validateMoneyAmount(totalCompensation, "totalCompensation")
                                validateMoneyAmount(totalExpenses, "totalExpenses")
                                validateMoneyAmount(netIncome, "netIncome")
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
            return@traced result
        }
    }

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
        return traced("RemittanceService.createDraft") {
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
            return@traced result.remittance
        }
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

    /**
     * Reverts a SUBMITTED remittance to DRAFT within the server-enforced 48-hour window
     * ([RemittancePolicy.assertWithinUndoWindow], database clock), deletes the immutable
     * snapshot (V13 trigger carve-out), releases every covered day back through the Branch Day
     * boundary, and audits all of it inside one transaction.
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
        return traced("RemittanceService.undoAt") {
            val remittance =
                transaction(transactionIsolation = WORKFLOW_ISOLATION) {
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
                    // surviving SUBMITTED coverage keeps REMITTED. Lock days in stable
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
            return@traced remittance
        }
    }

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
        return traced("RemittanceService.updateHeader") {
            val remittance =
                transaction {
                    // parent lock first, then draft + content checks under it so a
                    // concurrent breakdown insert cannot orphan content outside the new range.
                    val locked =
                        RemittanceRepository.lockByIdInTransaction(remittanceId)
                            ?: throw NotFoundException("Remittance not found")

                    RemittancePolicy.assertDraft(locked.status, "update header of")
                    if (type != locked.type &&
                        RemittanceLineRepository.findByRemittanceIdInTransaction(remittanceId).isNotEmpty()
                    ) {
                        throw ValidationException("Remove the included items before changing the kind.")
                    }

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
            return@traced remittance
        }
    }

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
        return traced("RemittanceService.addLine") {
            validateMoneyAmount(amount, "amount")
            val line =
                transaction {
                    // parent lock first so the draft check, range check, and version
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
                    RemittancePolicy.assertLineFitsKind(locked.type, type)

                    requireSourceInRange(type, sessionId, productSaleId, locked)

                    val addResult = RemittanceLineRepository.addLineInTransaction(params)
                    if (addResult.created) {
                        RemittanceAudit.lineInserted(callerId, locked.branchId, addResult.line)
                    }
                    addResult.line
                }

            logger.info { "[ADD-REMITTANCE-LINE] Line ${line.id} added to remittance $remittanceId" }
            return@traced line
        }
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
                    // voided sessions stay visible but are excluded from financial
                    // calculations, so attaching one is an explicit 400 (branch mismatch stays 404).
                    if (SessionReads.isVoidedInTransaction(sourceId)) {
                        throw ValidationException("Session is voided")
                    }
                    // daily_sales_summary counts only COMPLETED sessions; the snapshot must match it.
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
                    // voided-session sales are excluded from all financial
                    // calculations, so attaching one is an explicit 400 (same as
                    // SESSION; unknown sale stays 404 above).
                    val linkedSessionId = sale.sessionId
                    if (linkedSessionId != null && SessionReads.isVoidedInTransaction(linkedSessionId)) {
                        throw ValidationException("Product sale's session is voided")
                    }
                    sale.branchDayId
                }
            }
        // the pickers only offer the loaded range, so an out-of-range source is a
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
        return traced("RemittanceService.removeLine") {
            val line =
                transaction {
                    // parent lock first so the draft check and version bump join the
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
            return@traced line
        }
    }

    fun addDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        branchDayId: UUID,
    ): RemittanceDayBreakdown {
        return traced("RemittanceService.addDayBreakdown") {
            val breakdown =
                transaction {
                    // parent lock first, then draft + range checks; the version bump
                    // makes this content change visible to submit/header stale-version guards.
                    val locked =
                        RemittanceRepository.lockByIdInTransaction(remittanceId)
                            ?: throw NotFoundException("Remittance not found")

                    RemittancePolicy.assertDraft(locked.status, "add day breakdowns to")

                    // same range contract as lines: the day picker only offers the loaded
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
            return@traced breakdown
        }
    }

    fun removeDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        breakdownId: UUID,
    ): RemittanceDayBreakdown {
        return traced("RemittanceService.removeDayBreakdown") {
            val breakdown =
                transaction {
                    // parent lock first; the version bump keeps submit/header guards
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

            logger.info {
                "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown $breakdownId removed from remittance $remittanceId"
            }
            return@traced breakdown
        }
    }

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

/** One pickable branch day for the remittance day-range picker — no Branch Day record crosses the seam. */
data class RemittanceDayPickerEntry(
    val id: UUID,
    val date: LocalDate,
    val status: DayStatus,
)
