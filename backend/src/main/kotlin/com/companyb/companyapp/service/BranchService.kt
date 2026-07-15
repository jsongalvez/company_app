package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.BranchCreateResult
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object BranchService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
        branchType: BranchType,
    ): BranchCreateResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            throw BadRequestResponse("Branch name is required")
        }
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_USERS capability required to create branches",
        )
        return BranchRepository.create(id, cleanName, branchType, callerId)
    }

    fun findAll(callerId: UUID): List<Branch> {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_USERS capability required to list branches",
        )
        return BranchRepository.findAll()
    }

    fun findById(
        callerId: UUID,
        branchId: UUID,
    ): Branch {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_USERS capability required to view branches",
        )
        return BranchRepository.findById(branchId) ?: throw NotFoundResponse("Branch not found")
    }
}
