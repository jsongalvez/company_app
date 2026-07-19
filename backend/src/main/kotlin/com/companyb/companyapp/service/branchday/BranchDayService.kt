package com.companyb.companyapp.service.branchday

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
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

object BranchDayService {
    private val logger = KotlinLogging.logger {}

    private const val DAY_BOUNDARY_HOUR = 4
    private const val ONE_DAY = 1L

    val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    fun resolveOrCreate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay = BranchDayRepository.resolveOrCreate(branchId, date)

    fun requireBranchDayExists(branchDayId: UUID): BranchDay =
        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundException("Branch day not found")

    fun getEffectiveStatus(branchDayId: UUID): DayStatus? {
        val branchDay = BranchDayRepository.findById(branchDayId) ?: return null
        val today = LocalDate.now(manilaZone)
        return evaluateStatus(branchDay.status, branchDay.date, today)
    }

    fun checkBranchDayEditable(
        callerId: UUID,
        branchDayId: UUID,
        reason: String? = null,
    ): BranchDay {
        val branchDay = requireBranchDayExists(branchDayId)
        val today = LocalDate.now(manilaZone)
        val effectiveStatus = evaluateStatus(branchDay.status, branchDay.date, today)
        val hasEditPastDay =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
                contextType = CapabilityContextType.BRANCH,
                contextId = branchDay.branchId,
            )
        assertEditableState(effectiveStatus, hasEditPastDay, reason)
        logger.info { "[CHECK-BRANCH-DAY-EDITABLE] branch_day=$branchDayId effectiveStatus=$effectiveStatus allowed" }
        return branchDay
    }

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

    fun expirationUtc(branchDate: LocalDate): OffsetDateTime =
        branchDate
            .plusDays(ONE_DAY)
            .atTime(LocalTime.of(DAY_BOUNDARY_HOUR, 0))
            .atZone(manilaZone)
            .toOffsetDateTime()
            .withOffsetSameInstant(ZoneOffset.UTC)
}
