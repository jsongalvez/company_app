package com.companyb.companyapp.session.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn

/**
 * SessionDetail route on both platforms (#152 — the #151 resolution).
 *
 * The dashboard path passes the enriched row via nav args — the VM seeds Success with it and
 * never dispatches (zero extra requests). The Notifications call site navigates with sessionId
 * only (row = null) — the VM fetches once via `GET /api/sessions/{sessionId}` (bearer-only
 * gate; 404 for both non-bearer and missing → the single Error + Retry fallback below).
 *
 * #382 — the editable content lives in [SessionDetailPane] (shared byte-identical with the
 * desktop inline pane); this screen keeps the pushed-route chrome: Back, load/error fallbacks,
 * and the authoritative detail reload [SessionDetailViewModel.refresh] that the pane calls
 * after every mutation landing. A refresh failure that lands as Error while a good row was
 * already rendered keeps that row on screen (the VM's keep-row guard covers status legs; this
 * covers transport exceptions) — a stale-but-real detail beats a dead-end error pane.
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    viewModel: SessionDetailViewModel,
    apiClient: ApiClient,
    onBack: () -> Unit,
) {
    val detailState by viewModel.detail.collectAsState()
    var lastGoodRow by remember { mutableStateOf<DashboardSessionResponse?>(null) }

    LaunchedEffect(detailState) {
        (detailState as? UiState.Success)?.let { lastGoodRow = it.data }
        (detailState as? UiState.Error)?.let {
            logWarn("SessionDetailScreen", "detailState=Error: ${it.message}")
        }
    }

    LaunchedEffect(Unit) {
        logInfo("SessionDetailScreen", "composable entered: sessionId=$sessionId")
        viewModel.loadIfNeeded()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // #113 content-level Back precedent (pushed-route topbar pattern stays fog).
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }
        when (val state = detailState) {
            // Idle is unreachable for this VM (init is Loading|Success) but keeps the when
            // exhaustive over the sealed UiState (the ClientDetailScreen pattern).
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                val fallbackRow = lastGoodRow
                if (fallbackRow != null) {
                    SessionDetailPane(
                        session = fallbackRow,
                        apiClient = apiClient,
                        refreshSession = { viewModel.refresh() },
                    )
                } else {
                    DetailErrorPane(viewModel, state.message)
                }
            }

            is UiState.Success -> {
                SessionDetailPane(
                    session = state.data,
                    apiClient = apiClient,
                    refreshSession = { viewModel.refresh() },
                )
            }
        }
    }
}

@Composable
private fun DetailErrorPane(
    viewModel: SessionDetailViewModel,
    message: String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = message,
                    color = InkSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { viewModel.retry() }) {
                    Text("Retry")
                }
            }
        }
    }
}
