package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.RemittanceRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.time.LocalDate
import java.util.UUID

object RemittanceService {
    private val logger = KotlinLogging.logger {}

    private const val SUBMIT_REMITTANCE = "SUBMIT_REMITTANCE"

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun createDraft(
        callerId: UUID,
        id: UUID,
        type: RemittanceType,
        branchId: UUID,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
    ): Remittance {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = SUBMIT_REMITTANCE,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[CREATE-REMITTANCE-DRAFT] User $callerId lacks $SUBMIT_REMITTANCE capability" }
            throw ForbiddenResponse("SUBMIT_REMITTANCE capability required")
        }

        if (dateRangeEnd.isBefore(dateRangeStart)) {
            throw BadRequestResponse("dateRangeEnd must not be before dateRangeStart")
        }

        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val today = LocalDate.now(BranchDayService.manilaZone)
        val result =
            RemittanceRepository.createDraft(
                id = id,
                type = type,
                branchId = branchId,
                method = method,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
                submittedDate = today,
                submittedBy = callerId,
            )
        logger.info { "[CREATE-REMITTANCE-DRAFT] Remittance ${result.remittance.id} created=${result.created}" }
        return result.remittance
    }
}
