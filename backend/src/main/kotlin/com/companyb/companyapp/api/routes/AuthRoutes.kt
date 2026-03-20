package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.service.AuthService
import io.javalin.config.JavalinConfig
import io.javalin.http.bodyAsClass

object AuthRoutes {
    fun login(context: JavalinConfig) {
        context.routes.post("/auth/login") { context ->
            val loginRequest = context.bodyAsClass<LoginRequest>()
            val loginResponse: LoginResponse = AuthService.login(loginRequest.username, loginRequest.password)
            if (loginResponse.token.isNullOrBlank()) {
                context.status(401)
            } else {
                context.status(200)
                context.result(loginResponse.token.toString())
            }
        }
    }
}
