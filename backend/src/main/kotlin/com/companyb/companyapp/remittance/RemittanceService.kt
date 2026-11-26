package com.companyb.companyapp.remittance

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
    fun submit(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
    ): RemittanceSubmissionResult {
        return traced("RemittanceService.submit") {
            val result =
                try {
                    transaction(transactionIsolation = WORKFLOW_ISOLATION) {
                        submitInTransaction(callerId, remittanceId, expectedVersion)
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

    private fun submitInTransaction(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
    ): RemittanceSubmissionResult? {
        val before =
            RemittanceRepository.lockByIdInTransaction(remittanceId)
                ?: return null

        RemittancePolicy.assertSubmittable(before.status, before.version, expectedVersion, remittanceId)

        val breakdownIds = RemittanceRepository.findBreakdownDayIdsInTransaction(remittanceId)
        // lock covered days before the aggregate reads so a concurrent
        // day-gated write cannot commit between the sums and the REMITTED
        // transition (same sorted order as undo; the later
        // markDaysRemittedInTransaction re-lock is a no-op).
        BranchDayService.lockDaysInTransaction(breakdownIds)
        RemittanceContents.assertSubmitCoverageInTransaction(remittanceId, breakdownIds)
        val grossIncome = RemittanceTotalsRepository.sumGrossIncomeInTransaction(remittanceId)
        val totalCompensation = RemittanceTotalsRepository.sumCompensationsInTransaction(breakdownIds)
        val totalExpenses = RemittanceTotalsRepository.sumExpensesInTransaction(breakdownIds)
        val netIncome = RemittancePolicy.netOf(grossIncome, totalCompensation, totalExpenses)

        val insertedSnapshot =
            if (RemittancePolicy.requiresSnapshot(before.type)) {
                insertSnapshotInTransaction(
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

        return RemittanceSubmissionResult(
            remittance = after,
            grossIncome = grossIncome,
            totalCompensation = totalCompensation,
            totalExpenses = totalExpenses,
            netIncome = netIncome,
        )
    }

    private fun insertSnapshotInTransaction(
        params: RemittanceFinancialSnapshotCreateParams,
    ): RemittanceFinancialSnapshot {
        // Rows legal one by one can still sum past NUMERIC(10,2).
        validateMoneyAmount(params.grossIncome, "grossIncome")
        validateMoneyAmount(params.totalCompensation, "totalCompensation")
        validateMoneyAmount(params.totalExpenses, "totalExpenses")
        validateMoneyAmount(params.netIncome, "netIncome")
        return RemittanceFinancialSnapshotRepository.insertInTransaction(params)
    }

    fun createDraft(
        callerId: UUID,
        new: NewRemittance,
    ): Remittance {
        return traced("RemittanceService.createDraft") {
            BranchService.findById(new.branchId)

            val today = BranchDayService.currentOperationalDate()
            val result =
                transaction {
                    val createResult =
                        RemittanceRepository.createDraftInTransaction(
                            CreateDraftParams(
                                id = new.id,
                                type = new.type,
                                branchId = new.branchId,
                                method = new.method,
                                dateRangeStart = new.dateRangeStart,
                                dateRangeEnd = new.dateRangeEnd,
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

    fun updateHeader(
        callerId: UUID,
        remittanceId: UUID,
        edit: RemittanceHeader,
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
                    if (edit.type != locked.type &&
                        RemittanceLineRepository.findByRemittanceIdInTransaction(remittanceId).isNotEmpty()
                    ) {
                        throw ValidationException("Remove the included items before changing the kind.")
                    }

                    RemittanceContents.assertExistingContentInRange(
                        remittanceId = remittanceId,
                        rangeStart = edit.dateRangeStart,
                        rangeEnd = edit.dateRangeEnd,
                    )

                    val after =
                        RemittanceRepository.updateHeaderInTransaction(
                            UpdateHeaderParams(
                                remittanceId = remittanceId,
                                expectedVersion = expectedVersion,
                                type = edit.type,
                                method = edit.method,
                                dateRangeStart = edit.dateRangeStart,
                                dateRangeEnd = edit.dateRangeEnd,
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

    fun addLine(
        callerId: UUID,
        remittanceId: UUID,
        new: NewRemittanceLine,
    ): RemittanceLine {
        return traced("RemittanceService.addLine") {
            validateMoneyAmount(new.amount, "amount")
            val line =
                transaction {
                    RemittanceContents.addLineInTransaction(callerId, remittanceId, new)
                }

            logger.info { "[ADD-REMITTANCE-LINE] Line ${line.id} added to remittance $remittanceId" }
            return@traced line
        }
    }

    fun removeLine(
        callerId: UUID,
        remittanceId: UUID,
        lineId: UUID,
    ): RemittanceLine {
        return traced("RemittanceService.removeLine") {
            val line =
                transaction {
                    RemittanceContents.removeLineInTransaction(callerId, remittanceId, lineId)
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
                    RemittanceContents.addDayBreakdownInTransaction(callerId, remittanceId, id, branchDayId)
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
                    RemittanceContents.removeDayBreakdownInTransaction(callerId, remittanceId, breakdownId)
                }

            logger.info {
                "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown $breakdownId removed from remittance $remittanceId"
            }
            return@traced breakdown
        }
    }

    fun getRemittance(remittanceId: UUID): RemittanceDetail = RemittanceReadService.getRemittance(remittanceId)

    fun listRemittances(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> = RemittanceReadService.listRemittances(branchId, status)

    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> = RemittanceReadService.findSessionsInRange(branchId, from, to)

    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> = RemittanceReadService.findProductSalesInRange(branchId, from, to)

    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceDayPickerEntry> = RemittanceReadService.findBranchDaysInRange(branchId, from, to)

    fun getDrift(remittanceId: UUID): RemittanceDrift = RemittanceReadService.getDrift(remittanceId)
}
