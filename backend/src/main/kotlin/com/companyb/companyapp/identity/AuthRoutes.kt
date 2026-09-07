package com.companyb.companyapp.identity
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.contracts.identity.AcceptInviteRequest
import com.companyb.companyapp.contracts.identity.ForgotPasswordRequest
import com.companyb.companyapp.contracts.identity.LoginRequest
import com.companyb.companyapp.contracts.identity.LoginResponse
import com.companyb.companyapp.contracts.identity.ResetPasswordRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.AuthService
import com.companyb.companyapp.identity.LoginResult
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity

@OpenApi(
    path = ApiRoutes.AUTH_LOGIN,
    methods = [HttpMethod.POST],
    operationId = "auth_login",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = LoginRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = LoginResponse::class)]),
        OpenApiResponse(status = "401"),
        OpenApiResponse(status = "429"),
    ],
)
@OpenApi(
    path = ApiRoutes.AUTH_ACCEPT_INVITE,
    methods = [HttpMethod.POST],
    operationId = "auth_accept_invite",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AcceptInviteRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUTH_FORGOT_PASSWORD,
    methods = [HttpMethod.POST],
    operationId = "auth_forgot_password",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ForgotPasswordRequest::class)]),
    // 204 regardless of whether the identifier matched — enumeration resistance (#353).
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "429"),
    ],
)
@OpenApi(
    path = ApiRoutes.AUTH_RESET_PASSWORD,
    methods = [HttpMethod.POST],
    operationId = "auth_reset_password",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ResetPasswordRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUTH_LOGOUT,
    methods = [HttpMethod.POST],
    operationId = "auth_logout",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
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

    /**
     * #353 — public forgot-password request leg. Always 204 on success (the body reveals
     * nothing about identifier existence); 429 when the caller's IP exceeds its budget.
     */
    fun forgotPassword(config: JavalinConfig) {
        config.routes.post(ApiRoutes.AUTH_FORGOT_PASSWORD) { context ->
            val request = context.bodyAsClass<ForgotPasswordRequest>()
            val accepted = AuthService.requestPasswordReset(request.identifier, context.ip())
            if (accepted) {
                context.status(HttpStatus.NO_CONTENT)
            } else {
                context.status(HttpStatus.TOO_MANY_REQUESTS)
            }
        }
    }

    /**
     * #353 — public reset redemption leg; the code IS the authorization, exactly like
     * [acceptInvite]. Failures are domain 400s with distinct messages.
     */
    fun resetPassword(config: JavalinConfig) {
        config.routes.post(ApiRoutes.AUTH_RESET_PASSWORD) { context ->
            val request = context.bodyAsClass<ResetPasswordRequest>()
            AuthService.resetPassword(request.token, request.newPassword)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    fun logout(config: JavalinConfig) {
        config.routes.post(ApiRoutes.AUTH_LOGOUT) { context ->
            AuthService.logout(context.callerUuid())
            context.status(HttpStatus.OK)
        }
    }
}
