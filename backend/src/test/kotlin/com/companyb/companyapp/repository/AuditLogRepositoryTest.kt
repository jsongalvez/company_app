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
}
