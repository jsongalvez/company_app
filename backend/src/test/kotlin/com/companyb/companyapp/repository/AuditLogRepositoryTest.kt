package com.companyb.companyapp.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class AuditLogRepositoryTest {
    @Test
    fun `jsonField builds a single-field JSON object`() {
        assertEquals("""{"status":"INACTIVE"}""", AuditLogRepository.jsonField("status", "INACTIVE"))
    }

    @Test
    fun `jsonField escapes special characters in the value`() {
        assertEquals(
            """{"reason":"he said \"hi\""}""",
            AuditLogRepository.jsonField("reason", "he said \"hi\""),
        )
    }

    @Test
    fun `jsonFields with Map produces same output as vararg for equivalent input`() {
        val map = mapOf("name" to "Alice", "age" to "30")
        val varargResult = AuditLogRepository.jsonFields("name" to "Alice", "age" to "30")
        val mapResult = AuditLogRepository.jsonFields(map)
        assertEquals(varargResult, mapResult)
    }

    @Test
    fun `jsonFields with Map handles empty map`() {
        assertEquals("{}", AuditLogRepository.jsonFields(emptyMap()))
    }
}
