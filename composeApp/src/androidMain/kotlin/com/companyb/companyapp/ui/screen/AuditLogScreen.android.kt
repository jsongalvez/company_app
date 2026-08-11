package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

// D11 (mobile) — card list (#113 D3 card precedent): each entry in a surfaceVariant card; the
// shared AuditLogEntryRow handles the expandable content inside.
@Composable
internal actual fun AuditLogEntryList(state: AuditLogListState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
