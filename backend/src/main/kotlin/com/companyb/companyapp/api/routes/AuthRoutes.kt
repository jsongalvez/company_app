package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.mapping.toErrorResponse
import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.RegisterRequest
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
    path = "/auth/login",
    methods = [HttpMethod.POST],
    operationId = "auth_login",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = LoginRequest::class)]),
    responses = [OpenApiResponse(status = "200"), OpenApiResponse(status = "401"), OpenApiResponse(status = "429")],
)
@OpenApi(
    path = "/auth/register",
    methods = [HttpMethod.POST],
    operationId = "auth_register",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RegisterRequest::class)]),
    responses = [OpenApiResponse(status = "201"), OpenApiResponse(status = "409"), OpenApiResponse(status = "422")],
)
@OpenApi(
    path = "/api/auth/logout",
    methods = [HttpMethod.POST],
    operationId = "auth_logout",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [OpenApiResponse(status = "200")],
)
object AuthRoutes {
    fun login(context: JavalinConfig) {
        context.routes.post("/auth/login") { context ->
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

    fun logout(config: JavalinConfig) {
        config.routes.post("/api/auth/logout") { context ->
            val callerId = context.callerUuid()
            DenyList.deny(callerId)
            context.status(HttpStatus.OK)
        }
    }

    fun register(context: JavalinConfig) {
        context.routes.post("/auth/register") { context ->
            val registerRequest = context.bodyAsClass<RegisterRequest>()
            val registerResult: RegisterResult =
                AuthService.register(
                    registerRequest.username,
                    registerRequest.password,
                    registerRequest.email,
                    registerRequest.displayName,
                )

            when (registerResult) {
                RegisterResult.Success -> {
                    context.status(HttpStatus.CREATED)
                    return@post
                }

                RegisterResult.UsernameTaken, RegisterResult.EmailTaken -> {
                    context.status(HttpStatus.CONFLICT)
                    context.json(registerResult.toErrorResponse())
                }

                is RegisterResult.WeakPassword, RegisterResult.InvalidEmail -> {
                    context.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    context.json(registerResult.toErrorResponse())
                }
            }
        }
    }
}
