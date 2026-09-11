package com.companyb.companyapp.audit

import com.companyb.companyapp.contracts.audit.AuditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [parseChangedFields] (#123 build of the locked #104 D3 — the backend stores only
 * changed fields in `old_value`/`new_value` JSONB; the diff renders exactly that: UPDATE =
 * before/after per field, INSERT = added fields, DELETE = removed fields) and the #686
 * readability vocabulary (null → "Not set", empty → "Empty text", zero/redacted faithful;
 * partial malformed sides stay visible with the unavailable side labeled).
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
    fun null_sentinel_renders_as_not_set() {
        val fields =
            parseChangedFields(
                oldValue = """{"status":"OPEN"}""",
                newValue = """{"status":"null"}""",
            )

        assertEquals(
            expected = listOf("status" to "OPEN" to AUDIT_NOT_SET),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun key_missing_on_one_side_renders_not_set_for_that_side() {
        val fields =
            parseChangedFields(
                oldValue = """{"a":"1"}""",
                newValue = """{"a":"1","b":"2"}""",
            )

        assertEquals(
            expected = listOf("a" to "1" to "1", "b" to AUDIT_NOT_SET to "2"),
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
    fun malformed_with_absent_counterpart_returns_empty_list() {
        assertEquals(
            expected = emptyList<ChangedField>(),
            actual = parseChangedFields(oldValue = "not-json", newValue = null),
        )
    }

    @Test
    fun malformed_new_side_keeps_valid_old_values_as_partial() {
        // #686 — partial valid sides remain visible with the unavailable side labeled; the
        // malformed flag stays true so the row warns instead of implying completeness.
        val (fields, malformed) = parseDiff(oldValue = """{"a":"1"}""", newValue = "also-not-json")

        assertTrue(malformed)
        assertEquals(
            expected = listOf("a" to "1" to AUDIT_VALUE_UNAVAILABLE),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun malformed_old_side_keeps_valid_new_values_as_partial() {
        val (fields, malformed) = parseDiff(oldValue = "not-json", newValue = """{"b":"2"}""")

        assertTrue(malformed)
        assertEquals(
            expected = listOf("b" to AUDIT_VALUE_UNAVAILABLE to "2"),
            actual = fields.map { it.field to it.old to it.new },
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

    // #144 pass-1 as amended by #686: the row must distinguish a malformed diff side
    // (unavailable box when fully undecodable, partial values + warning when one side decodes)
    // from a genuinely empty diff (render "No field changes recorded").
    @Test
    fun malformed_side_is_detected() {
        assertTrue(diffHasMalformedSide(oldValue = "not-json", newValue = null))
        assertTrue(diffHasMalformedSide(oldValue = """{"a":"1"}""", newValue = "also-not-json"))
        assertTrue(diffHasMalformedSide(oldValue = "[1,2]", newValue = """{"a":"1"}"""))
    }

    @Test
    fun valid_or_absent_sides_are_not_malformed() {
        assertFalse(diffHasMalformedSide(oldValue = null, newValue = null))
        assertFalse(diffHasMalformedSide(oldValue = """{"a":"1"}""", newValue = null))
        assertFalse(diffHasMalformedSide(oldValue = """{"a":"1"}""", newValue = """{"a":"2"}"""))
        assertFalse(diffHasMalformedSide(oldValue = """{}""", newValue = """{}"""))
    }

    @Test
    fun empty_text_renders_distinct_from_not_set_and_zero() {
        // #686 — null, empty, and zero never flatten: "null" → Not set, "" → Empty text,
        // "0" stays "0".
        val fields =
            parseChangedFields(
                oldValue = """{"a":"","b":"0","c":"null"}""",
                newValue = """{"a":"x","b":"0","c":"y"}""",
            )

        assertEquals(
            expected =
                listOf(
                    "a" to AUDIT_EMPTY_TEXT to "x",
                    "b" to "0" to "0",
                    "c" to AUDIT_NOT_SET to "y",
                ),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun redacted_marker_stays_redacted() {
        val fields =
            parseChangedFields(
                oldValue = """{"firstName":"Ana"}""",
                newValue = """{"firstName":"[redacted]"}""",
            )

        assertEquals(
            expected = listOf("firstName" to "Ana" to "[redacted]"),
            actual = fields.map { it.field to it.old to it.new },
        )
    }

    @Test
    fun operation_labels_are_human_readable() {
        assertEquals("Created", auditOperationLabel(AuditAction.INSERT))
        assertEquals("Updated", auditOperationLabel(AuditAction.UPDATE))
        assertEquals("Deleted", auditOperationLabel(AuditAction.DELETE))
        // #876 — a newer server action degrades the pill text, never the row.
        assertEquals("Unknown", auditOperationLabel(AuditAction.UNKNOWN))
    }

    @Test
    fun field_names_humanize_snake_and_camel() {
        assertEquals("Branch day", formatAuditFieldName("branch_day"))
        assertEquals("First name", formatAuditFieldName("firstName"))
        assertEquals("Branch day id", formatAuditFieldName("branchDayId"))
    }

    @Test
    fun table_labels_prefer_registry_then_fallback() {
        assertEquals("Sessions", resolveAuditTableLabel(mapOf("session" to "Sessions"), "session"))
        assertEquals("Branch day", resolveAuditTableLabel(emptyMap(), "branch_day"))
    }

    @Test
    fun blank_table_name_never_returns_blank() {
        assertEquals(AUDIT_UNKNOWN_TABLE, resolveAuditTableLabel(emptyMap(), ""))
        assertEquals(AUDIT_UNKNOWN_TABLE, resolveAuditTableLabel(emptyMap(), "   "))
        assertEquals(AUDIT_UNKNOWN_TABLE, formatAuditTableFallback(""))
    }

    @Test
    fun action_display_names_share_pill_verbs_with_raw_fallback() {
        assertEquals("Created", auditActionDisplayName("INSERT"))
        assertEquals("Updated", auditActionDisplayName("UPDATE"))
        assertEquals("Deleted", auditActionDisplayName("DELETE"))
        assertEquals("BOGUS", auditActionDisplayName("BOGUS"))
    }

    @Test
    fun picker_seed_rejects_nonexistent_dates_without_throwing() {
        // #686 P4 HARD — pattern-valid but nonexistent dates must seed null (today), never throw.
        assertNull(auditIsoToPickerMillis("2026-02-30"))
        assertNull(auditIsoToPickerMillis(""))
        assertNull(auditIsoToPickerMillis("not-a-date"))
    }

    @Test
    fun applied_filter_count_counts_each_set_dimension_once() {
        assertEquals(0, auditAppliedFilterCount(AuditLogFilters()))
        assertEquals(
            2,
            auditAppliedFilterCount(AuditLogFilters(tableName = "session", dateFrom = "2026-08-01")),
        )
        assertEquals(
            0,
            auditAppliedFilterCount(AuditLogFilters(callerName = "   ", dateFrom = "")),
        )
    }
}
