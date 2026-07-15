package com.companyb.companyapp.api.routes

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.util.UUID

fun Context.pathParamAsUuid(name: String): UUID =
    runCatching { UUID.fromString(this.pathParam(name)) }
        .getOrElse { throw BadRequestResponse("Invalid $name") }
