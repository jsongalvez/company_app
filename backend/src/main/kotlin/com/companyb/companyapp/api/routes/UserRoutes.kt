package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.service.UserService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import java.util.UUID

object UserRoutes {
    private const val USER_ID_PARAM = "userId"
    private const val MANAGE_USERS_MESSAGE = "MANAGE_USERS capability required to manage users"

    fun register(config: JavalinConfig) {
        deactivate(config)
        list(config)
        reactivate(config)
    }

    fun deactivate(config: JavalinConfig) {
        config.routes.before("/api/users/{$USER_ID_PARAM}/deactivate") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.patch("/api/users/{$USER_ID_PARAM}/deactivate") { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.deactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    private fun list(config: JavalinConfig) {
        config.routes.before("/api/users") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.get("/api/users") { context ->
            context.status(HttpStatus.OK)
            context.json(UserService.listUsers())
        }
    }

    private fun reactivate(config: JavalinConfig) {
        config.routes.before("/api/users/{$USER_ID_PARAM}/reactivate") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                MANAGE_USERS_MESSAGE,
            )
        }

        config.routes.patch("/api/users/{$USER_ID_PARAM}/reactivate") { context ->
            val callerId = context.callerUuid()
            val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

            UserService.reactivate(callerId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
