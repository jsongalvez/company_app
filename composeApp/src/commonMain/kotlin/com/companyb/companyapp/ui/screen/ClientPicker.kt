package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.SessionCreateViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * The find-or-create client half of the #348 session-create flow (no selection yet), split out
 * of SessionCreateScreen.kt to keep both files under the detekt file-function budget. Same
 * package; only the entry point is internal.
 */

@Composable
internal fun ClientPickerSection(
    viewModel: SessionCreateViewModel,
    query: String,
    searchState: UiState<List<ClientResponse>>,
    cachedResults: List<ClientResponse>?,
    onCreateNewClick: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = { viewModel.onQueryChange(it) },
        label = { Text("Search clients by name or phone") },
        singleLine = true,
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
        modifier = Modifier.fillMaxWidth(),
    )

    ClientSearchArea(
        viewModel = viewModel,
        searchState = searchState,
        cachedResults = cachedResults,
        onCreateNewClick = onCreateNewClick,
    )
}

@Composable
private fun ClientSearchArea(
    viewModel: SessionCreateViewModel,
    searchState: UiState<List<ClientResponse>>,
    cachedResults: List<ClientResponse>?,
    onCreateNewClick: () -> Unit,
) {
    val isLoading = searchState is UiState.Loading
    val errorMessage = (searchState as? UiState.Error)?.message

    Box(modifier = Modifier.fillMaxSize().padding(top = Spacing.sm)) {
        when {
            searchState is UiState.Idle -> {
                CenteredHint("Search clients by name or phone")
            }

            searchState is UiState.Success && cachedResults?.isEmpty() == true -> {
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
                    Button(onClick = onCreateNewClick) {
                        Text("Create new client")
                    }
                }
            }

            errorMessage != null && cachedResults.isNullOrEmpty() -> {
                ErrorCard(
                    message = errorMessage,
                    onRetry = { viewModel.retrySearch() },
                )
            }

            else -> {
                val results = cachedResults
                if (results == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        results.forEach { client ->
                            SearchResultRow(client) { viewModel.selectClient(client) }
                        }
                    }
                }
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
private fun SearchResultRow(
    client: ClientResponse,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
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
