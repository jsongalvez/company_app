package com.companyb.companyapp.audit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.contracts.audit.AuditLogEntryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp

// #479 — the audit-log entry-row seam (#104 D2/D3/D7/D11), extracted from AuditLogScreen.kt so
// the file-function wall (TMF) stays honest: the shared row (header + branch line + expanded
// details), its affordances, and the list-args bundle shared by the platform list actuals.
// Affordances/display ride carriers so every signature stays under the LongParameterList
// threshold (5).

/**
 * #479 — the per-row callback bundle (D2/D8/#390): acknowledge + history + client-record jump.
 * Built per row by the list actuals (the callbacks close over the row's entry).
 */
internal data class AuditLogRowAffordances(
    val currentUserId: String?,
    val onAcknowledge: () -> Unit,
    val acknowledging: Boolean,
    val ackError: String?,
    val showAcknowledge: Boolean = true,
    val showFullHistory: Boolean = true,
    val onFullHistory: () -> Unit,
    val onOpenClientRecord: (() -> Unit)? = null,
)

/**
 * #479 — the per-row display inputs: label + expansion state. Data classes are LPL-free; the
 * carrier keeps [AuditLogEntryRow] at 4 explicit params.
 */
internal data class AuditLogRowDisplay(
    val tableLabel: String,
    val expanded: Boolean,
    val onToggleExpanded: () -> Unit,
)

