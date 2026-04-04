package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.mapping.toErrorResponse
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.RateLimiter
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
            val ip = context.ip()

            val isRateLimited = !RateLimiter.isAllowed(ip)
            if (isRateLimited) {
                context.status(HttpStatus.TOO_MANY_REQUESTS)
                return@post
            }

            val loginRequest = context.bodyAsClass<LoginRequest>()
            val token: String =
                AuthService.login(loginRequest.username, loginRequest.password) ?: run {
                    context.status(HttpStatus.UNAUTHORIZED)
                    return@post
                }

            JwtService.verifyToken(token) ?: run {
                context.status(HttpStatus.UNAUTHORIZED)
                return@post
            }

            context.status(HttpStatus.OK)
            context.json(LoginResponse(token))
        }
    }

    fun register(context: JavalinConfig) {
        context.routes.post("/auth/register") { context ->
            val registerRequest = context.bodyAsClass<RegisterRequest>()
            val registerResult: RegisterResult =
                AuthService.register(registerRequest.username, registerRequest.password)

            when (registerResult) {
                RegisterResult.Success -> {
                    context.status(HttpStatus.CREATED)
                    return@post
                }

                RegisterResult.UsernameTaken -> {
                    context.status(HttpStatus.CONFLICT)
                    context.json(registerResult.toErrorResponse())
                }

                is RegisterResult.WeakPassword -> {
                    context.status(HttpStatus.BAD_REQUEST)
                    context.json(registerResult.toErrorResponse())
                }
            }
        }
    }
}
