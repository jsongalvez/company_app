package com.companyb.companyapp.remittance

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.CommerceReads
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.session.SessionReads
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Draft content writes and checks on the workflow's open transaction. */
internal object RemittanceContents {
    fun addLineInTransaction(
        callerId: UUID,
        remittanceId: UUID,
        new: NewRemittanceLine,
    ): RemittanceLine {
        // parent lock first so the draft check, range check, and version
        // bump serialize with submit/undo and sibling mutations.
        val locked =
            RemittanceRepository.lockByIdInTransaction(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        RemittancePolicy.assertDraft(locked.status, "add lines to")

        val params =
            AddLineParams(
                id = new.id,
                remittanceId = remittanceId,
                type = new.type,
                sessionId = new.sessionId,
                productSaleId = new.productSaleId,
                amount = new.amount,
                createdBy = callerId,
                expectedVersion = locked.version,
            )
        RemittanceLineRepository.findExistingRequestInTransaction(params)?.let { return it }
        RemittancePolicy.assertLineFitsKind(locked.type, new.type)

        requireSourceInRange(new.type, new.sessionId, new.productSaleId, locked)

        val addResult = RemittanceLineRepository.addLineInTransaction(params)
        if (addResult.created) {
            RemittanceAudit.lineInserted(callerId, locked.branchId, addResult.line)
        }
        return addResult.line
    }

    fun removeLineInTransaction(
        callerId: UUID,
        remittanceId: UUID,
        lineId: UUID,
    ): RemittanceLine {
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
        return after
    }

    fun addDayBreakdownInTransaction(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        branchDayId: UUID,
    ): RemittanceDayBreakdown {
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
        return addResult.breakdown
    }

    fun removeDayBreakdownInTransaction(
        callerId: UUID,
        remittanceId: UUID,
        breakdownId: UUID,
    ): RemittanceDayBreakdown {
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
        return deleted
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

    fun assertSubmitCoverageInTransaction(
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
     * Source reads use the caller's open transaction.
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

    fun assertExistingContentInRange(
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
}

data class NewRemittanceLine(
    val id: UUID,
    val type: RemittanceLineType,
    val sessionId: UUID?,
    val productSaleId: UUID?,
    val amount: BigDecimal,
)
