package com.companyb.companyapp.ui.screen

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateClientRequest
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
    // The dialog's create path lives in the caller-provided ClientViewModel (entry-scoped).
    val createState by clientViewModel.createClientResult.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    val form = remember { SessionFormState() }

    SessionCreateEffects(
        viewModel = viewModel,
        createState = createState,
        form = form,
        onClientCreated = { showCreateDialog = false },
        onSessionCreated = onSessionCreated,
    )

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
                form = form,
                onChangeClient = {
                    form.price = ""
                    viewModel.clearSelectedClient()
                },
            )
        }
    }

    CreateClientDialogHost(showCreateDialog, createState, clientViewModel::createClient) {
        showCreateDialog = false
    }
}

/** The on-the-spot create-client dialog host (#348): dismiss stays blocked mid-flight. */
@Composable
private fun CreateClientDialogHost(
    showDialog: Boolean,
    createState: UiState<ClientResponse>,
    onCreate: (CreateClientRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!showDialog) return
    ClientCreateDialog(
        createState = createState,
        onCreate = onCreate,
        onDismiss = { if (createState !is UiState.Loading) onDismiss() },
    )
}

/** The form's text fields; one holder so the section reads/writes them through a single param. */
private class SessionFormState {
    var price by mutableStateOf("")
    var otherConcerns by mutableStateOf("")
    var remarks by mutableStateOf("")
}

/**
 * Entry + landing effects for the create flow: the Idle-only concern load, the created-client
 * select-and-close, the preview-driven price default (blank field only), and success navigation.
 */
@Composable
private fun SessionCreateEffects(
    viewModel: SessionCreateViewModel,
    createState: UiState<ClientResponse>,
    form: SessionFormState,
    onClientCreated: () -> Unit,
    onSessionCreated: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        logInfo("SessionCreateScreen", "composable entered (first composition)")
        // Retry-loop lesson: Idle-only load fires once per entry; Error gets a manual retry.
        viewModel.loadConcerns()
        viewModel.loadMembers()
    }

    // Created-on-the-spot client: select it (drives the preview load) and close the dialog —
    // the create-user shape's "Success closes through the caller's UiState effect".
    LaunchedEffect(createState) {
        (createState as? UiState.Success)?.data?.let { created ->
            viewModel.selectClient(created)
            onClientCreated()
        }
    }

    // Price defaults from the preview's base rate while the field is blank (untouched);
    // an explicit user value always wins.
    val previewData = (viewModel.preview.collectAsState().value as? UiState.Success)?.data
    LaunchedEffect(previewData) {
        val basePrice = previewData?.basePrice
        if (basePrice != null && form.price.isBlank()) form.price = basePrice
    }

    val createResult by viewModel.createResult.collectAsState()
    val concernAddFailures by viewModel.concernAddFailures.collectAsState()
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
}

/** The form half: previewed type + price, concerns, optional notes, submit. */
@Composable
private fun SessionFormSection(
    viewModel: SessionCreateViewModel,
    client: ClientResponse,
    form: SessionFormState,
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

    val preview by viewModel.preview.collectAsState()
    PreviewCard(viewModel, preview)

    OutlinedTextField(
        value = form.price,
        onValueChange = { form.price = it },
        label = { Text("Final price (₱)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    val concernsState by viewModel.concerns.collectAsState()
    val selectedConcernIds by viewModel.selectedConcernIds.collectAsState()
    ConcernsBlock(viewModel, concernsState, selectedConcernIds)

    val membersState by viewModel.members.collectAsState()
    val selectedPractitioner by viewModel.selectedPractitioner.collectAsState()
    RequestedPractitionerPicker(
        membersState = membersState,
        selected = selectedPractitioner,
        onSelect = viewModel::selectPractitioner,
        onRetry = viewModel::retryMembers,
    )

    OutlinedTextField(
        value = form.otherConcerns,
        onValueChange = { form.otherConcerns = it },
        label = { Text("Other concerns (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.remarks,
        onValueChange = { form.remarks = it },
        label = { Text("Remarks (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    SubmitArea(viewModel, form)
}

@Composable
private fun PreviewCard(
    viewModel: SessionCreateViewModel,
    preview: UiState<SessionPreviewResponse>,
) {
    when (preview) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            ErrorCard(message = preview.message, onRetry = { viewModel.retryPreview() })
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
                        text = preview.data.sessionType.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Base rate ₱${preview.data.basePrice} — final price defaults to it",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
            }
        }
    }
}

/**
 * #366 — the optional requested-practitioner picker: own-branch ACTIVE members by display
 * name (no username), "None" default — recording who the client asked for is a preference,
 * never a requirement (BR §Clients).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestedPractitionerPicker(
    membersState: UiState<List<BranchMemberResponse>>,
    selected: BranchMemberResponse?,
    onSelect: (BranchMemberResponse?) -> Unit,
    onRetry: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = (membersState as? UiState.Success)?.data.orEmpty()
    val isError = membersState is UiState.Error
    val isLoading = membersState is UiState.Loading || membersState is UiState.Idle

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (isError) {
                onRetry()
            } else if (!isLoading) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value =
                when {
                    isError -> "Colleagues unavailable — tap to retry"
                    isLoading -> "Loading colleagues…"
                    else -> selected?.displayName ?: "None"
                },
            onValueChange = {},
            readOnly = true,
            label = { Text("Requested practitioner (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = !isLoading && !isError,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = {
                        onSelect(member)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConcernsBlock(
    viewModel: SessionCreateViewModel,
    concernsState: UiState<List<ConcernResponse>>,
    selectedConcernIds: Set<String>,
) {
    when (concernsState) {
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
                    text = concernsState.message,
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
            concernsState.data.forEach { concern ->
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
}

@Composable
private fun SubmitArea(
    viewModel: SessionCreateViewModel,
    form: SessionFormState,
) {
    val preview by viewModel.preview.collectAsState()
    val createResult by viewModel.createResult.collectAsState()

    val priceValue = form.price.trim().toDoubleOrNull()
    val canSubmit =
        preview is UiState.Success && priceValue != null && priceValue >= 0 &&
            createResult !is UiState.Loading
    Button(
        onClick = { viewModel.createSession(form.price.trim(), form.remarks, form.otherConcerns) },
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
