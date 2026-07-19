package com.companyb.companyapp.service.finance.commission

import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.service.branchday.BranchDayService
import java.util.UUID

object CommissionService {
    fun getByBranchDayId(branchDayId: UUID): List<CommissionSplit> {
        BranchDayService.requireBranchDayExists(branchDayId)
        return CommissionSplitRepository.findByBranchDayId(branchDayId)
    }

    @Suppress("LongParameterList")
    fun createManualInclusion(
        callerId: UUID,
        id: UUID,
        productSaleId: UUID,
        userId: UUID,
        isIncluded: Boolean,
        reason: String?,
    ): CommissionManualInclusion =
        ManualInclusionHelper.create(
            callerId = callerId,
            id = id,
            productSaleId = productSaleId,
            userId = userId,
            isIncluded = isIncluded,
            reason = reason,
        )

    fun manualRecalculate(branchDayId: UUID) {
        CommissionEngine.manualRecalculate(branchDayId)
    }
}
