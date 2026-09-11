package com.companyb.companyapp.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// #479 — the audit-log diff + badge rendering seam (#104 D3/D7), extracted from
// AuditLogScreen.kt so the file-function wall (TMF) stays honest: the action pill, flag badge,
// changed-field list, and the single-parse JSON diff (#686: fully-unavailable corruption shows
// the "Changes unavailable" box, partial corruption keeps decodable values with a warning,
// genuine absence shows "No field changes recorded").

@Composable
internal fun ActionPill(action: AuditAction) {
    val (background, content) =
        when (action) {
            AuditAction.INSERT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            AuditAction.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = background,
    ) {
        Text(
            text = auditOperationLabel(action),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun FlagBadge() {
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "flagged",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

// #686 — human-readable operation verbs: the pill and row lead with Created/Updated/Deleted,
// never the raw INSERT/UPDATE/DELETE enum names.
internal fun auditOperationLabel(action: AuditAction): String =
    when (action) {
        AuditAction.INSERT -> "Created"

        AuditAction.UPDATE -> "Updated"

        AuditAction.DELETE -> "Deleted"

        // #876 — forward-compat sentinel: a newer server action degrades the pill text,
        // never the row.
        AuditAction.UNKNOWN -> "Unknown"
    }

// #686 — human-readable identifiers: underscores become spaces, camelCase splits, then
// sentence-case. Registry labels win; this is the fallback only (the exact technical key
// stays in the collapsed Technical details area).
internal fun formatAuditFieldName(raw: String): String {
    if (raw.isBlank()) return raw
    val spaced = raw.replace('_', ' ').replace(CAMEL_BOUNDARY, " $1")
    val lowered = spaced.trim().replace(WHITESPACE_RUN, " ").lowercase()
    return lowered.replaceFirstChar { it.uppercase() }
}

// #686 — table fallback shares the field formatter (snake_case table names + camelCase field
// keys converge here). Blank input has no words to humanize — callers use the unknown marker.
internal fun formatAuditTableFallback(tableName: String): String =
    if (tableName.isBlank()) AUDIT_UNKNOWN_TABLE else formatAuditFieldName(tableName)

// #686 — registry first, humanized fallback second. Never returns blank.
internal fun resolveAuditTableLabel(
    tableLabels: Map<String, String>,
    tableName: String,
): String =
    tableLabels[tableName]?.takeIf { it.isNotBlank() }
        ?: formatAuditTableFallback(tableName).takeIf { it.isNotBlank() }
        ?: AUDIT_UNKNOWN_TABLE

// #686 — distinct value vocabulary: null sentinel and absent sides are "Not set", empty text is
// "Empty text", zero and every other value stay faithful (including "[redacted]", numbers,
// booleans). Unknown values are never coerced into money/date guesses.
internal const val AUDIT_NOT_SET = "Not set"
internal const val AUDIT_EMPTY_TEXT = "Empty text"
internal const val AUDIT_VALUE_UNAVAILABLE = "Unavailable"
internal const val AUDIT_CHANGES_UNAVAILABLE_TITLE = "Changes unavailable"
internal const val AUDIT_CHANGES_UNAVAILABLE_BODY =
    "The stored change data could not be decoded, so no field values can be shown."
internal const val AUDIT_CHANGES_PARTIAL_BODY =
    "Some changes could not be decoded — shown values are partial."
internal const val AUDIT_NO_CHANGES_TEXT = "No field changes recorded."
internal const val AUDIT_UNKNOWN_TABLE = "Unknown"

private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])([A-Z])")
private val WHITESPACE_RUN = Regex("\\s+")
private const val COMPACT_DIFF_WIDTH_DP = 600

// D3 — the backend stores only changed fields (`old_value`/`new_value` JSONB), so the diff
// renders exactly that: UPDATE = before/after per field; INSERT = added fields only; DELETE =
// removed fields (+ the reason line rendered by the row from `entry.reason`). Each field leads
// with its human label; wide rows place before/after side by side, compact rows stack them
// under the label. Values stay selectable.
@Composable
internal fun ChangedFieldsList(
    fields: List<ChangedField>,
    action: AuditAction,
) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            fields.forEach { field ->
                AuditDiffFieldRow(field = field, action = action)
            }
        }
    }
}

