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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.SessionCreateViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #348 — start a client's session end-to-end. No client chosen: the debounced picker (the
 * ClientsScreen D2 shape, entry-scoped) with an on-the-spot [ClientCreateDialog] when the
 * search comes up empty. Client chosen: the server-previewed session type + defaulted base
 * price (never replicated client-side), concern multi-select, optional other-concerns and
 * remarks, and the walk-in submit. Success navigates to the new session's detail via
 * [onSessionCreated] — concern-add failures do NOT block navigation (the detail shows server
 * truth); they are logged here.
 */
@Composable
fun SessionCreateScreen(
    viewModel: SessionCreateViewModel,
    clientViewModel: ClientViewModel,
    branchName: String?,
    onBack: () -> Unit,
    onSessionCreated: (String) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchResults.collectAsState()
    val cachedResults by viewModel.freshestResults.collectAsState()
    val selectedClient by viewModel.selectedClient.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val concernsState by viewModel.concerns.collectAsState()
    val selectedConcernIds by viewModel.selectedConcernIds.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    val concernAddFailures by viewModel.concernAddFailures.collectAsState()
    // The dialog's create path lives in the caller-provided ClientViewModel (entry-scoped).
    val createState by clientViewModel.createClientResult.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var priceText by remember { mutableStateOf("") }
    var otherConcerns by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        logInfo("SessionCreateScreen", "composable entered (first composition)")
        // Retry-loop lesson: Idle-only load fires once per entry; Error gets a manual retry.
        viewModel.loadConcerns()
    }

    // Created-on-the-spot client: select it (drives the preview load) and close the dialog —
    // the create-user shape's "Success closes through the caller's UiState effect".
    LaunchedEffect(createState) {
        (createState as? UiState.Success)?.data?.let { created ->
            viewModel.selectClient(created)
            showCreateDialog = false
        }
    }

    // Price defaults from the preview's base rate while the field is blank (untouched);
    // an explicit user value always wins.
    val previewData = (preview as? UiState.Success)?.data
    LaunchedEffect(previewData) {
        val basePrice = previewData?.basePrice
        if (basePrice != null && priceText.isBlank()) priceText = basePrice
    }

    LaunchedEffect(createResult) {
        when (val state = createResult) {
            is UiState.Success -> {
                if (concernAddFailures > 0) {
                    logWarn(
                        "SessionCreateScreen",
                        "session ${state.data.id} created; $concernAddFailures concern add(s) failed",
                    )
                }
                onSessionCreated(state.data.id)
            }

            is UiState.Error -> {
                logWarn("SessionCreateScreen", "createResult=Error: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }
        if (branchName != null) {
            Text(
                text = branchName,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }

        val client = selectedClient
        if (client == null) {
            ClientPickerSection(
                viewModel = viewModel,
                query = query,
                searchState = searchState,
                cachedResults = cachedResults,
                onCreateNewClick = { showCreateDialog = true },
            )
        } else {
            SessionFormSection(
                viewModel = viewModel,
                client = client,
                preview = preview,
                concernsState = concernsState,
                selectedConcernIds = selectedConcernIds,
                createResult = createResult,
                priceText = priceText,
                onPriceChange = { priceText = it },
                otherConcerns = otherConcerns,
                onOtherConcernsChange = { otherConcerns = it },
                remarks = remarks,
                onRemarksChange = { remarks = it },
                onChangeClient = {
                    priceText = ""
                    viewModel.clearSelectedClient()
                },
            )
        }
    }

    if (showCreateDialog) {
        ClientCreateDialog(
            createState = createState,
            onCreate = clientViewModel::createClient,
            onDismiss = {
                if (createState !is UiState.Loading) showCreateDialog = false
            },
        )
    }
}

/** The find-or-create client half of the flow (no selection yet). */
@Composable
private fun ClientPickerSection(
    viewModel: SessionCreateViewModel,
    query: String,
    searchState: UiState<List<ClientResponse>>,
    cachedResults: List<ClientResponse>?,
    onCreateNewClick: () -> Unit,
) {
    val isLoading = searchState is UiState.Loading
    val errorMessage = (searchState as? UiState.Error)?.message

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

/** The form half: previewed type + price, concerns, optional notes, submit. */
@Composable
private fun SessionFormSection(
    viewModel: SessionCreateViewModel,
    client: ClientResponse,
    preview: UiState<SessionPreviewResponse>,
    concernsState: UiState<List<ConcernResponse>>,
    selectedConcernIds: Set<String>,
    createResult: UiState<SessionResponse>,
    priceText: String,
    onPriceChange: (String) -> Unit,
    otherConcerns: String,
    onOtherConcernsChange: (String) -> Unit,
    remarks: String,
    onRemarksChange: (String) -> Unit,
    onChangeClient: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = listOfNotNull(client.firstName, client.lastName).joinToString(" "),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChangeClient) {
            Text("Change")
        }
    }

    when (val p = preview) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            ErrorCard(message = p.message, onRetry = { viewModel.retryPreview() })
        }

        is UiState.Success -> {
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        text = "Session type",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSubtle,
                    )
                    Text(
                        text = p.data.sessionType.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Base rate ₱${p.data.basePrice} — final price defaults to it",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
            }
        }
    }

    OutlinedTextField(
        value = priceText,
        onValueChange = onPriceChange,
        label = { Text("Final price (₱)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    when (val c = concernsState) {
        is UiState.Idle -> {
            Unit
        }

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.xs), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = c.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                TextButton(onClick = { viewModel.retryConcerns() }) {
                    Text("Retry")
                }
            }
        }

        is UiState.Success -> {
            Text(
                text = "Concerns",
                style = MaterialTheme.typography.labelSmall,
                color = InkSubtle,
            )
            c.data.forEach { concern ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = concern.id in selectedConcernIds,
                        onCheckedChange = { viewModel.toggleConcern(concern.id) },
                    )
                    Text(
                        text = concern.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    OutlinedTextField(
        value = otherConcerns,
        onValueChange = onOtherConcernsChange,
        label = { Text("Other concerns (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = remarks,
        onValueChange = onRemarksChange,
        label = { Text("Remarks (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    val priceValue = priceText.trim().toDoubleOrNull()
    val canSubmit =
        preview is UiState.Success && priceValue != null && priceValue >= 0 &&
            createResult !is UiState.Loading
    Button(
        onClick = { viewModel.createSession(priceText.trim(), remarks, otherConcerns) },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (createResult is UiState.Loading) "Starting…" else "Start session")
    }
    (createResult as? UiState.Error)?.let { state ->
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
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
