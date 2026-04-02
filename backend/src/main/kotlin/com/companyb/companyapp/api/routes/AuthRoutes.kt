package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.RegisterRequest
import com.companyb.companyapp.dto.RegisterResponse
import com.companyb.companyapp.service.AuthService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass

object AuthRoutes {
    // TODO: Apply rate limiting at API layer for auth endpoints
    fun login(context: JavalinConfig) {
        context.routes.post("/auth/login") { context ->
            val loginRequest = context.bodyAsClass<LoginRequest>()
            val token: String? = AuthService.login(loginRequest.username, loginRequest.password)

            if (token == null) {
                context.status(HttpStatus.UNAUTHORIZED)
                return@post
            }

            val subject = JwtService.verifyToken(token)
            if (subject == null) {
                context.status(HttpStatus.UNAUTHORIZED)
            } else {
                context.status(HttpStatus.OK)
                context.json(LoginResponse(token))
            }
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
                    context.json(RegisterResponse(isSuccess = true))
                }

                RegisterResult.UsernameTaken -> {
                    context.status(HttpStatus.CONFLICT)
                    context.json(RegisterResponse(isSuccess = false))
                }

                is RegisterResult.WeakPassword -> {
                    context.status(HttpStatus.BAD_REQUEST)
                    context.json(RegisterResponse(isSuccess = false))
                }
            }
        }
    }
}
