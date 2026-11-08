package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.RightsResponse
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.observability.traced
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Reads the grants a person holds. Permission checks do not start here: they go through
 * [Access], which decides which grants count for each [Act].
 */
object CapabilityService {
    /**
     * Sentinel context id for GLOBAL-scoped capabilities (e.g. MANAGE_USERS, ASSIGN_DELEGATE).
     * GLOBAL grants have no specific branch/day, so they are stored and checked against the
     * all-zero (nil) UUID.
     */
    val GLOBAL_CONTEXT_ID: UUID = UUID(0L, 0L)

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = CapabilityRepository.hasCapability(userId, capabilityCode, contextType, contextId)

    /** Every act [userId] may do, at each place they stand at. */
    fun rightsOf(userId: UUID): RightsResponse = Rights.of(CapabilityRepository.grantsOf(userId))

    fun getCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        CapabilityRepository.findCapabilitiesForUser(userId)

    /** Distinct branch ids where [userId] holds any active grant (audit read window). */
    fun findBranchWindow(userId: UUID): List<UUID> = CapabilityRepository.findBranchWindow(userId)

    fun findDayWindow(userId: UUID): List<UUID> = CapabilityRepository.findDayWindow(userId)

    /** True when [userId] holds [capabilityCode] at any context. */
    fun hasCapabilityAnyContext(
        userId: UUID,
        capabilityCode: String,
    ): Boolean = CapabilityRepository.hasCapabilityAnyContext(userId, capabilityCode)

    /** Active people whose relief for [branchDayId] is in force. */
    fun reliefHolders(branchDayId: UUID): Set<UUID> =
        traced("CapabilityService.reliefHolders") {
            transaction { CapabilityRepository.reliefHoldersInTransaction(branchDayId) }
        }

    /** True when [userId]'s relief for [branchDayId] is in force. Runs on the caller's transaction. */
    fun holdsReliefInTransaction(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean = userId in CapabilityRepository.reliefHoldersInTransaction(branchDayId, userId)

    /** Per branch in [branchIds], who assigned there gets next-appointment alerts. On the caller's transaction. */
    fun alertedByBranchInTransaction(branchIds: Collection<UUID>): Map<UUID, List<UUID>> =
        CapabilityRepository.alertedByBranchInTransaction(branchIds)
}
