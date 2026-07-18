package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchCreateResult
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.CreateBranch
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object BranchService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
        branchType: BranchType,
    ): BranchCreateResult =
        BranchRepository.create(
            CreateBranch(
                id = id,
                name = name,
                branchType = branchType,
                changedBy = callerId,
            ),
        )

    fun findAll(): List<Branch> = BranchRepository.findAll()

    fun findById(branchId: UUID): Branch =
        BranchRepository.findById(branchId) ?: throw NotFoundException("Branch not found")
}
