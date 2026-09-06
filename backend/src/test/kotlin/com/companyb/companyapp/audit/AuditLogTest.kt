package com.companyb.companyapp.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class AuditLogTest {
    @Test
    fun `jsonField builds a single-field JSON object`() {
        assertEquals("""{"status":"INACTIVE"}""", AuditLog.jsonField("status", "INACTIVE"))
    }

    @Test
    fun `jsonField escapes special characters in the value`() {
        assertEquals(
            """{"reason":"he said \"hi\""}""",
            AuditLog.jsonField("reason", "he said \"hi\""),
        )
    }

    @Test
    fun `jsonFields with Map produces same output as vararg for equivalent input`() {
        val map = mapOf("name" to "Alice", "age" to "30")
        val varargResult = AuditLog.jsonFields("name" to "Alice", "age" to "30")
        val mapResult = AuditLog.jsonFields(map)
        assertEquals(varargResult, mapResult)
    }

    @Test
    fun `jsonFields with Map handles empty map`() {
        assertEquals("{}", AuditLog.jsonFields(emptyMap()))
    }
}
