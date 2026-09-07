package com.companyb.companyapp.api.routes

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.math.BigDecimal
import java.math.RoundingMode
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

const val DEFAULT_BROWSE_LIMIT = 20
const val MAX_BROWSE_LIMIT = 100

/** Shared keyset-browse limit parsing (audit log + daily summaries). */
fun parseBrowseLimit(raw: String?): Int {
    if (raw == null) return DEFAULT_BROWSE_LIMIT
    val limit =
        runCatching { raw.toInt() }
            .getOrElse { throw BadRequestResponse("Invalid limit: $raw") }
    if (limit < 1 || limit > MAX_BROWSE_LIMIT) {
        throw BadRequestResponse("limit must be between 1 and $MAX_BROWSE_LIMIT")
    }
    return limit
}

/**
 * Parses the request body only when one is present; endpoints that historically accepted
 * a body-less request keep working without one.
 */
inline fun <reified T> Context.bodyIfPresent(): T? {
    val raw = this.body()
    return if (raw.isBlank()) null else this.bodyAsClass(T::class.java)
}

@Suppress("MagicNumber")
fun parseNonNegativeBigDecimal(
    value: String,
    name: String,
    scale: Int = 2,
    roundingMode: RoundingMode = RoundingMode.HALF_UP,
): BigDecimal {
    val result =
        runCatching { BigDecimal(value).setScale(scale, roundingMode) }
            .getOrElse { throw BadRequestResponse("Invalid $name amount: $value") }
    if (result < BigDecimal.ZERO) throw BadRequestResponse("$name must be non-negative")
    return result
}

@Suppress("MagicNumber")
fun parsePositiveBigDecimal(
    value: String,
    name: String,
    scale: Int = 2,
    roundingMode: RoundingMode = RoundingMode.HALF_UP,
): BigDecimal {
    val result =
        runCatching { BigDecimal(value).setScale(scale, roundingMode) }
            .getOrElse { throw BadRequestResponse("Invalid $name amount: $value") }
    if (result <= BigDecimal.ZERO) throw BadRequestResponse("$name must be positive")
    return result
}
