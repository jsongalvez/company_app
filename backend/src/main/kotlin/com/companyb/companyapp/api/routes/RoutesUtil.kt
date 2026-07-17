package com.companyb.companyapp.api.routes

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

fun Context.uuidFromBody(key: String): UUID {
    val node = this.bodyAsClass(kotlinx.serialization.json.JsonObject::class.java)
    val value =
        node[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: throw BadRequestResponse("$key is required in request body")
    return uuidOrThrow(value, key)
}
