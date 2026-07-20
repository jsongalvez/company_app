package com.companyb.companyapp.service.attendance

import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import java.util.UUID

internal object AssignmentResolver {
    fun resolveIsRelief(
        branchId: UUID,
        userId: UUID,
    ): Boolean {
        val existingAssignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
        return existingAssignment == null
    }

    fun getIsRelief(
        branchDayId: UUID,
        userId: UUID,
    ): Boolean = AttendanceRepository.branchDayAssignmentIsRelief(branchDayId, userId) ?: false
}
