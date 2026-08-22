package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.dto.AcceptInviteRequest
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.service.AuthService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.AUTH_LOGIN,
    methods = [HttpMethod.POST],
    operationId = "auth_login",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = LoginRequest::class)]),
    responses = [OpenApiResponse(status = "200"), OpenApiResponse(status = "401"), OpenApiResponse(status = "429")],
)
@OpenApi(
    path = ApiRoutes.AUTH_ACCEPT_INVITE,
    methods = [HttpMethod.POST],
    operationId = "auth_accept_invite",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AcceptInviteRequest::class)]),
    responses = [OpenApiResponse(status = "204"), OpenApiResponse(status = "400")],
)
@OpenApi(
    path = ApiRoutes.AUTH_LOGOUT,
    methods = [HttpMethod.POST],
    operationId = "auth_logout",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [OpenApiResponse(status = "200")],
)
object AuthRoutes {
    fun login(context: JavalinConfig) {
        context.routes.post(ApiRoutes.AUTH_LOGIN) { context ->
            val loginRequest = context.bodyAsClass<LoginRequest>()
            val loginResult: LoginResult =
                AuthService.login(
                    loginRequest.username,
                    loginRequest.password,
                    context.ip(),
                )

            when (loginResult) {
                is LoginResult.Success -> {
                    context.status(HttpStatus.OK)
                    context.json(LoginResponse(loginResult.token))
                }

                LoginResult.RateLimited -> {
                    context.status(HttpStatus.TOO_MANY_REQUESTS)
                }

                LoginResult.InvalidCredentials -> {
                    context.status(HttpStatus.UNAUTHORIZED)
                }
            }
        }
    }

    /**
     * #350 — public invite redemption: the code IS the authorization. Registered directly
     * outside the authenticated api prefix, like login; failures are domain 400s with
     * distinct messages.
     */
    fun acceptInvite(config: JavalinConfig) {
        config.routes.post(ApiRoutes.AUTH_ACCEPT_INVITE) { context ->
            val request = context.bodyAsClass<AcceptInviteRequest>()
            AuthService.acceptInvite(request.token, request.newPassword)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    fun logout(config: JavalinConfig) {
        config.routes.post(ApiRoutes.AUTH_LOGOUT) { context ->
            val callerId = context.callerUuid()
            DenyList.deny(callerId)
            context.status(HttpStatus.OK)
        }
    }
}
