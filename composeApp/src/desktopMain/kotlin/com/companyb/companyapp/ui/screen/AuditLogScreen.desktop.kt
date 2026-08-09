package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.ui.theme.Spacing

// D11 (desktop) — dense rows: the shared AuditLogEntryRow with minimal chrome, hairline
// dividers, the list itself scrolls. Desktop = scan persona (same reasoning as #113 D3).
@Composable
actual fun AuditLogEntryList(
    entries: List<AuditLogEntryResponse>,
    tableLabels: Map<String, String>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    currentUserId: String?,
    onAcknowledge: (AuditLogEntryResponse) -> Unit,
    acknowledgingIds: Set<String>,
    ackErrors: Map<String, String>,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    showAcknowledge: Boolean,
    showFullHistory: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                AuditLogEntryRow(
                    entry = entry,
                    tableLabel = tableLabels[entry.tableName] ?: entry.tableName,
                    expanded = entry.id in expandedIds,
                    onToggleExpanded = { onToggleExpanded(entry.id) },
                    currentUserId = currentUserId,
                    onAcknowledge = { onAcknowledge(entry) },
                    acknowledging = entry.id in acknowledgingIds,
                    ackError = ackErrors[entry.id],
                    onFullHistory = { onFullHistory(entry) },
                    showAcknowledge = showAcknowledge,
                    showFullHistory = showFullHistory,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}
