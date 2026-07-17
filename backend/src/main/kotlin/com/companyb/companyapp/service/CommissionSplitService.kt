package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.model.CommissionSplit
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object CommissionSplitService {
    private val logger = KotlinLogging.logger {}

    fun getByBranchDayId(branchDayId: UUID): List<CommissionSplit> {
        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundException("Branch day not found")

        return CommissionSplitRepository.findByBranchDayId(branchDayId)
    }
}
