package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.service.AuthService
import io.javalin.config.JavalinConfig
import io.javalin.http.bodyAsClass

object AuthRoutes {
    fun login(context: JavalinConfig) {
        context.routes.post("/auth/login") { context ->
            val loginRequest = context.bodyAsClass<LoginRequest>()
            val token: String? = AuthService.login(loginRequest.username, loginRequest.password)
            if (token.isNullOrBlank()) {
                context.status(io.javalin.http.HttpStatus.UNAUTHORIZED)
            } else {
                context.status(io.javalin.http.HttpStatus.OK)
                context.result(token)
            }
        }
    }
}
