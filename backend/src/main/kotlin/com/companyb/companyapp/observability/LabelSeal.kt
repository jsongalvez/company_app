package com.companyb.companyapp.observability

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Audit L5 (user, message 13675: "On telemetry, sure"). The page sends each tapped label as a
 * 32-bit FNV-1a hash ([LABEL_KEYS]), which anyone can recompute for a short label such as a name.
 * Before an event is stored, the server replaces each one with a keyed HMAC-SHA256 of it, so only
 * someone holding the deployment's secret can match a label. `tools/replay/replay.mjs` derives
 * the same key from the same secret.
 */
object LabelSeal {
    private val LABEL_KEYS = setOf("h", "al", "lh")
    private const val DERIVE = "telemetry-labels"
    private const val HEX_CHARS = 12
    private const val KEY_BYTES = 32
    private const val HMAC = "HmacSHA256"

    @Volatile
    private var key: ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    /** [secret] is `TELEMETRY_SALT` when set, else `JWT_SECRET`; never in git. */
    fun configure(secret: String) {
        key = hmac(secret.toByteArray(), DERIVE)
    }

    fun seal(data: String): String {
        val tree = runCatching { Json.parseToJsonElement(data) }.getOrNull() ?: return data
        return sealed(tree).toString()
    }

    private fun sealed(e: JsonElement): JsonElement =
        when (e) {
            is JsonObject -> {
                JsonObject(
                    e.mapValues { (k, v) ->
                        val label = k in LABEL_KEYS && v is JsonPrimitive && v.isString
                        if (label) JsonPrimitive(mark(v.content)) else sealed(v)
                    },
                )
            }

            is JsonArray -> {
                JsonArray(e.map(::sealed))
            }

            else -> {
                e
            }
        }

    private fun mark(fnv: String): String = hmac(key, fnv).joinToString("") { "%02x".format(it) }.take(HEX_CHARS)

    private fun hmac(
        k: ByteArray,
        text: String,
    ): ByteArray =
        Mac
            .getInstance(HMAC)
            .apply { init(SecretKeySpec(k, HMAC)) }
            .doFinal(text.toByteArray())
}
