package com.companyb.companyapp.config

import io.javalin.json.JsonMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
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
        this@KotlinxSerializationMapper.json.decodeFromString(
            serializerForType(targetType),
            json,
        ) as T

    override fun toJsonString(
        obj: Any,
        type: Type,
    ): String = json.encodeToString(serializerForType(type), obj)

    private fun serializerForType(type: Type): KSerializer<Any> = serializer(type)
}
