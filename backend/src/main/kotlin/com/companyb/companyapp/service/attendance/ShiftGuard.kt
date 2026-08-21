package com.companyb.companyapp.service.attendance

import com.companyb.companyapp.exception.ConflictException
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
}
