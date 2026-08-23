package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

private val NEGATIVE_QUANTITY_REASONS =
    setOf(InventoryMovementReason.TESTER, InventoryMovementReason.SAMPLE, InventoryMovementReason.MISSING)

private val ALLOWED_MOVEMENT_REASONS =
    NEGATIVE_QUANTITY_REASONS + InventoryMovementReason.ADJUSTMENT

@OpenApi(
    path = "/api/branches/{branchId}/inventory",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_inventory_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/inventory",
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_inventory_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/inventory/low-stock",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "inventory_low_stock",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/inventory/movements",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "inventory_movements",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/inventory/{productId}/restock",
    methods = [HttpMethod.POST],
    pathParams = [
        OpenApiParam(
            name = "branchId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "productId", type = UUID::class, required = true),
    ],
    operationId = "inventory_restock",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/inventory/{productId}/movement",
    methods = [HttpMethod.POST],
    pathParams = [
        OpenApiParam(
            name = "branchId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "productId", type = UUID::class, required = true),
    ],
    operationId = "inventory_movement",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object BranchInventoryRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val PRODUCT_ID_PARAM = "productId"

    @Suppress("LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/inventory") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val required =
                if (context.method() == HandlerType.POST) {
                    CapabilityCodes.MANAGE_PRODUCTS
                } else {
                    CapabilityCodes.EDIT_BRANCH_DATA
                }
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                required,
            )
        }

        config.routes.before("/api/branches/{branchId}/inventory/low-stock") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/branches/{branchId}/inventory/movements") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/branches/{branchId}/inventory/{productId}/restock") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.MANAGE_PRODUCTS,
            )
        }

        config.routes.before("/api/branches/{branchId}/inventory/{productId}/movement") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val request = context.bodyAsClass<InventoryMovementRequest>()
            val reason = validateMovementReason(request.reason)
            val required =
                if (reason == InventoryMovementReason.ADJUSTMENT) {
                    CapabilityCodes.MANAGE_PRODUCTS
                } else {
                    CapabilityCodes.EDIT_BRANCH_DATA
                }
            // Branch-scoped only (#157 decision): the movement's day comes from the body while
            // the route is branch-scoped via the path — a day-grant check on the body's day
            // would authorize a write against a DIFFERENT branch (the parent-child scoping
            // trap). Inventory is not relief-eligible; see the #157 resolution.
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                required,
            )
        }

        config.routes.post(ApiRoutes.BRANCH_INVENTORY_PATH, ::handleEnsureCard)
        config.routes.post(ApiRoutes.BRANCH_INVENTORY_RESTOCK_PATH, ::handleRestock)
        config.routes.get(ApiRoutes.BRANCH_INVENTORY_PATH, ::handleGetInventory)
        config.routes.get(ApiRoutes.BRANCH_INVENTORY_LOW_STOCK_PATH, ::handleGetLowStock)
        config.routes.get(ApiRoutes.BRANCH_INVENTORY_MOVEMENTS_PATH, ::handleGetMovements)
        config.routes.post(
            ApiRoutes.BRANCH_INVENTORY_MOVEMENT_PATH,
            ::handleRecordMovement,
        )
    }

    private fun handleEnsureCard(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val request = context.bodyAsClass<AddInventoryCardRequest>()
        val productId = uuidOrThrow(request.productId, "product id")

        InventoryService.ensureCard(
            callerId = callerId,
            branchId = branchId,
            productId = productId,
        )

        context.status(HttpStatus.CREATED)
    }

    private fun handleRestock(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
        val request = context.bodyAsClass<RestockRequest>()
        val movementId = uuidOrThrow(request.id, "movement id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
        if (request.quantity <= 0) throw BadRequestResponse("Restock quantity must be positive")

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = request.quantity,
                notes = null,
                branchDayId = branchDayId,
                reason = request.editReason,
            )

        context.status(HttpStatus.CREATED)
        context.json(movement.toResponse())
    }

    private fun handleGetInventory(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

        context.json(
            InventoryService.getStock(branchId).map { it.toResponse() },
        )
    }

    private fun handleGetLowStock(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val thresholdOverride = context.queryParam("threshold")?.toIntOrNull()

        context.json(
            InventoryService.getLowStockAlerts(branchId, thresholdOverride).map { it.toResponse() },
        )
    }

    private fun handleGetMovements(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val dateParam = context.queryParam("date")
        val date =
            dateParam?.let {
                runCatching { LocalDate.parse(it) }
                    .getOrElse { throw BadRequestResponse("Invalid date format (expected yyyy-MM-dd)") }
            }

        context.json(
            InventoryService.getMovementHistory(branchId, date).map { it.toResponse() },
        )
    }

    private fun validateMovementReason(reason: InventoryMovementReason): InventoryMovementReason {
        if (reason !in ALLOWED_MOVEMENT_REASONS) {
            throw BadRequestResponse("Invalid movement reason for this endpoint")
        }
        return reason
    }

    @Suppress("ThrowsCount")
    private fun handleRecordMovement(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
        val request = context.bodyAsClass<InventoryMovementRequest>()
        val movementId = uuidOrThrow(request.movementId, "movement id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
        val reason = validateMovementReason(request.reason)

        if (reason in NEGATIVE_QUANTITY_REASONS && request.quantityChange >= 0) {
            throw BadRequestResponse("$reason movement must have a negative quantity change")
        }
        if (reason == InventoryMovementReason.MISSING && request.notes.isNullOrBlank()) {
            throw BadRequestResponse("Notes are required for MISSING movements")
        }

        val movementType =
            when (reason) {
                InventoryMovementReason.TESTER -> MovementType.Tester

                InventoryMovementReason.SAMPLE -> MovementType.Sample

                InventoryMovementReason.MISSING -> MovementType.Missing

                InventoryMovementReason.ADJUSTMENT -> MovementType.Adjustment

                InventoryMovementReason.RESTOCK,
                InventoryMovementReason.SALE,
                -> throw BadRequestResponse("Invalid movement reason for this endpoint")
            }

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = movementType,
                quantityChange = request.quantityChange,
                notes = request.notes,
                branchDayId = branchDayId,
                reason = request.editReason,
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
            reason = reason,
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
            unitPrice = unitPrice.toPlainString(),
            commissionAmount = commissionAmount.toPlainString(),
        )
}
