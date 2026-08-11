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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(entries, key = { it.id }) { entry ->
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
