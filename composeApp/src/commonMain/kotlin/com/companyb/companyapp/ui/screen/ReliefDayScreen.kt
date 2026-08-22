package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.ReliefDayViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #358 — the relief notification tap destination: one branch day's requests, addressed by
 * (branchId, date) exactly as the notification carries them. Rendered by the dashboard
 * route when its date argument points at a non-today day (or any day without a clock-in —
 * the today dashboard stays the clock-in-gated live surface). Loading/empty/error states
 * per the ticket acceptance; authorization is server-side row scoping (view-only relief
 * users stay view-only — there are no actions here).
 */
@Composable
fun ReliefDayScreen(
    viewModel: ReliefDayViewModel,
    branchName: String?,
    date: String,
    modifier: Modifier = Modifier,
) {
    val requestsState by viewModel.requests.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Relief duty", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "${branchName ?: "Branch"} — $date",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )

        when (val state = requestsState) {
            is UiState.Loading -> {
                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }

            is UiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        "No relief activity on this day",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                } else {
                    state.data.forEach { row -> DayRequestRow(row) }
                }
            }

            UiState.Idle -> {
                Unit
            }
        }
    }
}

@Composable
private fun DayRequestRow(row: ReliefAccessResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.requestedBy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(text = row.requestStatus.name, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
    }
}
