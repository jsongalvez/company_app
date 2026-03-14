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
            if (token == null) {
                context.status(401)
            } else {
                context.status(200)
                context.result(token)
            }
        }
    }
}
