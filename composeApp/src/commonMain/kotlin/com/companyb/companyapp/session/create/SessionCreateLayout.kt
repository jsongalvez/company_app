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
import androidx.compose.ui.focus.FocusRequester
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientPickerArgs
import com.companyb.companyapp.client.ClientPickerSection
import com.companyb.companyapp.client.clientPrimaryName
import com.companyb.companyapp.client.clientSecondaryLine
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
    val priceFocus: FocusRequester,
    val dateFocus: FocusRequester,
    val showValidation: Boolean,
    val onChangeClient: () -> Unit,
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
            // #673 — one identity presentation (primary + secondary, never clinical/IDs).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clientPrimaryName(client),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = clientSecondaryLine(client),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // #726 — compact Profile roundtrip: secondary verification affordance beside
                // Change; identity + intake fields stay primary. Linked push preserves the
                // entry-scoped draft; Back restores it (no discard prompt, no disk persist).
                TextButton(
                    onClick = { args.onClientProfileClick(client.id) },
                    enabled = !args.isSubmissionLocked,
                ) {
                    Text("Profile")
                }
                TextButton(onClick = args.onChangeClient, enabled = !args.isSubmissionLocked) {
                    Text("Change")
                }
            }
            SessionFormFields(
                viewModel = args.viewModel,
                draft = args.draft,
                selectedClient = client,
                isSubmissionLocked = args.isSubmissionLocked,
                priceFocus = args.priceFocus,
                dateFocus = args.dateFocus,
                showValidation = args.showValidation,
            )
        }
    }
}

@Composable
internal expect fun SessionCreateBackHandler(
    locked: Boolean,
    onBack: () -> Unit,
)
