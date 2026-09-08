@file:Suppress("MatchingDeclarationName") // #599 multi-decl client owner, stays cohesive

package com.companyb.companyapp.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

/**
 * The find-or-create client half of the #348 session-create flow (no selection yet), split out
 * of SessionCreateScreen.kt to keep both files under the detekt file-function budget.
 * #558 — lives in the `client` owner as the single source, consumed via the client-owned
 * [ClientPickerApi] (session's `SessionClientPickerApi` extends it; never copied into session/).
 * Only the entry point is internal.
 *
 * #673 — one identity presentation (full name primary, phone secondary, age/address only
 * to disambiguate same-name rows; em dash for missing, "Anonymized client" for husks;
 * never clinical concerns or raw IDs). Search focuses on entry, keeps the existing
 * minimum-query/debounce, exposes loading without collapsing results (keep-last), applies
 * latest-query-wins behind [ClientSearcher], and disables stale rows during refresh.
 * Arrow keys move focus, Enter selects, Tab reaches explicit Create client; no default
 * selection on a response landing.
 */

internal data class ClientPickerArgs(
    val viewModel: ClientPickerApi,
    val query: String,
    val searchState: UiState<List<ClientResponse>>,
    val cachedResults: List<ClientResponse>?,
    val selectedClientId: String? = null,
    val selectionEnabled: Boolean = true,
)

@Composable
internal fun ClientPickerSection(
    args: ClientPickerArgs,
    onCreateNewClick: () -> Unit,
    searchModifier: Modifier = Modifier.fillMaxSize(),
) {
    // #673 — search focuses on entry; Tab order: field → results (Enter selects) → Create.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    OutlinedTextField(
        value = args.query,
        onValueChange = { args.viewModel.onQueryChange(it) },
        label = { Text("Search clients by name or phone") },
        singleLine = true,
        trailingIcon = {
            if (args.selectionEnabled && args.query.isNotBlank()) {
                IconButton(
                    onClick = { args.viewModel.onQueryChange("") },
                    modifier = Modifier.semantics { contentDescription = "Clear client search" },
                ) {
                    Text(
                        text = "×",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        enabled = args.selectionEnabled,
        modifier = Modifier.operationalField().focusRequester(searchFocus),
    )

    ClientSearchArea(
        args = args,
        onCreateNewClick = onCreateNewClick,
        modifier = searchModifier,
    )
}

@Composable
private fun ClientSearchArea(
    args: ClientPickerArgs,
    onCreateNewClick: () -> Unit,
    modifier: Modifier,
) {
    val searchState = args.searchState
    val cachedResults = args.cachedResults
    val isLoading = searchState is UiState.Loading
    val errorMessage = (searchState as? UiState.Error)?.message

    Box(modifier = modifier.padding(top = Spacing.sm)) {
        when {
            searchState is UiState.Idle -> {
                CenteredHint("Search clients by name or phone")
            }

            searchState is UiState.Success && cachedResults?.isEmpty() == true -> {
                ClientSearchEmpty(args.selectionEnabled, onCreateNewClick)
            }

            errorMessage != null && cachedResults.isNullOrEmpty() -> {
                ErrorCard(
                    message = errorMessage,
                    onRetry = { if (args.selectionEnabled) args.viewModel.retrySearch() },
                    retryEnabled = args.selectionEnabled,
                )
            }

            else -> {
                ClientSearchOutcome(args, cachedResults, onCreateNewClick)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun ClientSearchEmpty(
    selectionEnabled: Boolean,
    onCreateNewClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No clients found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(Spacing.sm))
        SecondaryActionButton(label = "Create new client", onClick = onCreateNewClick, enabled = selectionEnabled)
    }
}

@Composable
private fun ClientSearchOutcome(
    args: ClientPickerArgs,
    cachedResults: List<ClientResponse>?,
    onCreateNewClick: () -> Unit,
) {
    val results = cachedResults
    if (results == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // #673 — stale rows stay visible during refresh but are not selectable as if
        // they matched the new query; no default selection (focused = -1 on landing).
        val stale = args.searchState is UiState.Loading
        val rowsEnabled = args.selectionEnabled && !stale
        var focusedIndex by remember(results) { mutableStateOf(-1) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionDown -> {
                                focusedIndex = movePickerFocus(focusedIndex, 1, results.size)
                                true
                            }

                            Key.DirectionUp -> {
                                focusedIndex = movePickerFocus(focusedIndex, -1, results.size)
                                true
                            }

                            Key.Enter, Key.NumPadEnter -> {
                                val target = results.getOrNull(focusedIndex)
                                if (rowsEnabled && target != null) args.viewModel.selectClient(target)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            results.forEachIndexed { index, client ->
                SearchResultRow(
                    client = client,
                    siblings = results,
                    selected = client.id == args.selectedClientId,
                    focused = index == focusedIndex,
                    enabled = rowsEnabled,
                    onFocus = { focusedIndex = index },
                ) {
                    args.viewModel.selectClient(client)
                }
            }
            // Explicit Create stays reachable via Tab after the list (and via the
            // empty state when there are no rows).
            TertiaryActionButton(
                label = "Create new client",
                onClick = onCreateNewClick,
                enabled = args.selectionEnabled,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    client: ClientResponse,
    siblings: List<ClientResponse>,
    selected: Boolean,
    focused: Boolean,
    enabled: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    // #670 — focus uses the shared 2dp PrimaryHover ring (distinct from hover/selection);
    // selection keeps the fill wash. No hand-rolled border variant.
    var rowModifier: Modifier = Modifier.fillMaxWidth()
    if (focused) rowModifier = rowModifier.operationalFocusRing()
    Surface(
        onClick = {
            onFocus()
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        modifier = rowModifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(
                text = clientPrimaryName(client),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = clientSecondaryLine(client, siblings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
