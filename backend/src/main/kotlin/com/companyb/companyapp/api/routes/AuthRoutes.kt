package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.mapping.toErrorResponse
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.RegisterRequest
import com.companyb.companyapp.service.AuthService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass

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
                    context.status(HttpStatus.BAD_REQUEST)
                    context.json(registerResult.toErrorResponse())
                }
            }
        }
    }
}
