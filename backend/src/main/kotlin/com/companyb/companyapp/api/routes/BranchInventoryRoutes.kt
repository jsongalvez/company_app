package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.service.BranchInventoryService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object BranchInventoryRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val PRODUCT_ID_PARAM = "productId"

    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/inventory") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val request = context.bodyAsClass<AddInventoryCardRequest>()
            val productId =
                runCatching { UUID.fromString(request.productId) }
                    .getOrElse { throw BadRequestResponse("Invalid product id") }

            BranchInventoryService.ensureCard(
                callerId = callerId,
                branchId = branchId,
                productId = productId,
            )

            context.status(HttpStatus.CREATED)
        }

        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/inventory/{$PRODUCT_ID_PARAM}/restock") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val productId =
                runCatching { UUID.fromString(context.pathParam(PRODUCT_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid product id") }
            val request = context.bodyAsClass<RestockRequest>()
            val movementId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid movement id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }

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

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/inventory") { context ->
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }

            context.json(
                BranchInventoryService.findByBranch(branchId).map { it.toResponse() },
            )
        }
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
            notes = null,
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
