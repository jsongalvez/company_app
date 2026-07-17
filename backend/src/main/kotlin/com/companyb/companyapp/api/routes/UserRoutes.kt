package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.service.UserService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import java.util.UUID

object UserRoutes {
    private const val USER_ID_PARAM = "userId"

    fun deactivate(config: JavalinConfig) {
        config.routes.before("/api/users/{$USER_ID_PARAM}/deactivate") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                "MANAGE_USERS capability required to deactivate users",
            )
        }

        config.routes.patch("/api/users/{$USER_ID_PARAM}/deactivate") { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.deactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
