package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.service.BranchInventoryService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object BranchInventoryRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val PRODUCT_ID_PARAM = "productId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/inventory") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.MANAGE_PRODUCTS,
            )
        }

        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/inventory", ::handleEnsureCard)
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/inventory/{$PRODUCT_ID_PARAM}/restock", ::handleRestock)
        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/inventory", ::handleGetInventory)
        config.routes.post(
            "/api/branches/{$BRANCH_ID_PARAM}/inventory/{$PRODUCT_ID_PARAM}/movement",
            ::handleRecordMovement,
        )
    }

    private fun handleEnsureCard(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val request = context.bodyAsClass<AddInventoryCardRequest>()
        val productId = uuidOrThrow(request.productId, "product id")

        BranchInventoryService.ensureCard(
            branchId = branchId,
            productId = productId,
        )

        context.status(HttpStatus.CREATED)
    }

    private fun handleRestock(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
        val request = context.bodyAsClass<RestockRequest>()
        val movementId = uuidOrThrow(request.id, "movement id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")

        val movement =
            BranchInventoryService.restock(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                quantity = request.quantity,
                branchDayId = branchDayId,
            )

        context.status(HttpStatus.CREATED)
        context.json(movement.toResponse())
    }

    private fun handleGetInventory(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

        context.json(
            BranchInventoryService.findByBranch(branchId).map { it.toResponse() },
        )
    }

    @Suppress("ThrowsCount")
    private fun handleRecordMovement(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
        val request = context.bodyAsClass<InventoryMovementRequest>()
        val movementId = uuidOrThrow(request.movementId, "movement id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
        val reason =
            runCatching { InventoryMovementReason.valueOf(request.reason.uppercase()) }
                .getOrElse { throw BadRequestResponse("Invalid movement reason") }

        val movement =
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                reason = reason,
                quantityChange = request.quantityChange,
                notes = request.notes,
                branchDayId = branchDayId,
            )

        context.status(HttpStatus.CREATED)
        context.json(movement.toResponse())
    }

    private fun InventoryMovement.toResponse(): InventoryMovementResponse =
        InventoryMovementResponse(
            id = id.toString(),
            productId = productId.toString(),
            branchId = branchId.toString(),
            branchDayId = branchDayId.toString(),
            reason = reason.name,
            quantityChange = quantityChange,
            movedBy = movedBy.toString(),
            movedAt = movedAt.toString(),
            notes = notes,
        )

    private fun BranchInventoryWithProduct.toResponse(): BranchInventoryResponse =
        BranchInventoryResponse(
            id = inventory.id.toString(),
            branchId = inventory.branchId.toString(),
            productId = inventory.productId.toString(),
            productName = productName,
            currentStock = inventory.currentStock,
            version = inventory.version,
        )
}
