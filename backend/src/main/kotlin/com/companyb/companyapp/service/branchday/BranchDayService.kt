package com.companyb.companyapp.service.branchday

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.service.CapabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Shared day-state service. Every operational/financial write must call [checkBranchDayEditable]
 * before mutating, and create the owning day via [resolveOrCreate].
 *
 * Day state is lazily evaluated against the current Asia/Manila calendar date: a day that is
 * still OPEN but whose calendar date is already in the past is treated as PAST.
 */
@Suppress("TooManyFunctions")
object BranchDayService {
    private val logger = KotlinLogging.logger {}

    private const val DAY_BOUNDARY_HOUR = 4
    private const val ONE_DAY = 1L

    val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    fun resolveOrCreate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay = BranchDayRepository.resolveOrCreate(branchId, date)

    /**
     * Resolves the branch day for today (Asia/Manila), creating it idempotently if missing.
     * Consumers (inventory mutations, finance/expenses) need today's [BranchDay.id] before the
     * first write of the day — a 404 on a missing day would deadlock them — so this is a
     * resolve-or-create, not a find-only read.
     *
     * @throws NotFoundException if the branch does not exist.
     */
    fun getToday(branchId: UUID): BranchDay {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        val today = LocalDate.now(manilaZone)
        return resolveOrCreate(branchId, today)
    }

    /**
     * Finds a branch day by ID or throws [NotFoundException].
     */
    fun requireBranchDayExists(branchDayId: UUID): BranchDay =
        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundException("Branch day not found")

    /**
     * Branch-scoped variant of [requireBranchDayExists] (the parent-child convention): the
     * day must belong to [branchId], else 404 — a foreign day is indistinguishable from a
     * missing one. Used by the #157 session-create gated-day guard.
     */
    fun requireBranchDayForBranch(
        branchDayId: UUID,
        branchId: UUID,
    ): BranchDay =
        BranchDayRepository.findByIdForBranch(branchDayId, branchId)
            ?: throw NotFoundException("Branch day not found for this branch")

    /**
     * Finds today's (Asia/Manila) branch day for [branchId] without creating it — the
     * find-only mirror of [getToday]. Gates use this when a day-scoped check must not
     * mutate: a missing day means no BRANCH_DAY grant can exist for it (a grant always
     * references an existing day row).
     */
    fun findToday(branchId: UUID): BranchDay? {
        val today = LocalDate.now(manilaZone)
        return BranchDayRepository.findByBranchAndDate(branchId, today)
    }

    /**
     * Find-only branch-day lookup by explicit date — never creates. The #158 read-side
     * day gates use this to resolve the day a single-day summary read refers to.
     */
    fun findByBranchAndDate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay? = BranchDayRepository.findByBranchAndDate(branchId, date)

    /**
     * Returns the effective status of a branch day, applying lazy evaluation:
     * an OPEN day whose calendar date is in the past is treated as PAST.
     * Returns null if the branch day does not exist.
     */
    fun getEffectiveStatus(branchDayId: UUID): DayStatus? {
        val branchDay = BranchDayRepository.findById(branchDayId) ?: return null
        val today = LocalDate.now(manilaZone)
        return evaluateStatus(branchDay.status, branchDay.date, today)
    }

    /**
     * Resolves the branch day and asserts it is editable by [callerId].
     * Covers the branch-day lookup, [EDIT_PAST_DAY] capability check, and day-state validation.
     *
     * @return the resolved [BranchDay] so callers can use it without a second lookup.
     * @throws NotFoundException if the branch day does not exist.
     * @throws ForbiddenException if the day is PAST/REMITTED and the user lacks EDIT_PAST_DAY.
     * @throws ValidationException if the day is REMITTED and no reason was supplied.
     */
    fun checkBranchDayEditable(
        callerId: UUID,
        branchDayId: UUID,
        reason: String? = null,
    ): Pair<BranchDay, Boolean> {
        val branchDay = requireBranchDayExists(branchDayId)
        val today = LocalDate.now(manilaZone)
        val effectiveStatus = evaluateStatus(branchDay.status, branchDay.date, today)
        val isRemitted = effectiveStatus == DayStatus.REMITTED
        val hasEditPastDay = hasEditPastDayCapability(callerId, branchDay.branchId)
        assertEditableState(effectiveStatus, hasEditPastDay, reason)
        logger.info {
            "[CHECK-BRANCH-DAY-EDITABLE] branch_day=$branchDayId" +
                " effectiveStatus=$effectiveStatus allowed isRemitted=$isRemitted"
        }
        return branchDay to isRemitted
    }

