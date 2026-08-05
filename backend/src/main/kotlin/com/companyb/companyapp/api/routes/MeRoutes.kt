package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.service.MeService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import java.util.UUID

object MeRoutes {
    fun getMe(config: JavalinConfig) {
        config.routes.get("/api/me") { context ->
            val callerId = context.callerUuid()
            val response: MeResponse = MeService.getMe(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getCapabilities(config: JavalinConfig) {
        config.routes.get("/api/me/capabilities") { context ->
            val callerId = context.callerUuid()
            val response: List<UserCapabilityResponse> = MeService.getCapabilities(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getBranches(config: JavalinConfig) {
        config.routes.get("/api/me/branches") { context ->
            val callerId = context.callerUuid()
            val response: List<MeBranchResponse> = MeService.getBranches(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }
}
