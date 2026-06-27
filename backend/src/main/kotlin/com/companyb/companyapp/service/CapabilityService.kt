package com.companyb.companyapp.service

import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import java.util.UUID

/**
 * Central authorization check. Every mutating service-layer method must call
 * [hasCapability] before performing work. This is the only sanctioned path for
 * permission checks — never inspect roles directly in business logic.
 */
object CapabilityService {
    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = CapabilityRepository.hasCapability(userId, capabilityCode, contextType, contextId)
}
