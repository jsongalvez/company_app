package com.companyb.companyapp.ui.screen

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.SessionDetailViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * SessionDetail route on both platforms (#152 — the #151 resolution).
 *
 * The dashboard path passes the enriched row via nav args — the VM seeds Success with it and
 * never dispatches (zero extra requests). The Notifications call site navigates with sessionId
 * only (row = null) — the VM fetches once via `GET /api/sessions/{sessionId}` (bearer-only
 * gate; 404 for both non-bearer and missing → the single Error + Retry fallback below).
 * Content is the same [SessionDetailContent] the desktop inline pane renders — byte-identical
 * rendering with the dashboard path.
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
) {
    val detailState by viewModel.detail.collectAsState()

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
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(
                                text = state.message,
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

            is UiState.Success -> {
                SessionDetailContent(session = state.data)
            }
        }
    }
}
