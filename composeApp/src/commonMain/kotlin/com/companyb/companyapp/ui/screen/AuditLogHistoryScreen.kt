package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AuditLogViewModel

// #479 — the pushed per-record history screen (#104 D8), extracted from AuditLogScreen.kt so
// the file-function wall (TMF) stays honest: entry-scoped VM load effect, breadcrumb header,
// and the drill-down-free entry list (no acknowledge, no further history).

@Composable
private fun AuditLogHistoryLoadEffect(
    viewModel: AuditLogViewModel,
    tableName: String,
    recordId: String,
) {
    LaunchedEffect(Unit) {
        logInfo("AuditLogHistoryScreen", "composable entered (first composition)")
        // Load once per VM lifetime (Idle), re-fire from an error state (auto-retry — the
        // same policy as the main screen's loads); a rotation/re-entry refire must not wipe
        // the loaded history back to a spinner (pass-5 SOFT).
        if (viewModel.history.value is UiState.Idle || viewModel.history.value is UiState.Error) {
            viewModel.loadHistory(tableName, recordId)
        }
    }
}

@Composable
private fun AuditLogHistoryHeader(
    onBack: () -> Unit,
    tableName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }
        Text(
            text = "History — $tableName",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

// D8 — "Full history for this record": pushed on both platforms (#91 push-route lock; ClientDetail
// precedent — content-level Back TextButton, the pushed-route topbar pattern stays fog). The route
// gets its own entry-scoped VM (fresh entry self-cleans, #112 pattern).
@Composable
fun AuditLogHistoryScreen(
    viewModel: AuditLogViewModel,
    tableName: String,
    recordId: String,
    currentUserId: String?,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsState()
    val acknowledgingIds by viewModel.acknowledgingIds.collectAsState()
    val ackErrors by viewModel.ackErrors.collectAsState()
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    AuditLogHistoryLoadEffect(viewModel, tableName, recordId)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        AuditLogHistoryHeader(onBack = onBack, tableName = tableName)
        when (val state = history) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                logWarn("AuditLogHistoryScreen", "historyState=Error: ${state.message}")
                ErrorCard(
                    message = state.message,
                    onRetry = { viewModel.loadHistory(tableName, recordId) },
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState("No history recorded for this record")
                } else {
                    AuditLogEntryList(
                        args =
                            AuditLogEntryListArgs(
                                entries = state.data,
                                tableLabels = emptyMap(),
                                expandedIds = expandedIds,
                                onToggleExpanded = { id ->
                                    expandedIds =
                                        if (id in expandedIds) expandedIds - id else expandedIds + id
                                },
                                currentUserId = currentUserId,
                                onAcknowledge = viewModel::acknowledge,
                                acknowledgingIds = acknowledgingIds,
                                ackErrors = ackErrors,
                                onFullHistory = {},
                                // D8 — the per-record screen is the record's trail + breadcrumb back
                                // only: no acknowledge affordance, no further drill-down (prototype D8).
                                showAcknowledge = false,
                                showFullHistory = false,
                            ),
                    )
                }
            }
        }
    }
}
