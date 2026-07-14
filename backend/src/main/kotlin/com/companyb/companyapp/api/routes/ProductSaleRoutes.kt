package com.companyb.companyapp.api.routes

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
        config.routes.post("/api/product-sales") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateProductSaleRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid sale id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }
            val sessionId =
                request.sessionId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid session id") }
                }
            val clientId =
                request.clientId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid client id") }
                }
            val productId =
                runCatching { UUID.fromString(request.productId) }
                    .getOrElse { throw BadRequestResponse("Invalid product id") }

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
