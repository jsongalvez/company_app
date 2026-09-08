package com.companyb.companyapp.utils

import java.util.Base64

/**
 * Opaque URL-safe cursor encoding for keyset pagination: parts joined with
 * `|`, base64url (no padding). Format is internal — decode with
 * [decodeOpaqueCursor]; never parse client-side.
 */
fun encodeOpaqueCursor(vararg parts: String): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(parts.joinToString("|").toByteArray(Charsets.UTF_8))

/** Inverse of [encodeOpaqueCursor]; throws [IllegalArgumentException] on malformed input. */
fun decodeOpaqueCursor(raw: String): List<String> {
    val decoded =
        runCatching {
            String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("Invalid cursor") }
    val parts = decoded.split("|")
    require(parts.isNotEmpty()) { "Invalid cursor" }
    return parts
}