@Composable
private fun AuditDiffFieldRow(
    field: ChangedField,
    action: AuditAction,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < COMPACT_DIFF_WIDTH_DP.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatAuditFieldName(field.field),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (stacked) {
                AuditDiffSide(label = "Before", value = diffBefore(field, action))
                AuditDiffSide(label = "After", value = diffAfter(field, action))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    AuditDiffSide(label = "Before", value = diffBefore(field, action), modifier = Modifier.weight(1f))
                    AuditDiffSide(label = "After", value = diffAfter(field, action), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AuditDiffSide(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: AUDIT_NOT_SET,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun diffBefore(
    field: ChangedField,
    action: AuditAction,
): String? =
    when (action) {
        AuditAction.INSERT -> null
        AuditAction.DELETE -> field.old
        else -> field.old
    }

private fun diffAfter(
    field: ChangedField,
    action: AuditAction,
): String? =
    when (action) {
        AuditAction.INSERT -> field.new
        AuditAction.DELETE -> null
        else -> field.new
    }

// D3 — a changed-field line. `old`/`new` are display-ready (#686: the "null" sentinel the
// backend writes for absent values renders as "Not set", "" as "Empty text"); either side may
// be null (INSERT/DELETE absent shapes, rendered as "Not set").
internal data class ChangedField(
    val field: String,
    val old: String?,
    val new: String?,
)

@Suppress("ReturnCount") // #600 guard-clause style: malformed-data and empty-diff exits are distinct answers
internal fun parseChangedFields(
    oldValue: String?,
    newValue: String?,
): List<ChangedField> = parseDiff(oldValue, newValue).first

// D3 — distinguishes a genuinely empty diff (both sides absent — the "No field changes
// recorded" line) from a present-but-unparseable side (#686: fully-unavailable corruption shows
// the "Changes unavailable" box, partial corruption keeps decodable values with a warning).
internal fun diffHasMalformedSide(
    oldValue: String?,
    newValue: String?,
): Boolean = parseDiff(oldValue, newValue).second

// Parses both diff sides ONCE (single JSON parse + single corruption logWarn per side) and
// returns the renderable fields + whether either side was malformed. A fully unavailable diff
// (malformed side with no decodable counterpart) renders the "Changes unavailable" box;
// a partial diff (one malformed side, one valid side with keys) keeps the valid values with
// the unavailable side labeled — never implying all changes rendered. "No field changes
// recorded" only when both sides are genuinely absent/empty.
internal fun parseDiff(
    oldValue: String?,
    newValue: String?,
): Pair<List<ChangedField>, Boolean> {
    val old = parseFieldMap(oldValue)
    val new = parseFieldMap(newValue)
    // #695 single-exit fold: each malformed/valid shape computes its answer as a
    // `when` branch value — no behavior change, only the 8 early returns collapse.
    // Branch bodies stay single calls so this orchestrator stays under Cyclomatic 15.
    val result: Pair<List<ChangedField>, Boolean> =
        when {
            old is FieldMap.Malformed && new is FieldMap.Malformed -> emptyList<ChangedField>() to true
            old is FieldMap.Malformed -> partialForMalformedOld(new)
            new is FieldMap.Malformed -> partialForMalformedNew(old)
            else -> mergedValidDiff(old, new)
        }
    return result
}

private fun partialForMalformedOld(new: FieldMap): Pair<List<ChangedField>, Boolean> {
    val newFields = (new as? FieldMap.Valid)?.fields
    return if (newFields.isNullOrEmpty()) {
        emptyList<ChangedField>() to true
    } else {
        newFields.keys.sorted().map { key ->
            ChangedField(field = key, old = AUDIT_VALUE_UNAVAILABLE, new = newFields[key]?.toDisplayValue())
        } to true
    }
}

private fun partialForMalformedNew(old: FieldMap): Pair<List<ChangedField>, Boolean> {
    val oldFields = (old as? FieldMap.Valid)?.fields
    return if (oldFields.isNullOrEmpty()) {
        emptyList<ChangedField>() to true
    } else {
        oldFields.keys.sorted().map { key ->
            ChangedField(field = key, old = oldFields[key]?.toDisplayValue(), new = AUDIT_VALUE_UNAVAILABLE)
        } to true
    }
}

private fun mergedValidDiff(
    old: FieldMap,
    new: FieldMap,
): Pair<List<ChangedField>, Boolean> {
    val oldFields = (old as? FieldMap.Valid)?.fields
    val newFields = (new as? FieldMap.Valid)?.fields
    // #601 max-2: empty-diff and key-diff share one exit.
    return if (oldFields == null && newFields == null) {
        emptyList<ChangedField>() to false
    } else {
        val keys = (oldFields?.keys ?: emptySet()) + (newFields?.keys ?: emptySet())
        keys.sorted().map { key ->
            ChangedField(
                field = key,
                // A side that is absent entirely (INSERT/DELETE shape) stays null; a key missing on
                // one side of an UPDATE renders "Not set" for that side via the display mapping.
                old = oldFields?.let { it[key]?.toDisplayValue() ?: AUDIT_NOT_SET },
                new = newFields?.let { it[key]?.toDisplayValue() ?: AUDIT_NOT_SET },
            )
        } to false
    }
}

private sealed interface FieldMap {
    data object Absent : FieldMap

    data object Malformed : FieldMap

    data class Valid(
        val fields: Map<String, String>,
    ) : FieldMap
}

@Suppress("ReturnCount") // #600 guard-clause style: absent/malformed/valid are three terminal answers
private fun parseFieldMap(raw: String?): FieldMap {
    if (raw == null) return FieldMap.Absent
    val element =
        runCatching { Json.parseToJsonElement(raw) }
            .getOrElse {
                logWarn("AuditLogScreen", "unparseable audit diff JSON: $raw")
                return FieldMap.Malformed
            }
    if (element !is JsonObject) return FieldMap.Malformed
    return FieldMap.Valid(
        element.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() },
    )
}

private fun String.toDisplayValue(): String =
    when {
        this == "null" -> AUDIT_NOT_SET
        this.isEmpty() -> AUDIT_EMPTY_TEXT
        else -> this
    }
