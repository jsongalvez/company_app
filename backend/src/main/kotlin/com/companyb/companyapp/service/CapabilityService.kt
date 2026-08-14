package com.companyb.companyapp.service

import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import java.util.UUID

/**
 * Central authorization check. Every mutating service-layer method must call
 * [hasCapability] before performing work. This is the only sanctioned path for
 * permission checks — never inspect roles directly in business logic.
 */
object CapabilityService {
    /**
     * Sentinel context id for GLOBAL-scoped capabilities (e.g. MANAGE_USERS, ASSIGN_DELEGATE).
     * GLOBAL grants have no specific branch/day, so they are stored and checked against the
     * all-zero (nil) UUID. Use this whenever [hasCapability] is called with
     * [CapabilityContextType.GLOBAL].
     */
    val GLOBAL_CONTEXT_ID: UUID = UUID(0L, 0L)

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = CapabilityRepository.hasCapability(userId, capabilityCode, contextType, contextId)

    fun requireCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
        message: String = "$capabilityCode capability required",
    ) {
        if (!hasCapability(userId, capabilityCode, contextType, contextId)) {
            throw ForbiddenException(message)
        }
    }

    fun getCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        CapabilityRepository.findCapabilitiesForUser(userId)

    /** Distinct branch ids where [userId] holds any active grant (audit read window). */
    fun findBranchWindow(userId: UUID): List<UUID> = CapabilityRepository.findBranchWindow(userId)

    /**
     * Day-scoped gate (#157): true when [userId] holds [capabilityCode] at [branchId]
     * (BRANCH) or at [branchDayId] (BRANCH_DAY — a relief grant for that day).
     * GLOBAL grants do NOT satisfy this check (the #131 strictness).
     */
    fun hasCapabilityForBranchDay(
        userId: UUID,
        capabilityCode: String,
        branchId: UUID,
        branchDayId: UUID,
    ): Boolean = CapabilityRepository.hasCapabilityForBranchDay(userId, capabilityCode, branchId, branchDayId)

    fun requireCapabilityForBranchDay(
        userId: UUID,
        capabilityCode: String,
        branchId: UUID,
        branchDayId: UUID,
        message: String = "$capabilityCode capability required for this branch or day",
    ) {
        if (!hasCapabilityForBranchDay(userId, capabilityCode, branchId, branchDayId)) {
            throw ForbiddenException(message)
        }
    }

    /** True when [userId] holds [capabilityCode] at any context (audit branchless policy). */
    fun hasCapabilityAnyContext(
        userId: UUID,
        capabilityCode: String,
    ): Boolean = CapabilityRepository.hasCapabilityAnyContext(userId, capabilityCode)
}
