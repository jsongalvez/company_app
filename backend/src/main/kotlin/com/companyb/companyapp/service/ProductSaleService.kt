package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ProductSale
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object ProductSaleService {
    private val logger = KotlinLogging.logger {}

    private const val EDIT_BRANCH_DATA = "EDIT_BRANCH_DATA"
    private const val MINIMUM_QUANTITY = 1

    @Suppress("ReturnCount", "ThrowsCount", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    fun sell(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
        productId: UUID,
        quantity: Int,
        expectedVersion: Int,
    ): ProductSale {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required to record product sales")
        }

        BranchDayService.assertEditable(branchDayId, callerId)

        val branchDay =
            BranchDayRepository.findById(branchDayId)
                ?: throw NotFoundResponse("Branch day not found")

        if (BranchRepository.findById(branchDay.branchId) == null) {
            throw NotFoundResponse("Branch not found")
        }

        val product =
            ProductRepository.findById(productId)
                ?: throw NotFoundResponse("Product not found")

        if (!product.isActive) {
            throw BadRequestResponse("Product is not active")
        }

        if (quantity < MINIMUM_QUANTITY) {
            throw BadRequestResponse("Quantity must be at least 1")
        }

        if (sessionId != null) {
            if (clientId != null) {
                throw BadRequestResponse("Session-linked sale must not have a clientId")
            }
            if (isWalkIn) {
                throw BadRequestResponse("Session-linked sale must not be a walk-in")
            }
            if (SessionRepository.findById(sessionId) == null) {
                throw NotFoundResponse("Session not found")
            }
        }

        if (sessionId == null && clientId != null && !isWalkIn) {
            throw BadRequestResponse("Walk-in sale with known client must set isWalkIn=true")
        }

        if (sessionId == null && clientId == null && !isWalkIn) {
            throw BadRequestResponse("Anonymous sale must set isWalkIn=true")
        }

        val result =
            try {
                ProductSaleRepository.sell(
                    id = id,
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = clientId,
                    isWalkIn = isWalkIn,
                    productId = productId,
                    branchId = branchDay.branchId,
                    quantity = quantity,
                    expectedVersion = expectedVersion,
                    handledBy = callerId,
                    product = product,
                )
            } catch (e: IllegalStateException) {
                when (e.message) {
                    "version_mismatch" -> throw ConflictResponse("Inventory version mismatch")
                    "insufficient_stock" -> throw BadRequestResponse("Insufficient stock")
                    else -> throw e
                }
            }

        CommissionEngineService.recalculate(branchDayId)

        return result
    }
}
