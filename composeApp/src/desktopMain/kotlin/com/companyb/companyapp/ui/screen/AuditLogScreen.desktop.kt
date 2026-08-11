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
internal actual fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            args.entries.forEach { entry ->
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
