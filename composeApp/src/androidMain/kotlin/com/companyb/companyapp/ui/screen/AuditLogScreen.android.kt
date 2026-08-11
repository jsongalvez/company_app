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
internal actual fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(args.entries, key = { it.id }) { entry ->
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuditLogEntryRow(
                    entry = entry,
                    tableLabel = args.tableLabels[entry.tableName] ?: entry.tableName,
                    expanded = entry.id in args.expandedIds,
                    onToggleExpanded = { args.onToggleExpanded(entry.id) },
                    currentUserId = args.currentUserId,
                    onAcknowledge = { args.onAcknowledge(entry) },
                    acknowledging = entry.id in args.acknowledgingIds,
                    ackError = args.ackErrors[entry.id],
                    onFullHistory = { args.onFullHistory(entry) },
                    showAcknowledge = args.showAcknowledge,
                    showFullHistory = args.showFullHistory,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}
