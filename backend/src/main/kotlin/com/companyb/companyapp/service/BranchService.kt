package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.BranchCreateResult
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object BranchService {
    private val logger = KotlinLogging.logger {}
    private const val MANAGE_USERS = "MANAGE_USERS"

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
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_USERS capability required to create branches")
        }
        return BranchRepository.create(id, cleanName, branchType, callerId)
    }

    fun findAll(callerId: UUID): List<Branch> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-BRANCHES] User $callerId lacks $MANAGE_USERS capability" }
            throw ForbiddenResponse("MANAGE_USERS capability required to list branches")
        }
        return BranchRepository.findAll()
    }

    fun findById(
        callerId: UUID,
        branchId: UUID,
    ): Branch {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-BRANCH] User $callerId lacks $MANAGE_USERS capability" }
            throw ForbiddenResponse("MANAGE_USERS capability required to view branches")
        }
        return BranchRepository.findById(branchId) ?: throw NotFoundResponse("Branch not found")
    }
}
