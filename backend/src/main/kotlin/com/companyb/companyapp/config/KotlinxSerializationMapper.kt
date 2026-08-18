package com.companyb.companyapp.config

import io.javalin.http.BadRequestResponse
import io.javalin.json.JsonMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.lang.reflect.Type

class KotlinxSerializationMapper : JsonMapper {
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> fromJsonString(
        json: String,
        targetType: Type,
    ): T =
        try {
            this@KotlinxSerializationMapper.json.decodeFromString(
                serializerForType(targetType),
                json,
            ) as T
        } catch (_: SerializationException) {
            throw BadRequestResponse("Invalid request body")
        }

    override fun toJsonString(
        obj: Any,
        type: Type,
    ): String =
        json.encodeToString(
            JsonElement.serializer(),
            toJsonElement(obj, type),
        )

    private fun toJsonElement(
        obj: Any?,
        type: Type,
    ): JsonElement =
        when (obj) {
            null -> {
                JsonNull
            }

            is JsonElement -> {
                obj
            }

            is Collection<*> -> {
                JsonArray(obj.map { toJsonElement(it, it?.javaClass ?: Any::class.java) })
            }

            is Map<*, *> -> {
                JsonObject(
                    obj.mapKeys { it.key.toString() }.mapValues { (_, v) ->
                        toJsonElement(v, v?.javaClass ?: Any::class.java)
                    },
                )
            }

            else -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    json.encodeToJsonElement(serializerForType(type), obj)
                } catch (_: SerializationException) {
                    @Suppress("UNCHECKED_CAST")
                    json.encodeToJsonElement(serializerForType(obj.javaClass), obj)
                }
            }
        }

    private fun serializerForType(type: Type): KSerializer<Any> = serializer(type)
}
