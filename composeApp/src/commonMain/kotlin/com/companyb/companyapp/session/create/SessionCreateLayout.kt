@file:Suppress("MatchingDeclarationName")

package com.companyb.companyapp.session.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.ui.screen.ClientPickerArgs
import com.companyb.companyapp.ui.screen.ClientPickerSection
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
            SessionFormSection(args)
        }
    }
}

@Composable
internal expect fun SessionCreateBackHandler(enabled: Boolean)
