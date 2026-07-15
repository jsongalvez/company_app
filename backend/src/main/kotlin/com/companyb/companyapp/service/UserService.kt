package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by both persisting
 * INACTIVE status (so future tokens fail DB authorization) and adding the user to the
 * in-memory [DenyList] (so already-issued tokens are rejected before any DB lookup).
 */
object UserService {
    private val logger = KotlinLogging.logger {}

    /**
     * @throws ForbiddenResponse if [callerId] lacks MANAGE_USERS on the GLOBAL context.
     * @throws NotFoundResponse if [targetUserId] does not exist.
     */
    fun deactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_USERS capability required to deactivate users",
        )
        val updated = UserRepository.deactivate(targetUserId, callerId)
        if (!updated) {
            throw NotFoundResponse("User not found")
        }
        DenyList.deny(targetUserId)
        logger.info { "[DEACTIVATE] User deactivated and added to deny list" }
    }
}
