package com.companyb.companyapp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [parseChangedFields] (#123 build of the locked #104 D3 — the backend stores only
 * changed fields in `old_value`/`new_value` JSONB; the diff renders exactly that: UPDATE =
 * `field: old → new`, INSERT = added fields, DELETE = removed fields, "null" sentinel → "—").
 */
class AuditLogDiffTest {
    @Test
    fun update_with_both_sides_renders_old_to_new() {
        val fields =
            parseChangedFields(
                oldValue = """{"status":"OPEN","note":"a"}""",
                newValue = """{"status":"REMITTED","note":"a"}""",
            )

        assertEquals(
            expected = listOf("note" to "a" to "a", "status" to "OPEN" to "REMITTED"),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun insert_with_new_only_renders_added_fields() {
        val fields = parseChangedFields(oldValue = null, newValue = """{"name":"New Client"}""")

        assertEquals(
            expected = listOf("name" to null to "New Client"),
            actual =
                fields.map {
                    it.field to it.old to
                        it.new
                },
        )
    }

    @Test
    fun delete_with_old_only_renders_removed_fields() {
        val fields = parseChangedFields(oldValue = """{"note":"old"}""", newValue = null)

        assertEquals(expected = listOf("note" to "old" to null), actual = fields.map { it.field to it.old to it.new })
    }

    @Test
    fun null_sentinel_renders_as_dash() {
        val fields =
            parseChangedFields(
                oldValue = """{"status":"OPEN"}""",
                newValue = """{"status":"null"}""",
            )

        assertEquals(expected = listOf("status" to "OPEN" to "—"), actual = fields.map { it.field to it.old to it.new })
    }

    @Test
    fun key_missing_on_one_side_renders_dash_for_that_side() {
        val fields =
            parseChangedFields(
                oldValue = """{"a":"1"}""",
                newValue = """{"a":"1","b":"2"}""",
            )

        assertEquals(
            expected = listOf("a" to "1" to "1", "b" to "—" to "2"),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun fields_are_sorted_alphabetically() {
        val fields =
            parseChangedFields(
                oldValue = """{"z":"1","a":"2"}""",
                newValue = """{"z":"1","a":"3"}""",
            )

        assertEquals(expected = listOf("a", "z"), actual = fields.map { it.field })
    }

    @Test
    fun malformed_json_returns_empty_list() {
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = "not-json", newValue = null),
        )
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = """{"a":"1"}""", newValue = "also-not-json"),
        )
    }

    @Test
    fun non_object_json_returns_empty_list() {
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = "[1,2]", newValue = null),
        )
    }

    @Test
    fun both_null_returns_empty_list() {
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = null, newValue = null),
        )
    }

    @Test
    fun empty_objects_return_empty_list() {
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = """{}""", newValue = """{}"""),
        )
    }
}
