package com.companyb.companyapp.service

import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.javalin.http.ForbiddenResponse
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
            throw ForbiddenResponse(message)
        }
    }

    fun getCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        CapabilityRepository.findCapabilitiesForUser(userId)
}
