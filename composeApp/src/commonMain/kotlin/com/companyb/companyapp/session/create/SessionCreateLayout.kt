@file:Suppress("MatchingDeclarationName") // #594 multi-decl layout owner, stays cohesive

package com.companyb.companyapp.session.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientPickerArgs
import com.companyb.companyapp.client.ClientPickerSection
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.theme.Spacing

internal data class SessionCreateBodyArgs(
    val viewModel: SessionCreateFormApi,
    val selectedClient: ClientResponse?,
    val query: String,
    val searchState: UiState<List<ClientResponse>>,
    val cachedResults: List<ClientResponse>?,
    val draft: SessionCreateDraft,
    val isSubmissionLocked: Boolean,
    val onClientProfileClick: (String) -> Unit,
    val onSubmissionStarted: () -> Unit,
    val onContinueAfterConcernFailure: () -> Unit,
)

/** Only the client-picker/form arrangement diverges between mobile and desktop. */
@Composable
internal expect fun SessionCreateBodyLayout(
    args: SessionCreateBodyArgs,
    onCreateNewClick: () -> Unit,
    modifier: Modifier,
)

/** Mobile keeps existing picker/form content; desktop replaces only this body arrangement. */
@Composable
internal fun SessionCreateMobileBody(
    args: SessionCreateBodyArgs,
    onCreateNewClick: () -> Unit,
    modifier: Modifier,
) {
    val client = args.selectedClient
    if (client == null) {
        ClientPickerSection(
            args =
                ClientPickerArgs(
                    viewModel = args.viewModel,
                    query = args.query,
                    searchState = args.searchState,
                    cachedResults = args.cachedResults,
                    selectionEnabled = !args.isSubmissionLocked,
                ),
            onCreateNewClick = onCreateNewClick,
            searchModifier = modifier,
        )
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // #620 — sole caller of the deleted SessionFormSection: the mobile form
            // header (client name + Change) lives with its exempt mobile body; the
            // shared SessionFormFields below stays (desktop workspace uses it too).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = listOfNotNull(client.firstName, client.lastName).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = args.viewModel::clearSelectedClient, enabled = !args.isSubmissionLocked) {
                    Text("Change")
                }
            }
            SessionFormFields(
                viewModel = args.viewModel,
                draft = args.draft,
                isSubmissionLocked = args.isSubmissionLocked,
                onSubmissionStarted = args.onSubmissionStarted,
                onContinueAfterConcernFailure = args.onContinueAfterConcernFailure,
            )
        }
    }
}

@Composable
internal expect fun SessionCreateBackHandler(enabled: Boolean)
