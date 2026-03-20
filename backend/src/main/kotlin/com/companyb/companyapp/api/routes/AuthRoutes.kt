package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.service.AuthService
import com.companyb.companyapp.utils.HTTP
import io.javalin.config.JavalinConfig
import io.javalin.http.bodyAsClass

object AuthRoutes {
    fun login(context: JavalinConfig) {
        context.routes.post("/auth/login") { context ->
            val loginRequest = context.bodyAsClass<LoginRequest>()
            val loginResponse: LoginResponse = AuthService.login(loginRequest.username, loginRequest.password)
            if (loginResponse.token.isNullOrBlank()) {
                context.status(HTTP.Response.ClientError.UNAUTHORIZED)
            } else {
                context.status(HTTP.Response.Successful.OK)
                context.result(loginResponse.token.toString())
            }
        }
    }
}
