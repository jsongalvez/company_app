package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.service.UserService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.util.UUID

object UserRoutes {
    private const val USER_ID_PARAM = "userId"

    fun deactivate(config: JavalinConfig) {
        config.routes.patch("/api/users/{$USER_ID_PARAM}/deactivate") { context ->
            // The /api/* before-filter has already authenticated the caller and set "userId".
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.deactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
