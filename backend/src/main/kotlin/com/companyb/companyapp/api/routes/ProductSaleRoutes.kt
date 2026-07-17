package com.companyb.companyapp.api.routes

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.service.ProductSaleService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object ProductSaleRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/product-sales") { context ->
            if (context.method() != io.javalin.http.HandlerType.POST) return@before
            val request = context.bodyAsClass<CreateProductSaleRequest>()
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            CapabilityFilter.requireBranchCapability(
                context,
                branchDayId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.post("/api/product-sales") { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateProductSaleRequest>()

            val id = uuidOrThrow(request.id, "sale id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val sessionId = request.sessionId?.let { uuidOrThrow(it, "session id") }
            val clientId = request.clientId?.let { uuidOrThrow(it, "client id") }
            val productId = uuidOrThrow(request.productId, "product id")

            if (request.quantity < 1) throw BadRequestResponse("Quantity must be at least 1")

            val sale =
                ProductSaleService.sell(
                    callerId = callerId,
                    id = id,
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = clientId,
                    isWalkIn = request.isWalkIn,
                    productId = productId,
                    quantity = request.quantity,
                    expectedVersion = request.expectedVersion,
                )

            context.status(HttpStatus.CREATED)
            context.json(sale.toResponse())
        }
    }

    private fun ProductSale.toResponse(): ProductSaleResponse =
        ProductSaleResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            sessionId = sessionId?.toString(),
            clientId = clientId?.toString(),
            isWalkIn = isWalkIn,
            productId = productId.toString(),
            productName = productName,
            handledBy = handledBy.toString(),
            quantity = quantity,
            unitPriceAtTime = unitPriceAtTime.toPlainString(),
            totalAmountAtTime = totalAmountAtTime.toPlainString(),
            commissionAmountAtTime = commissionAmountAtTime.toPlainString(),
            soldAt = soldAt.toString(),
        )
}
