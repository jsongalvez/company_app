package com.companyb.companyapp.commerce
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.commerce.AddInventoryCardRequest
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.commerce.InventoryMovementRequest
import com.companyb.companyapp.contracts.commerce.InventoryMovementResponse
import com.companyb.companyapp.contracts.commerce.RestockRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

private val NEGATIVE_QUANTITY_REASONS =
    setOf(InventoryMovementReason.TESTER, InventoryMovementReason.SAMPLE, InventoryMovementReason.MISSING)

private val ALLOWED_MOVEMENT_REASONS =
    NEGATIVE_QUANTITY_REASONS + InventoryMovementReason.ADJUSTMENT

@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_inventory_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchInventoryResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_inventory_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddInventoryCardRequest::class)]),
    responses = [
        OpenApiResponse(status = "201"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_LOW_STOCK_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "threshold", type = Int::class, required = false)],
    operationId = "inventory_low_stock",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchInventoryResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_MOVEMENTS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "date", type = String::class, required = false)],
    operationId = "inventory_movements",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<InventoryMovementResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_RESTOCK_PATH,
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
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RestockRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = InventoryMovementResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_INVENTORY_MOVEMENT_PATH,
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
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = InventoryMovementRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = InventoryMovementResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchInventoryRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val PRODUCT_ID_PARAM = "productId"

    fun register(config: JavalinConfig) {
        registerGuards(config)
        registerHandlers(config)
    }

    private fun requireKnownBranchId(context: Context): UUID {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        BranchService.findById(branchId)
        return branchId
    }

    private fun registerGuards(config: JavalinConfig) {
        // #732 — 404 precedence for an unknown branch before the capability gate
        // (#730 SessionBaseRateRoutes precedent; #711/#715/#724 class):
        // requireBranchCapabilityForBranchId alone conflates "unknown branch" with
        // "known but non-member". BranchService.findById throws NotFoundException.
        config.routes.before(ApiRoutes.BRANCH_INVENTORY_PATH) { context ->
            val branchId = requireKnownBranchId(context)
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

        config.routes.before(ApiRoutes.BRANCH_INVENTORY_LOW_STOCK_PATH) { context ->
            val branchId = requireKnownBranchId(context)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_INVENTORY_MOVEMENTS_PATH) { context ->
            val branchId = requireKnownBranchId(context)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_INVENTORY_RESTOCK_PATH) { context ->
            val branchId = requireKnownBranchId(context)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.MANAGE_PRODUCTS,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_INVENTORY_MOVEMENT_PATH) { context ->
            val branchId = requireKnownBranchId(context)
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
    }

    private fun registerHandlers(config: JavalinConfig) {
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
        val view = InventoryService.getInventory(branchId)

        val body: List<BranchInventoryResponse> =
            view.cards.map { it.toResponse(view.breakdowns[it.inventory.productId]) }
        context.json(body)
    }

    private fun handleGetLowStock(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val thresholdOverride = context.queryParam("threshold")?.toIntOrNull()
        val view = InventoryService.getLowStockInventory(branchId, thresholdOverride)

        val body: List<BranchInventoryResponse> =
            view.cards.map { it.toResponse(view.breakdowns[it.inventory.productId]) }
        context.json(body)
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

    private fun handleRecordMovement(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
        val request = context.bodyAsClass<InventoryMovementRequest>()
        val movementId = uuidOrThrow(request.movementId, "movement id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
        val reason = validateMovementReason(request.reason)
        requireMovementPayload(reason, request.quantityChange, request.notes)

        val movementType = resolveMovementType(reason)

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

    private fun requireMovementPayload(
        reason: InventoryMovementReason,
        quantityChange: Int,
        notes: String?,
    ) {
        if (reason in NEGATIVE_QUANTITY_REASONS && quantityChange >= 0) {
            throw BadRequestResponse("$reason movement must have a negative quantity change")
        }
        if (reason == InventoryMovementReason.MISSING && notes.isNullOrBlank()) {
            throw BadRequestResponse("Notes are required for MISSING movements")
        }
    }

    private fun resolveMovementType(reason: InventoryMovementReason): MovementType =
        when (reason) {
            InventoryMovementReason.TESTER -> MovementType.Tester

            InventoryMovementReason.SAMPLE -> MovementType.Sample

            InventoryMovementReason.MISSING -> MovementType.Missing

            InventoryMovementReason.ADJUSTMENT -> MovementType.Adjustment

            InventoryMovementReason.RESTOCK,
            InventoryMovementReason.SALE,
            -> throw BadRequestResponse("Invalid movement reason for this endpoint")
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

    private fun BranchInventoryWithProduct.toResponse(breakdown: InventoryBreakdown? = null): BranchInventoryResponse =
        BranchInventoryResponse(
            id = inventory.id.toString(),
            branchId = inventory.branchId.toString(),
            productId = inventory.productId.toString(),
            productName = productName,
            currentStock = inventory.currentStock,
            version = inventory.version,
            unitPrice = unitPrice.toPlainString(),
            commissionAmount = commissionAmount.toPlainString(),
            available = inventory.currentStock,
            stock = breakdown?.stock ?: 0,
            sales = breakdown?.sales ?: 0,
            testerSample = breakdown?.testerSample ?: 0,
            missing = breakdown?.missing ?: 0,
            adjustment = breakdown?.adjustment ?: 0,
        )
}
