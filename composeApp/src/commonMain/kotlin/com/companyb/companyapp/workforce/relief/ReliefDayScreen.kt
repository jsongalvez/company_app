package com.companyb.companyapp.workforce.relief

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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.relief.ReliefDayViewModel

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
    date: String,
    modifier: Modifier = Modifier,
) {
    val requestsState by viewModel.requests.collectAsState()
    // #401 — the day's invites, the panel's auxiliary leg (own UiState, own retry).
    val invitesState by viewModel.invites.collectAsState()
    // #388 — the name travels with the VM (resolved from branch-options), not the nav
    // hosts: the deep link only ever carried the id.
    val branchName by viewModel.branchName.collectAsState()

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
                // #388 — shared error+retry card: load() is idempotent, so Retry re-issues
                // both the request list and the pending name resolution.
                ErrorCard(
                    message = state.message,
                    onRetry = viewModel::load,
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
                    // #399 — this panel IS one branch day; past-operational-date PENDING rows
                    // render Expired (resolved statuses keep their raw enum text).
                    val today = currentOperationalDate()
                    val dayPast = parseInviteDate(date)?.let { it < today } == true
                    state.data.forEach { row -> DayRequestRow(row, dayPast) }
                }
            }

            UiState.Idle -> {
                Unit
            }
        }

        // #401 — the day's invites under the requests: invite-sourced taps (accepted /
        // declined / revoked / reminder) render the tapped entity's true state instead of
        // dying as an empty day. Own leg — a failure here is an inline retry strip and the
        // request list above stays untouched.
        DayInvitesSection(
            invitesState = invitesState,
            onRetry = viewModel::load,
        )
    }
}

@Composable
private fun DayInvitesSection(
    invitesState: UiState<List<ReliefInviteResponse>>,
    onRetry: () -> Unit,
) {
    when (val state = invitesState) {
        is UiState.Error -> {
            ErrorCard(
                message = state.message,
                onRetry = onRetry,
            )
        }

        is UiState.Success -> {
            if (state.data.isNotEmpty()) {
                Text("Invites", style = MaterialTheme.typography.titleSmall)
                val today = currentOperationalDate()
                toReliefDayInviteRows(state.data, today).forEach { row -> DayInviteRow(row) }
            }
        }

        UiState.Loading,
        UiState.Idle,
        -> {
            Unit
        }
    }
}

@Composable
private fun DayInviteRow(row: ReliefDayInviteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(text = row.statusText, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
    }
}

@Composable
private fun DayRequestRow(
    row: ReliefAccessResponse,
    dayPast: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.requestedBy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        val expired = dayPast && row.requestStatus == ReliefAccessStatus.PENDING
        Text(
            text = if (expired) "Expired" else row.requestStatus.name,
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
        )
    }
}
