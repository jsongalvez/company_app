package com.companyb.companyapp.api

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.util.UUID

fun Context.callerUuid(): UUID {
    val userId =
        this.attribute<String>("userId")
            ?: throw BadRequestResponse("Missing authentication")
    return runCatching { UUID.fromString(userId) }
        .getOrElse { throw BadRequestResponse("Invalid user ID in authentication") }
}
