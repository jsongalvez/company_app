package com.companyb.companyapp.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiErrorsTest {
    @Test
    fun extractApiErrorMessage_parses_error_field_else_null() {
        val body = """{"error":"Username already exists"}"""
        assertEquals(expected = "Username already exists", actual = extractApiErrorMessage(body))
        assertNull(extractApiErrorMessage("""{"other":"x"}"""))
        assertNull(extractApiErrorMessage("not json"))
        assertNull(extractApiErrorMessage(null))
        assertNull(extractApiErrorMessage(""))
    }

    @Test
    fun extractApiErrorMessage_ignores_unknown_fields() {
        assertEquals(
            expected = "Slot must be 1 or greater",
            actual = extractApiErrorMessage("""{"error":"Slot must be 1 or greater","code":400}"""),
        )
    }

    @Test
    fun extractApiErrorMessage_rejects_malformed_and_null_shapes() {
        assertNull(extractApiErrorMessage("{error:unquoted}"))
        assertNull(extractApiErrorMessage("""{"error":null}"""))
        assertNull(extractApiErrorMessage("""["error"]"""))
    }
}
