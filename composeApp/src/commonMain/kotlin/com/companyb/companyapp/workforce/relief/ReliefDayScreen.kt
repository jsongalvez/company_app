package com.companyb.companyapp.workforce.relief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

        // #680 — the day's requests and invites render through the shared planning owner
        // (same Expired rule, status text, empty states, per-leg Retry as the planning
        // tab) with no actions — the deep link selects the exact branch/day read-only.
        // #399 — this panel IS one branch day; past-operational-date PENDING rows
        // render Expired (resolved statuses keep their raw enum text).
        val dayPast = parseInviteDate(date)?.let { it < currentOperationalDate() } == true
        PlanningDayRequests(
            state = requestsState,
            dayPast = dayPast,
            onRetry = viewModel::reloadRequests,
        )

        // #401 — the day's invites under the requests: invite-sourced taps (accepted /
        // declined / revoked / reminder) render the tapped entity's true state instead of
        // dying as an empty day. Own leg — a failure here never touches the requests above.
        PlanningDayInvites(
            state = invitesState,
            onRetry = viewModel::reloadInvites,
        )
    }
}