@Composable
private fun AuditLogExpandedDetails(
    entry: AuditLogEntryResponse,
    affordances: AuditLogRowAffordances,
) {
    Column(
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
    ) {
        val (fields, malformedDiff) =
            remember(entry.oldValue, entry.newValue) {
                parseDiff(entry.oldValue, entry.newValue)
            }
        when {
            // D3 — a present-but-unparseable diff side is server-data corruption: render
            // nothing rather than a misleading "no changes" line (corruption ≠ absence).
            malformedDiff -> {
                Unit
            }

            fields.isEmpty() -> {
                Text(
                    text = "No field changes recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                ChangedFieldsList(fields = fields, action = entry.action)
            }
        }
        entry.reason?.let { reason ->
            Text(
                text = "Reason: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        AuditLogEntryActions(
            entry = entry,
            affordances = affordances,
        )
        if (affordances.ackError != null) {
            InlineErrorText(text = affordances.ackError)
        }
    }
}

@Composable
private fun AuditLogEntryHeader(
    entry: AuditLogEntryResponse,
    tableLabel: String,
    expanded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ActionPill(action = entry.action)
        Text(
            text = tableLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.isFlagged) {
            FlagBadge()
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = entry.changedByName ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = formatRelativeTimestamp(entry.changedAt, logTag = "AuditLogScreen"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
        )
    }
}

// D11 — shared row in commonMain; the platform list actuals diverge only in row chrome (desktop
// dense rows / mobile cards, #95 smallest-divergent-subtree). Renders: action pill + table label
// + flag badge + changedByName + timestamp (D7); expanded → changed-fields diff (D3), reason
// line when present, Acknowledge (D2 — hidden on the caller's own flagged row; the server's 409
// stays as the authoritative backstop with an inline error) + "Full history for this record"
// (D8).
@Composable
internal fun AuditLogEntryRow(
    entry: AuditLogEntryResponse,
    display: AuditLogRowDisplay,
    affordances: AuditLogRowAffordances,
    modifier: Modifier = Modifier,
) {
    val canAcknowledge = canAcknowledgeEntry(affordances.showAcknowledge, entry, affordances.currentUserId)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = display.onToggleExpanded)
                .rowHover()
                .padding(vertical = Spacing.xs),
    ) {
        AuditLogEntryHeader(entry = entry, tableLabel = display.tableLabel, expanded = display.expanded)

        // #383 — branch context is always visible on the collapsed row: the human branch name
        // when the row has one, an explicit marker otherwise (never a raw UUID or blank).
        Text(
            text = auditBranchDisplayName(entry.branchName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )

        if (display.expanded) {
            AuditLogExpandedDetails(
                entry = entry,
                affordances = affordances,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun AuditLogEntryActions(
    entry: AuditLogEntryResponse,
    affordances: AuditLogRowAffordances,
) {
    val canAcknowledge = canAcknowledgeEntry(affordances.showAcknowledge, entry, affordances.currentUserId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (canAcknowledge) {
            TextButton(
                onClick = affordances.onAcknowledge,
                enabled = !affordances.acknowledging,
            ) {
                Text(if (affordances.acknowledging) "Acknowledging…" else "Acknowledge")
            }
        }
        if (affordances.showFullHistory) {
            TextButton(onClick = affordances.onFullHistory) {
                Text("Full history for this record")
            }
        }
        if (affordances.onOpenClientRecord != null && canOpenClientRecord(entry)) {
            TextButton(onClick = affordances.onOpenClientRecord) {
                Text("Open client record")
            }
        }
    }
}

// D2 — the Acknowledge affordance: hidden on the caller's own flagged rows (self-ack is
// server-409'd), and on already-acknowledged rows (the backend leaves `isFlagged` true and sets
// `acknowledgedAt` — offering Acknowledge there would 404 on tap for other reviewers / fresh
// VMs). Extracted pure so the rule is test-pinned.
internal fun canAcknowledgeEntry(
    showAcknowledge: Boolean,
    entry: AuditLogEntryResponse,
    currentUserId: String?,
): Boolean = showAcknowledge && entry.isFlagged && entry.acknowledgedAt == null && entry.changedBy != currentUserId

// #390 — the client-record jump target: client audit rows carry tableName = "client" (backend
// ClientTable.tableName) with recordId = client.id (ClientAudit.inserted/updated), so
// Route.ClientDetail resolves directly. Extracted pure so the rule is test-pinned like
// [canAcknowledgeEntry]. The caller-side GLOBAL EDIT_BRANCH_DATA gate lives at the NavHost
// call sites; this predicate only answers "is this row a client record?".
internal const val AUDIT_TABLE_CLIENT = "client"

internal fun canOpenClientRecord(entry: AuditLogEntryResponse): Boolean = entry.tableName == AUDIT_TABLE_CLIENT

// #383 — rows without branch context (global rows: products, clients, users, credentials)
// render an explicit marker — never a raw UUID or blank.
internal const val AUDIT_NO_BRANCH_MARKER = "No branch"

internal fun auditBranchDisplayName(branchName: String?): String =
    branchName?.takeIf { it.isNotBlank() } ?: AUDIT_NO_BRANCH_MARKER

// D11 — the shared row-affordance bundle across the list actuals and all three list call sites
// (For-review, All-activity, history). A holder keeps the expect/actual signatures under
// detekt's LongParameterList threshold (6) and dissolves the repeated 10-param clump (pass-6
// HARD — the bare 11-param actuals failed :composeApp:detekt*).
internal data class AuditLogEntryListArgs(
    val entries: List<AuditLogEntryResponse>,
    val tableLabels: Map<String, String>,
    val expandedIds: Set<String>,
    val onToggleExpanded: (String) -> Unit,
    val currentUserId: String?,
    val onAcknowledge: (AuditLogEntryResponse) -> Unit,
    val acknowledgingIds: Set<String>,
    val ackErrors: Map<String, String>,
    val onFullHistory: (AuditLogEntryResponse) -> Unit,
    val showAcknowledge: Boolean,
    val showFullHistory: Boolean,
    // #390 — non-null only when the caller holds GLOBAL EDIT_BRANCH_DATA; the list actuals
    // additionally filter per-row via canOpenClientRecord. Default null keeps the D8 history
    // screen drill-down-free without touching its call site.
    val onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)? = null,
)

// D11 — desktop dense rows / mobile cards (#95 smallest-divergent-subtree); the row itself is
// the shared [AuditLogEntryRow]. The modifier lets the All-activity tab weight the list so the
// pinned Load-more affordances below it stay visible (D5; pass-7 HARD — the pre-fix
// fillMaxSize consumed the weighted column's full height and clipped the button).
@Composable
internal fun MobileAuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(args.entries, key = { it.id }) { entry ->
            // #390 — resolve per-row: affordance renders only for client-table rows when the
            // caller holds the backend's client-read scope.
            val onOpenRecord = args.onOpenClientRecord
            val openClientRecord =
                if (onOpenRecord != null && canOpenClientRecord(entry)) {
                    { onOpenRecord(entry) }
                } else {
                    null
                }
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuditLogEntryRow(
                    entry = entry,
                    display =
                        AuditLogRowDisplay(
                            tableLabel = args.tableLabels[entry.tableName] ?: entry.tableName,
                            expanded = entry.id in args.expandedIds,
                            onToggleExpanded = { args.onToggleExpanded(entry.id) },
                        ),
                    affordances =
                        AuditLogRowAffordances(
                            currentUserId = args.currentUserId,
                            onAcknowledge = { args.onAcknowledge(entry) },
                            acknowledging = entry.id in args.acknowledgingIds,
                            ackError = args.ackErrors[entry.id],
                            onFullHistory = { args.onFullHistory(entry) },
                            onOpenClientRecord = openClientRecord,
                            showAcknowledge = args.showAcknowledge,
                            showFullHistory = args.showFullHistory,
                        ),
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}

@Composable
internal fun RefreshErrorLine(refreshError: String?) {
    if (refreshError != null) {
        InlineErrorText(text = refreshError, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
internal fun InlineErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}
