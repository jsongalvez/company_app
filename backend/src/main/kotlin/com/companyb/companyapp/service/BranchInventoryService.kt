package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.InventoryMovement
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object BranchInventoryService {
    private val logger = KotlinLogging.logger {}

    private const val MANAGE_PRODUCTS = "MANAGE_PRODUCTS"

    @Suppress("ThrowsCount")
    fun ensureCard(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
    ) {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to manage inventory")
        }

        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundResponse("Branch not found")
        }

        if (ProductRepository.findById(productId) == null) {
            throw NotFoundResponse("Product not found")
        }

        BranchInventoryRepository.ensureCard(branchId, productId)
        logger.info { "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId" }
    }

    @Suppress("ThrowsCount", "LongParameterList")
    fun restock(
        callerId: UUID,
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        quantity: Int,
        branchDayId: UUID,
    ): InventoryMovement {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to manage inventory")
        }

        if (quantity <= 0) {
            throw BadRequestResponse("Restock quantity must be positive")
        }

        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundResponse("Branch not found")
        }

        if (ProductRepository.findById(productId) == null) {
            throw NotFoundResponse("Product not found")
        }

        BranchDayService.assertEditable(branchDayId, callerId)

        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        val expectedVersion = card.version

        val result =
            try {
                BranchInventoryRepository.restock(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    quantity = quantity,
                    branchDayId = branchDayId,
                    expectedVersion = expectedVersion,
                    movedBy = callerId,
                )
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictResponse("Inventory version mismatch")
                }
                throw e
            }

        return result.movement
    }

    fun findByBranch(branchId: UUID): List<BranchInventoryWithProduct> {
        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundResponse("Branch not found")
        }
        return BranchInventoryRepository.findByBranch(branchId)
    }
}
