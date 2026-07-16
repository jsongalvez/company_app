package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.BranchCreateResult
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.Branch
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object BranchService {
    private val logger = KotlinLogging.logger {}

    @Suppress("UnusedParameter")
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
        return BranchRepository.create(id, cleanName, branchType, callerId)
    }

    @Suppress("UnusedParameter")
    fun findAll(callerId: UUID): List<Branch> = BranchRepository.findAll()

    @Suppress("UnusedParameter")
    fun findById(
        callerId: UUID,
        branchId: UUID,
    ): Branch = BranchRepository.findById(branchId) ?: throw NotFoundResponse("Branch not found")
}
