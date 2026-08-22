package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #113 — Clients search screen (US-20), per the locked #99 D1–D10.
 *
 * D2 — debounce/guard live in the VM (testable via virtual time); the composable forwards every
 * keystroke via `onQueryChange` and renders the states. Keep-last-results while typing: any
 * Success caches locally, and Loading/Error with a cache keeps rendering the cached list (the
 * in-field spinner is the only busy signal — no list flicker, #97 Q5 axis).
 *
 * D1 — "Client anonymized" confirmation: the detail entry's VM sets [ClientState.anonymizeNotice]
 * on 204; this screen (whose entry-scoped VM survives the detail push, #112 pattern) collects the
 * notice and shows it as a snackbar, then consumes it.
 */
@Composable
fun ClientsScreen(
    viewModel: ClientViewModel,
    onClientClick: (ClientResponse) -> Unit,
) {
    val searchState by viewModel.searchResults.collectAsState()
    val lastFiredQuery by viewModel.lastFiredQuery.collectAsState()
    // #161 — VM-held query (D9-deviation fix): the field binds the VM's state so the search
    // survives a detail push → pop-back (composition remember died on re-entry, clearing the
    // query one frame after the kept list re-rendered).
    val query by viewModel.query.collectAsState()
    // Keep-last-results while typing (D2 / #97 Q5 axis; the #162 KeepLast unifier — VM-side so
    // it survives pop-back, unlike the old screen-side remember): any Success caches into the
    // VM's freshest flow, and Loading/Error with held content keeps rendering it (the in-field
    // spinner is the only busy signal — no list flicker).
    val cachedResults by viewModel.freshestResults.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // #348 — create-client dialog (the create-user shape: Success closes, dismiss blocked
    // while Loading).
    var showCreateDialog by remember { mutableStateOf(false) }
    val createState by viewModel.createClientResult.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("ClientsScreen", "composable entered (first composition)")
    }

    LaunchedEffect(createState) {
        if (createState is UiState.Success) showCreateDialog = false
    }

    // D1 — the confirmation crosses the VM boundary via ClientState; consume-before-show, and
    // collect (not keyed on the notice): consuming inside a LaunchedEffect(notice) key would
    // restart the effect (key change) and cancel showSnackbar mid-display.
    LaunchedEffect(Unit) {
        ClientState.anonymizeNotice.collect { notice ->
            if (notice != null) {
                ClientState.consumeAnonymizeNotice()
                snackbarHostState.showSnackbar(notice)
            }
        }
    }

    val isLoading = searchState is UiState.Loading
    val errorMessage = (searchState as? UiState.Error)?.message
    LaunchedEffect(errorMessage) {
        errorMessage?.let { logWarn("ClientsScreen", "searchState=Error: $it") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    label = { Text("Search clients by name or phone") },
                    singleLine = true,
                    // D2 X-clear — back to empty state. "×" (U+00D7) is used instead of a glyph
                    // icon: the project has no material-icons dependency (see #107's hamburger
                    // Canvas precedent) and U+00D7 is Latin-1-covered by Inter.
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            Text(
                                text = "×",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier =
                                    Modifier
                                        .clickable { viewModel.onQueryChange("") }
                                        .padding(Spacing.xs),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (isLoading) {
                    Spacer(Modifier.width(Spacing.sm))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                TextButton(onClick = { showCreateDialog = true }) {
                    Text("New client")
                }
            }

            ClientResultsArea(
                viewModel = viewModel,
                searchState = searchState,
                cachedResults = cachedResults,
                lastFiredQuery = lastFiredQuery,
                onClientClick = onClientClick,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showCreateDialog) {
        ClientCreateDialog(
            createState = createState,
            onCreate = viewModel::createClient,
            onDismiss = {
                if (createState !is UiState.Loading) showCreateDialog = false
            },
        )
    }
}

@Composable
private fun ClientResultsArea(
    viewModel: ClientViewModel,
    searchState: UiState<List<ClientResponse>>,
    cachedResults: List<ClientResponse>?,
    lastFiredQuery: String,
    onClientClick: (ClientResponse) -> Unit,
) {
    val errorMessage = (searchState as? UiState.Error)?.message
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = Spacing.sm),
    ) {
        when {
            // D2 empty state (never searched / query below 2 chars → VM Idle).
            searchState is UiState.Idle -> {
                CenteredHint("Search clients by name or phone")
            }

            // D2 no-results.
            searchState is UiState.Success && cachedResults?.isEmpty() == true -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No clients found for \"$lastFiredQuery\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        text = "Try a different name or phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // D2 error state. The empty-cache case is included: after a successful
            // no-results search the cache holds an empty list, and a RE-search failure
            // with nothing to keep must surface the error + retry — a blank list would
            // be a silent failure with no affordance (keep-last only applies when there
            // is content to keep).
            errorMessage != null && cachedResults.isNullOrEmpty() -> {
                ErrorCard(
                    message = errorMessage,
                    onRetry = { viewModel.retrySearch() },
                )
            }

            else -> {
                val results = cachedResults
                if (results == null) {
                    // Loading with nothing cached yet.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // keep-last-results (D2 / #97 Q5 silent-refresh axis): Loading/Error with
                    // a cache keeps rendering the last list — the in-field spinner is the
                    // only busy signal.
                    ClientResultList(
                        results = results,
                        onClientClick = onClientClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * #113 D7/D8 — route gate card. Drawer hides the Clients item without `EDIT_BRANCH_DATA` (#108
 * DrawerViewModel), so this only renders on a direct nav; the code-only gate is the #156
 * any-context check (#92 Q3), with the backend's GLOBAL gate (F5) as the authoritative
 * backstop (ADR-0007). #92's `UiState.Unauthorized` card is an unimplemented lock —
 * this minimal card is the in-place 403 surface per D8.
 */
@Composable
fun RouteGateCard(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "You don't have permission to view $label",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
