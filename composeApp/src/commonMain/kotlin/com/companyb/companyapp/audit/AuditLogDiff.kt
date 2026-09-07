package com.companyb.companyapp.audit

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
// changed-field list, and the single-parse JSON diff (corruption = render nothing, absence =
// "no field changes recorded").

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
            text = action.name,
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

// D3 — the backend stores only changed fields (`old_value`/`new_value` JSONB), so the diff
// renders exactly that: UPDATE = `field: old → new`; INSERT = added fields only; DELETE =
// removed fields (+ the reason line rendered by the row from `entry.reason`).
@Composable
internal fun ChangedFieldsList(
    fields: List<ChangedField>,
    action: AuditAction,
) {
    fields.forEach { field ->
        Text(
            text =
                buildString {
                    append(field.field)
                    append(": ")
                    when (action) {
                        AuditAction.INSERT -> {
                            append(field.new ?: "—")
                        }

                        AuditAction.DELETE -> {
                            append(field.old ?: "—")
                        }

                        else -> {
                            append(field.old ?: "—")
                            append(" → ")
                            append(field.new ?: "—")
                        }
                    }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// D3 — a changed-field line. `old`/`new` are display-ready (the "null" sentinel the backend
// writes for absent values renders as "—"); either side may be null (INSERT/DELETE shapes).
internal data class ChangedField(
    val field: String,
    val old: String?,
    val new: String?,
)

@Suppress("ReturnCount") // guard-clause style: malformed-data and empty-diff exits are distinct answers
internal fun parseChangedFields(
    oldValue: String?,
    newValue: String?,
): List<ChangedField> = parseDiff(oldValue, newValue).first

// D3 — distinguishes a genuinely empty diff (both sides absent — the "No field changes recorded"
// line) from a present-but-unparseable side (server-data corruption — the row renders nothing).
internal fun diffHasMalformedSide(
    oldValue: String?,
    newValue: String?,
): Boolean = parseDiff(oldValue, newValue).second

// Parses both diff sides ONCE (single JSON parse + single corruption logWarn per side) and
// returns the renderable fields + whether either side was malformed (the row renders nothing on
// the latter; "No field changes recorded" only when both sides are genuinely absent/empty).
internal fun parseDiff(
    oldValue: String?,
    newValue: String?,
): Pair<List<ChangedField>, Boolean> {
    val old = parseFieldMap(oldValue)
    val new = parseFieldMap(newValue)
    // A present-but-unparseable side is server-data corruption — render nothing rather than a
    // partial diff from the healthy side.
    if (old is FieldMap.Malformed || new is FieldMap.Malformed) return emptyList<ChangedField>() to true
    val oldFields = (old as? FieldMap.Valid)?.fields
    val newFields = (new as? FieldMap.Valid)?.fields
    if (oldFields == null && newFields == null) return emptyList<ChangedField>() to false
    val keys = (oldFields?.keys ?: emptySet()) + (newFields?.keys ?: emptySet())
    return keys.sorted().map { key ->
        ChangedField(
            field = key,
            // A side that is absent entirely (INSERT/DELETE shape) stays null; a key missing on
            // one side of an UPDATE renders "—" for that side.
            old = oldFields?.let { it[key]?.toDisplayValue() ?: "—" },
            new = newFields?.let { it[key]?.toDisplayValue() ?: "—" },
        )
    } to false
}

private sealed interface FieldMap {
    data object Absent : FieldMap

    data object Malformed : FieldMap

    data class Valid(
        val fields: Map<String, String>,
    ) : FieldMap
}

@Suppress("ReturnCount") // guard-clause style: absent/malformed/valid are three terminal answers
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

private fun String.toDisplayValue(): String = if (this == "null") "—" else this
