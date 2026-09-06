package com.companyb.companyapp.identity
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserRoleReplaceRequest
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.identity.UserService
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
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<UserSummaryResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ROLES,
    methods = [HttpMethod.GET],
    operationId = "roles_list",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<RoleResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.INVITES,
    methods = [HttpMethod.POST],
    operationId = "invite_mint",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = InviteMintRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = InviteMintResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.USER_DEACTIVATE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_deactivate",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.USER_REACTIVATE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_reactivate",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.USER_ROLES_PATH,
    methods = [HttpMethod.PUT],
    pathParams = [OpenApiParam(name = "userId", type = UUID::class, required = true)],
    operationId = "user_roles_replace",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UserRoleReplaceRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object UserRoutes {
    private const val USER_ID_PARAM = "userId"
    private const val MANAGE_USERS_MESSAGE = "MANAGE_USERS capability required to manage users"

    fun register(config: JavalinConfig) {
        deactivate(config)
        list(config)
        reactivate(config)
        mintInvite(config)
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

    /**
     * #350 — mint a single-use invite link (GLOBAL MANAGE_USERS per ADR-0007; service
     * commands stay capability-free). Public account creation itself happens at
     * /auth/accept-invite with the code, outside this gate.
     */
    private fun mintInvite(config: JavalinConfig) {
        config.routes.before(ApiRoutes.INVITES) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.post(ApiRoutes.INVITES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<InviteMintRequest>()

            context.status(HttpStatus.CREATED)
            context.json(UserService.mintInvite(callerId, request))
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
