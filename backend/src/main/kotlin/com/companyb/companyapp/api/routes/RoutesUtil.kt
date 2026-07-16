package com.companyb.companyapp.api.routes

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.util.UUID

fun Context.pathParamAsUuid(name: String): UUID =
    runCatching { UUID.fromString(this.pathParam(name)) }
        .getOrElse { throw BadRequestResponse("Invalid $name") }

fun uuidOrThrow(
    value: String,
    name: String,
): UUID =
    runCatching { UUID.fromString(value) }
        .getOrElse { throw BadRequestResponse("Invalid $name") }

fun Context.uuidFromQuery(name: String): UUID {
    val value = this.queryParam(name) ?: throw BadRequestResponse("$name query param is required")
    return uuidOrThrow(value, name)
}
