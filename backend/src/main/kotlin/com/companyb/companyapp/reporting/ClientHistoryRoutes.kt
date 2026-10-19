package com.companyb.companyapp.reporting

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.reporting.ClientHistoryResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.CLIENT_HISTORY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "client_history",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClientHistoryResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ClientHistoryRoutes {
    fun register(config: JavalinConfig) {
        // Same gate as the client record itself (ClientRoutes): clients are global records.
        config.routes.before(ApiRoutes.CLIENT_HISTORY_PATH) { context ->
            CapabilityFilter.requireAnyContextCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }
        config.routes.get(ApiRoutes.CLIENT_HISTORY_PATH) { context ->
            context.json(ClientHistoryRepository.load(context.pathParamAsUuid("clientId")))
        }
    }
}
