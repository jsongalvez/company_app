package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** The persisted remittance table name, for domain exceptions thrown outside persistence code. */
internal const val REMITTANCE_TABLE_NAME = "remittance"

internal fun remittanceVersionMismatch(recordId: UUID): VersionMismatchException =
    VersionMismatchException(REMITTANCE_TABLE_NAME, recordId)

/**
 * Pure remittance state and financial policy (#320). Every rule here is testable without a
 * database: financial arithmetic, snapshot eligibility, DRAFT/SUBMITTED transition validation,
 * and the 48-hour undo window. Persistence code applies these rules inside command-owned
 * transactions; this object owns the rules themselves.
 */
object RemittancePolicy {
    /** Server-enforced undo window (#103): how long a SUBMITTED remittance may revert to DRAFT. */
    const val UNDO_WINDOW_HOURS = 48L

    fun netOf(
        grossIncome: BigDecimal,
        totalCompensation: BigDecimal,
        totalExpenses: BigDecimal,
    ): BigDecimal = grossIncome.subtract(totalCompensation).subtract(totalExpenses)

    /** Sums line amounts in insertion order at full scale — no rounding anywhere in remittance math. */
    fun sum(amounts: List<BigDecimal>): BigDecimal = amounts.fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }

    /**
     * Only SESSION remittances freeze an immutable financial snapshot; PRODUCT remittances have
     * no session income to freeze.
     */
    fun requiresSnapshot(type: RemittanceType): Boolean = type == RemittanceType.SESSION

    /** Transition guard for submit: the row must be a DRAFT at [expectedVersion]. */
    fun assertSubmittable(
        status: RemittanceStatus,
        actualVersion: Int,
        expectedVersion: Int,
        remittanceId: UUID,
    ) {
        if (actualVersion != expectedVersion) throw remittanceVersionMismatch(remittanceId)
        if (status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only submit DRAFT remittances")
        }
    }

    /** Transition guard for undo: the row must be SUBMITTED at [expectedVersion]. */
    fun assertUndoable(
        status: RemittanceStatus,
        actualVersion: Int,
        expectedVersion: Int,
        remittanceId: UUID,
    ) {
        if (status != RemittanceStatus.SUBMITTED) {
            throw ValidationException("Can only undo SUBMITTED remittances")
        }
        if (actualVersion != expectedVersion) throw remittanceVersionMismatch(remittanceId)
    }

    /** Transition guard for draft-only mutations (header edits, lines, day breakdowns). */
    fun assertDraft(
        status: RemittanceStatus,
        mutation: String,
    ) {
        if (status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only $mutation DRAFT remittances")
        }
    }

    /**
     * Range-membership guard (#483): a line or day breakdown may only join a remittance whose
     * loaded range covers its branch-day date (inclusive on both ends). The pickers only offer
     * in-range entries, so an out-of-range write means a stale client or a forged request.
     */
    fun assertDateInRange(
        date: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        what: String,
    ) {
        if (date.isBefore(rangeStart) || date.isAfter(rangeEnd)) {
            throw ValidationException("$what date $date is outside remittance range $rangeStart..$rangeEnd")
        }
    }

    /**
     * Undo-window boundary: undo is allowed up to and including exactly 48 hours after the
     * submission instant; any instant strictly after that is expired. Both instants come from
     * the database clock in production (the same clock that stamps `submitted_at`).
     */
    fun assertWithinUndoWindow(
        submissionInstant: OffsetDateTime,
        comparisonInstant: OffsetDateTime,
    ) {
        if (comparisonInstant.isAfter(submissionInstant.plusHours(UNDO_WINDOW_HOURS))) {
            throw ValidationException("Undo window of $UNDO_WINDOW_HOURS hours has expired")
        }
    }
}
