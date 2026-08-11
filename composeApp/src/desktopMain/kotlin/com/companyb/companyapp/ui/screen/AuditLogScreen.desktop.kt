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
internal actual fun AuditLogEntryList(state: AuditLogListState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            state.entries.forEach { entry ->
                AuditLogEntryRow(
                    entry = entry,
                    tableLabel = state.tableLabels[entry.tableName] ?: entry.tableName,
                    expanded = entry.id in state.expandedIds,
                    onToggleExpanded = { state.onToggleExpanded(entry.id) },
                    currentUserId = state.currentUserId,
                    onAcknowledge = { state.onAcknowledge(entry) },
                    acknowledging = entry.id in state.acknowledgingIds,
                    ackError = state.ackErrors[entry.id],
                    onFullHistory = { state.onFullHistory(entry) },
                    showAcknowledge = state.showAcknowledge,
                    showFullHistory = state.showFullHistory,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}
