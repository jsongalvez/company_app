package com.companyb.companyapp.workforce

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import java.util.UUID

internal object ShiftGuard {
    fun ensureNoActiveClockIn(
        userId: UUID,
        branchDayId: UUID,
    ) {
        val activeClockIn = AttendanceRepository.hasActiveClockInInTransaction(userId, branchDayId)
        if (activeClockIn) {
            throw ConflictException("User already has an active clock-in for this branch day")
        }
    }

    /**
     * #376 — the cutoff protocol shared by every duty-retraction command (relief-request
     * cancel/withdraw #357, invite revoke #374): serialize against clock-in on the
     * branch_day row BEFORE reading attendance. The plain absence check alone is a TOCTOU
     * under READ COMMITTED — a concurrent clock-in commits independently and the
     * retraction would land after the duty started (#363 ruling 3). Clock-in takes the
     * same row lock up front ([BranchDayService.lockDayInTransaction]), so the two sides
     * serialize: whichever commits first wins, and a retraction can never decide on a
     * shift that has begun.
     */
    fun ensureRetractionAllowed(
        userId: UUID,
        branchDayId: UUID,
        message: String,
    ) {
        BranchDayService.lockDayInTransaction(branchDayId)
        if (AttendanceRepository.hasActiveClockInInTransaction(userId, branchDayId)) {
            throw ValidationException(message)
        }
    }
}