    /**
     * Read-path variant of [checkBranchDayEditable]: asserts the caller may view data on a
     * non-OPEN day. Reads on PAST/REMITTED days require [CapabilityCodes.EDIT_PAST_DAY]
     * (mirroring the write gate) but never a reason — a read mutates nothing.
     *
     * @return the resolved [BranchDay].
     * @throws NotFoundException if the branch day does not exist.
     * @throws ForbiddenException if the day is PAST/REMITTED and the user lacks EDIT_PAST_DAY.
     */
    fun checkBranchDayReadable(
        callerId: UUID,
        branchDayId: UUID,
    ): BranchDay {
        val branchDay = requireBranchDayExists(branchDayId)
        val today = LocalDate.now(manilaZone)
        val effectiveStatus = evaluateStatus(branchDay.status, branchDay.date, today)
        assertReadableState(effectiveStatus, hasEditPastDayCapability(callerId, branchDay.branchId))
        logger.info { "[CHECK-BRANCH-DAY-READABLE] branch_day=$branchDayId effectiveStatus=$effectiveStatus allowed" }
        return branchDay
    }

    private fun hasEditPastDayCapability(
        callerId: UUID,
        branchId: UUID,
    ): Boolean =
        CapabilityService.hasCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
        )

    /**
     * Pure day-state resolution: an OPEN day whose calendar date precedes [today] is treated as
     * PAST. REMITTED and explicitly-PAST days are returned unchanged.
     */
    fun evaluateStatus(
        status: DayStatus,
        date: LocalDate,
        today: LocalDate,
    ): DayStatus =
        if (status == DayStatus.OPEN && date.isBefore(today)) {
            DayStatus.PAST
        } else {
            status
        }

    /**
     * Pure authorization/validation for a write against an already-resolved [effectiveStatus].
     * Extracted so it can be unit-tested without a database.
     */
    fun assertEditableState(
        effectiveStatus: DayStatus,
        hasEditPastDay: Boolean,
        reason: String?,
    ) {
        if (effectiveStatus == DayStatus.OPEN) {
            return
        }
        if (!hasEditPastDay) {
            throw ForbiddenException("EDIT_PAST_DAY capability required to write on a $effectiveStatus day")
        }
        if (effectiveStatus == DayStatus.REMITTED && reason.isNullOrBlank()) {
            throw ValidationException("A reason is required to write on a REMITTED day")
        }
    }

    /**
     * Pure authorization for a read against an already-resolved [effectiveStatus].
     * Reads on PAST/REMITTED days require [CapabilityCodes.EDIT_PAST_DAY]; no reason is ever
     * required — a read mutates nothing.
     */
    fun assertReadableState(
        effectiveStatus: DayStatus,
        hasEditPastDay: Boolean,
    ) {
        if (effectiveStatus == DayStatus.OPEN || hasEditPastDay) {
            return
        }
        throw ForbiddenException("EDIT_PAST_DAY capability required to read on a $effectiveStatus day")
    }

    /**
     * Converts a branch calendar [branchDate] to its UTC expiration instant at the 4 AM Manila
     * boundary of the following day. Used for relief-access capability `valid_to` timestamps.
     */
    fun expirationUtc(branchDate: LocalDate): OffsetDateTime =
        branchDate
            .plusDays(ONE_DAY)
            .atTime(LocalTime.of(DAY_BOUNDARY_HOUR, 0))
            .atZone(manilaZone)
            .toOffsetDateTime()
            .withOffsetSameInstant(ZoneOffset.UTC)
}
