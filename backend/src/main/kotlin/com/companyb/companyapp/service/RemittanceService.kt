package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.RemittanceDayBreakdownRepository
import com.companyb.companyapp.repository.RemittanceLineRepository
import com.companyb.companyapp.repository.RemittanceRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
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

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList", "MaxLineLength")
    fun addLine(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        amount: BigDecimal,
    ): RemittanceLine {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = SUBMIT_REMITTANCE,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[ADD-REMITTANCE-LINE] User $callerId lacks $SUBMIT_REMITTANCE capability" }
            throw ForbiddenResponse("SUBMIT_REMITTANCE capability required")
        }

        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw BadRequestResponse("Can only add lines to DRAFT remittances")
        }

        when (type) {
            RemittanceLineType.SESSION -> {
                if (sessionId == null) throw BadRequestResponse("sessionId is required for SESSION line type")
                if (productSaleId != null) throw BadRequestResponse("productSaleId must be null for SESSION line type")
            }

            RemittanceLineType.PRODUCT_SALE -> {
                if (productSaleId ==
                    null
                ) {
                    throw BadRequestResponse("productSaleId is required for PRODUCT_SALE line type")
                }
                if (sessionId != null) throw BadRequestResponse("sessionId must be null for PRODUCT_SALE line type")
            }
        }

        val line =
            try {
                RemittanceLineRepository.addLine(
                    id = id,
                    remittanceId = remittanceId,
                    type = type,
                    sessionId = sessionId,
                    productSaleId = productSaleId,
                    amount = amount,
                    createdBy = callerId,
                    expectedVersion = remittance.version,
                )
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictResponse("Remittance version mismatch")
                }
                throw e
            }

        logger.info { "[ADD-REMITTANCE-LINE] Line ${line.id} added to remittance $remittanceId" }
        return line
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun removeLine(
        callerId: UUID,
        remittanceId: UUID,
        lineId: UUID,
    ): RemittanceLine {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = SUBMIT_REMITTANCE,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[DELETE-REMITTANCE-LINE] User $callerId lacks $SUBMIT_REMITTANCE capability" }
            throw ForbiddenResponse("SUBMIT_REMITTANCE capability required")
        }

        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw BadRequestResponse("Can only delete lines from DRAFT remittances")
        }

        val line =
            try {
                RemittanceLineRepository.softDeleteLine(lineId, remittanceId, callerId, remittance.version)
                    ?: throw NotFoundResponse("Remittance line not found")
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictResponse("Remittance version mismatch")
                }
                throw e
            }

        logger.info { "[DELETE-REMITTANCE-LINE] Line $lineId deleted from remittance $remittanceId" }
        return line
    }

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun addDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        branchDayId: UUID,
    ): RemittanceDayBreakdown {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = SUBMIT_REMITTANCE,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[ADD-REMITTANCE-BREAKDOWN] User $callerId lacks $SUBMIT_REMITTANCE capability" }
            throw ForbiddenResponse("SUBMIT_REMITTANCE capability required")
        }

        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw BadRequestResponse("Can only add day breakdowns to DRAFT remittances")
        }

        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundResponse("Branch day not found")

        val breakdown =
            RemittanceDayBreakdownRepository.addDayBreakdown(
                id = id,
                remittanceId = remittanceId,
                branchDayId = branchDayId,
                createdBy = callerId,
            )

        logger.info { "[ADD-REMITTANCE-BREAKDOWN] Day breakdown ${breakdown.id} added to remittance $remittanceId" }
        return breakdown
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun getRemittance(
        callerId: UUID,
        remittanceId: UUID,
    ): RemittanceDetail {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = SUBMIT_REMITTANCE,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[GET-REMITTANCE] User $callerId lacks $SUBMIT_REMITTANCE capability" }
            throw ForbiddenResponse("SUBMIT_REMITTANCE capability required")
        }

        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")

        val lines = RemittanceLineRepository.findByRemittanceId(remittanceId)
        val totalAmount = RemittanceLineRepository.sumAmountsByRemittanceId(remittanceId)
        val dayBreakdowns = RemittanceDayBreakdownRepository.findByRemittanceId(remittanceId)

        return RemittanceDetail(
            remittance = remittance,
            lines = lines,
            totalAmount = totalAmount,
            dayBreakdowns = dayBreakdowns,
        )
    }
}

data class RemittanceDetail(
    val remittance: Remittance,
    val lines: List<RemittanceLine>,
    val totalAmount: BigDecimal,
    val dayBreakdowns: List<RemittanceDayBreakdown>,
)
