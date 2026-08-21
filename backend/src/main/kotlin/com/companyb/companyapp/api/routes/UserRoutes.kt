package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.UserCreateRequest
import com.companyb.companyapp.dto.UserRoleReplaceRequest
import com.companyb.companyapp.service.UserService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.USERS,
    methods = [HttpMethod.GET],
    operationId = "users",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.USERS,
    methods = [HttpMethod.POST],
    operationId = "user_create",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UserCreateRequest::class)]),
    responses = [
        OpenApiResponse(
            status = "201",
            content = [OpenApiContent(from = com.companyb.companyapp.dto.UserSummaryResponse::class)],
        ), OpenApiResponse(status = "400"), OpenApiResponse(status = "409"),
    ],
)
@OpenApi(
    path = ApiRoutes.ROLES,
    methods = [HttpMethod.GET],
    operationId = "roles_list",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = com.companyb.companyapp.dto.RoleResponse::class)],
        ),
    ],
)
@OpenApi(
    path = "/api/users/{userId}/deactivate",
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_deactivate",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/users/{userId}/reactivate",
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_reactivate",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.USER_ROLES_PATH,
    methods = [HttpMethod.PUT],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_roles_replace",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UserRoleReplaceRequest::class)]),
    responses = [OpenApiResponse(status = "204"), OpenApiResponse(status = "400"), OpenApiResponse(status = "404")],
)
object UserRoutes {
    private const val USER_ID_PARAM = "userId"
    private const val MANAGE_USERS_MESSAGE = "MANAGE_USERS capability required to manage users"

    fun register(config: JavalinConfig) {
        deactivate(config)
        list(config)
        reactivate(config)
        create(config)
        roles(config)
        replaceRoles(config)
    }

    fun deactivate(config: JavalinConfig) {
        config.routes.before(ApiRoutes.USER_DEACTIVATE_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.patch(ApiRoutes.USER_DEACTIVATE_PATH) { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.deactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    private fun list(config: JavalinConfig) {
        config.routes.before(ApiRoutes.USERS) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.get(ApiRoutes.USERS) { context ->
            context.status(HttpStatus.OK)
            context.json(UserService.listUsers())
        }
    }

    private fun reactivate(config: JavalinConfig) {
        config.routes.before(ApiRoutes.USER_REACTIVATE_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.patch(ApiRoutes.USER_REACTIVATE_PATH) { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.reactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    private fun create(config: JavalinConfig) {
        config.routes.post(ApiRoutes.USERS) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<UserCreateRequest>()

            context.status(HttpStatus.CREATED)
            context.json(UserService.create(callerId, request))
        }
    }

    private fun roles(config: JavalinConfig) {
        config.routes.before(ApiRoutes.ROLES) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.get(ApiRoutes.ROLES) { context ->
            context.status(HttpStatus.OK)
            context.json(UserService.getRoles())
        }
    }

    private fun replaceRoles(config: JavalinConfig) {
        config.routes.before(ApiRoutes.USER_ROLES_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.put(ApiRoutes.USER_ROLES_PATH) { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)
            val request = context.bodyAsClass<UserRoleReplaceRequest>()

            UserService.replaceRoles(callerId, targetUserId, request.roles)
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
