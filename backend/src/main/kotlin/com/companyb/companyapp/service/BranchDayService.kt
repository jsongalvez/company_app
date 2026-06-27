package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.DayStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Shared day-state service. Every operational/financial write must call [assertEditable]
 * before mutating, and create the owning day via [resolveOrCreate].
 *
 * Day state is lazily evaluated against the current Asia/Manila calendar date: a day that is
 * still OPEN but whose calendar date is already in the past is treated as PAST.
 */
object BranchDayService {
    private val logger = KotlinLogging.logger {}

    private const val EDIT_PAST_DAY = "EDIT_PAST_DAY"
    private const val DAY_BOUNDARY_HOUR = 4
    private const val ONE_DAY = 1L

    val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    fun resolveOrCreate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay = BranchDayRepository.resolveOrCreate(branchId, date)

    /**
     * Asserts the given branch day can be written to by [userId].
     *
     * @param reason mandatory free-text reason when writing to a REMITTED day.
     * @throws NotFoundResponse if the branch day does not exist.
     * @throws ForbiddenResponse if the day is PAST/REMITTED and the user lacks EDIT_PAST_DAY.
     * @throws BadRequestResponse if the day is REMITTED and no reason was supplied.
     */
    fun assertEditable(
        branchDayId: UUID,
        userId: UUID,
        reason: String? = null,
    ) {
        val branchDay = BranchDayRepository.findById(branchDayId) ?: throw NotFoundResponse("Branch day not found")
        val today = LocalDate.now(manilaZone)
        val effectiveStatus = evaluateStatus(branchDay.status, branchDay.date, today)
        val hasEditPastDay =
            CapabilityService.hasCapability(
                userId = userId,
                capabilityCode = EDIT_PAST_DAY,
                contextType = CapabilityContextType.BRANCH,
                contextId = branchDay.branchId,
            )
        assertEditableState(effectiveStatus, hasEditPastDay, reason)
        logger.info { "[ASSERT-EDITABLE] branch_day=$branchDayId effectiveStatus=$effectiveStatus allowed" }
    }

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
            throw ForbiddenResponse("EDIT_PAST_DAY capability required to write on a $effectiveStatus day")
        }
        if (effectiveStatus == DayStatus.REMITTED && reason.isNullOrBlank()) {
            throw BadRequestResponse("A reason is required to write on a REMITTED day")
        }
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
