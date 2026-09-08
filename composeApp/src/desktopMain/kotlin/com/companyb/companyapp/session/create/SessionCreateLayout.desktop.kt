package com.companyb.companyapp.session.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.client.ClientPickerArgs
import com.companyb.companyapp.client.ClientPickerSection
import com.companyb.companyapp.client.clientPhoneLine
import com.companyb.companyapp.client.clientPrimaryName
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

private const val CLIENT_DIRECTORY_WIDTH = 320

/** Desktop Variant C: persistent client directory beside the in-progress session workspace. */
@Composable
internal actual fun SessionCreateBodyLayout(
    args: SessionCreateBodyArgs,
    onCreateNewClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ClientDirectoryPane(args, onCreateNewClick)
        SessionWorkspacePane(
            args = args,
            onClientProfileClick = args.onClientProfileClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClientDirectoryPane(
    args: SessionCreateBodyArgs,
    onCreateNewClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(CLIENT_DIRECTORY_WIDTH.dp),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
            Text(
                text = "Clients",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Find client, then complete session without leaving this list",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            ClientPickerSection(
                args =
                    ClientPickerArgs(
                        viewModel = args.viewModel,
                        query = args.query,
                        searchState = args.searchState,
                        cachedResults = args.cachedResults,
                        selectedClientId = args.selectedClient?.id,
                        selectionEnabled = !args.isSubmissionLocked,
                    ),
                onCreateNewClick = onCreateNewClick,
                searchModifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
internal actual fun SessionCreateBackHandler(enabled: Boolean) {
    if (!enabled) return
}

@Composable
private fun SessionWorkspacePane(
    args: SessionCreateBodyArgs,
    onClientProfileClick: (String) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.background,
    ) {
        val client = args.selectedClient
        if (client == null) {
            EmptySessionWorkspace()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                DesktopSelectedClientSummary(
                    client = client,
                    onOpenProfile = { onClientProfileClick(client.id) },
                    onChangeClient = args.viewModel::clearSelectedClient,
                    enabled = !args.isSubmissionLocked,
                )
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
}

@Composable
private fun EmptySessionWorkspace() {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Choose a client",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Client details and session fields will stay here while you work.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun DesktopSelectedClientSummary(
    client: ClientResponse,
    onOpenProfile: () -> Unit,
    onChangeClient: () -> Unit,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onOpenProfile,
            enabled = enabled,
            shape = RoundedCornerShape(CornerRadius.lg),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // #673 — one identity presentation; missing phone reads as an
                        // em dash, never clinical concerns or raw IDs.
                        text = clientPrimaryName(client),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = clientPhoneLine(client),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                    Text(
                        text = "Total sessions: ${client.sessionCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                    Text(
                        text = "Open profile",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        TextButton(
            onClick = onChangeClient,
            enabled = enabled,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Change client")
        }
    }
}
