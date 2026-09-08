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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

/**
 * The find-or-create client half of the #348 session-create flow (no selection yet), split out
 * of SessionCreateScreen.kt to keep both files under the detekt file-function budget.
 * #558 — lives in the `client` owner as the single source, consumed via the client-owned
 * [ClientPickerApi] (session's `SessionClientPickerApi` extends it; never copied into session/).
 * Only the entry point is internal.
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
        modifier = Modifier.fillMaxWidth(),
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
                ClientSearchOutcome(args, cachedResults)
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
        Button(onClick = onCreateNewClick, enabled = selectionEnabled) {
            Text("Create new client")
        }
    }
}

@Composable
private fun ClientSearchOutcome(
    args: ClientPickerArgs,
    cachedResults: List<ClientResponse>?,
) {
    val results = cachedResults
    if (results == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            results.forEach { client ->
                SearchResultRow(
                    client,
                    client.id == args.selectedClientId,
                    args.selectionEnabled,
                ) {
                    args.viewModel.selectClient(client)
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    client: ClientResponse,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = listOfNotNull(client.firstName, client.lastName).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(Spacing.sm),
        )
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
