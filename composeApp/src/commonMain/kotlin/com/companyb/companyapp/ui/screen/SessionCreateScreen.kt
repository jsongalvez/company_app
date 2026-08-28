@file:Suppress("TooManyFunctions")

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
import androidx.compose.runtime.DisposableEffect
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
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.SessionCreateDraft
import com.companyb.companyapp.viewmodel.SessionCreateViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.bookingFields
import kotlin.time.Clock

/**
 * #348 — start a client's session end-to-end. No client chosen: the debounced picker (the
 * ClientsScreen D2 shape, entry-scoped) with an on-the-spot [ClientCreateDialog] when the
 * search comes up empty. Client chosen: the server-previewed session type + defaulted base
 * price (never replicated client-side), concern multi-select, optional other-concerns and
 * remarks, and the walk-in submit. Success navigates to the new session's detail via
 * [onSessionCreated]. If concern adds fail, the entry stays open until retry succeeds or the
 * practitioner explicitly continues without the failed links.
 */
@Composable
@Suppress("LongParameterList")
fun SessionCreateScreen(
    viewModel: SessionCreateViewModel,
    clientViewModel: ClientViewModel,
    branchName: String?,
    onBack: () -> Unit,
    onSessionCreated: (String) -> Unit,
    onClientProfileClick: (String) -> Unit = {},
    onSubmissionLockChanged: (Boolean) -> Unit = {},
) {
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchResults.collectAsState()
    val cachedResults by viewModel.freshestResults.collectAsState()
    val selectedClient by viewModel.selectedClient.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    val isSubmissionLocked = createResult is UiState.Loading || createResult is UiState.Success
    SessionCreateNavigationGuard(isSubmissionLocked, onSubmissionLockChanged)
    // The dialog's create path lives in the caller-provided ClientViewModel (entry-scoped).
    val createState by clientViewModel.createClientResult.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var sessionNavigationStarted by remember { mutableStateOf(false) }
    val navigateToCreatedSession: (String) -> Unit = { sessionId ->
        if (!sessionNavigationStarted) {
            sessionNavigationStarted = true
            onSessionCreated(sessionId)
        }
    }
    SessionCreateEffects(
        viewModel = viewModel,
        clientViewModel = clientViewModel,
        createState = createState,
        onClientCreated = { showCreateDialog = false },
        onSessionCreated = navigateToCreatedSession,
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SessionCreateHeader(branchName, isSubmissionLocked, onBack)

        SessionCreateBodyLayout(
            args =
                SessionCreateBodyArgs(
                    viewModel = viewModel,
                    selectedClient = selectedClient,
                    query = query,
                    searchState = searchState,
                    cachedResults = cachedResults,
                    draft = draft,
                    isSubmissionLocked = isSubmissionLocked,
                    onClientProfileClick = onClientProfileClick,
                    onSubmissionStarted = { onSubmissionLockChanged(true) },
                    onContinueAfterConcernFailure = {
                        (createResult as? UiState.Success)?.data?.id?.let(navigateToCreatedSession)
                    },
                ),
            onCreateNewClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }

    CreateClientDialogHost(showCreateDialog, createState, clientViewModel::createClient) {
        showCreateDialog = false
    }
}

@Composable
private fun SessionCreateHeader(
    branchName: String?,
    isSubmissionLocked: Boolean,
    onBack: () -> Unit,
) {
    TextButton(onClick = onBack, enabled = !isSubmissionLocked) {
        Text("‹ Back")
    }
    branchName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
private fun SessionCreateNavigationGuard(
    isSubmissionLocked: Boolean,
    onSubmissionLockChanged: (Boolean) -> Unit,
) {
    LaunchedEffect(isSubmissionLocked) {
        onSubmissionLockChanged(isSubmissionLocked)
    }
    DisposableEffect(Unit) {
        onDispose { onSubmissionLockChanged(false) }
    }
    SessionCreateBackHandler(isSubmissionLocked)
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

/**
 * Entry + landing effects for the create flow: the Idle-only concern load, the created-client
 * select-and-close, the preview-driven price default (blank field only), and success navigation.
 */
@Composable
internal fun SessionCreateEffects(
    viewModel: SessionCreateViewModel,
    clientViewModel: ClientViewModel,
    createState: UiState<ClientResponse>,
    onClientCreated: () -> Unit,
    onSessionCreated: (String) -> Unit,
) {
    val createResult by viewModel.createResult.collectAsState()
    val concernAddFailures by viewModel.concernAddFailures.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("SessionCreateScreen", "composable entered (first composition)")
        // Retry-loop lesson: Idle-only load fires once per entry; Error gets a manual retry.
        viewModel.loadConcerns()
        viewModel.loadMembers()
    }

    LaunchedEffect(Unit) {
        ClientState.clientMutation.collect { mutation ->
            mutation?.let(viewModel::applyClientMutation)
        }
    }

    // Created-on-the-spot client: select it (drives the preview load) and close the dialog —
    // the create-user shape's "Success closes through the caller's UiState effect".
    LaunchedEffect(createState) {
        (createState as? UiState.Success)?.data?.let { created ->
            viewModel.includeClientInSearchResults(created)
            viewModel.selectClient(created)
            onClientCreated()
            clientViewModel.consumeCreateClientResult()
        }
    }

    // Price defaults from the preview's base rate while the field is blank (untouched);
    // an explicit user value always wins. #405 — a medical-mission visit is always free,
    // so the locked ₱0 replaces any draft the user managed to type before the preview landed.
    val previewData = (viewModel.preview.collectAsState().value as? UiState.Success)?.data
    LaunchedEffect(previewData) { viewModel.applyPreviewPrice(previewData) }

    LaunchedEffect(createResult, concernAddFailures) {
        when (val state = createResult) {
            is UiState.Success -> {
                if (concernAddFailures > 0) {
                    logWarn(
                        "SessionCreateScreen",
                        "session ${state.data.id} created; $concernAddFailures concern add(s) failed",
                    )
                } else {
                    onSessionCreated(state.data.id)
                }
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
internal fun SessionFormSection(args: SessionCreateBodyArgs) {
    val client = args.selectedClient ?: return
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

/** Shared form controls used by mobile and desktop layouts. */
@Composable
internal fun SessionFormFields(
    viewModel: SessionCreateViewModel,
    draft: SessionCreateDraft,
    isSubmissionLocked: Boolean,
    onSubmissionStarted: () -> Unit,
    onContinueAfterConcernFailure: () -> Unit,
) {
    val preview by viewModel.preview.collectAsState()
    val controlsEnabled = !isSubmissionLocked
    PreviewCard(viewModel, preview, controlsEnabled)

    // #405 — the mission price is not editable input; ₱0 is shown locked (server normalizes
    // authoritatively regardless).
    val previewData = (preview as? UiState.Success)?.data
    val missionPrice = previewData != null && missionPriceLocked(previewData.sessionType)
    OutlinedTextField(
        value = draft.finalPrice,
        onValueChange = viewModel::setFinalPrice,
        label = { Text("Final price (₱)") },
        supportingText =
            if (missionPrice) {
                { Text("Medical mission visit — always free") }
            } else {
                null
            },
        singleLine = true,
        enabled = !missionPrice && controlsEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    BookingSection(
        draft = draft,
        onBookedChange = viewModel::setBooked,
        onDateChange = viewModel::setNextAppointmentDate,
        enabled = controlsEnabled,
    )

    val concernsState by viewModel.concerns.collectAsState()
    val selectedConcernIds by viewModel.selectedConcernIds.collectAsState()
    ConcernsBlock(viewModel, concernsState, selectedConcernIds, controlsEnabled)

    val membersState by viewModel.members.collectAsState()
    val selectedPractitioner by viewModel.selectedPractitioner.collectAsState()
    RequestedPractitionerPicker(
        membersState = membersState,
        selected = selectedPractitioner,
        onSelect = viewModel::selectPractitioner,
        onRetry = viewModel::retryMembers,
        enabled = controlsEnabled,
    )

    OutlinedTextField(
        value = draft.otherConcerns,
        onValueChange = viewModel::setOtherConcerns,
        label = { Text("Other concerns (optional)") },
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.remarks,
        onValueChange = viewModel::setRemarks,
        label = { Text("Remarks (optional)") },
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )

    SubmitArea(viewModel, draft, onSubmissionStarted, onContinueAfterConcernFailure)
}

@Composable
private fun PreviewCard(
    viewModel: SessionCreateViewModel,
    preview: UiState<SessionPreviewResponse>,
    enabled: Boolean,
) {
    when (preview) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            ErrorCard(
                message = preview.message,
                onRetry = { viewModel.retryPreview() },
                retryEnabled = enabled,
            )
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
                        text =
                            if (missionPriceLocked(preview.data.sessionType)) {
                                "Medical mission visit — always free (₱0)"
                            } else {
                                "Base rate ₱${preview.data.basePrice} — final price defaults to it"
                            },
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
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = (membersState as? UiState.Success)?.data.orEmpty()
    val isError = membersState is UiState.Error
    val isLoading = membersState is UiState.Loading || membersState is UiState.Idle

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (!enabled) {
                return@ExposedDropdownMenuBox
            } else if (isError) {
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
            enabled = enabled && !isLoading && !isError,
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                enabled = enabled,
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    enabled = enabled,
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
    enabled: Boolean,
) {
    when (concernsState) {
        is UiState.Idle -> {
            Text(
                text = "Loading concerns…",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
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
                TextButton(onClick = { viewModel.retryConcerns() }, enabled = enabled) {
                    Text("Retry")
                }
            }
        }

        is UiState.Success -> {
            ConcernOptions(viewModel, concernsState.data, selectedConcernIds, enabled)
        }
    }
}

@Composable
private fun ConcernOptions(
    viewModel: SessionCreateViewModel,
    concerns: List<ConcernResponse>,
    selectedConcernIds: Set<String>,
    enabled: Boolean,
) {
    Text(
        text = "Concerns",
        style = MaterialTheme.typography.labelSmall,
        color = InkSubtle,
    )
    if (concerns.isEmpty()) {
        Text(
            text = "No common concerns available",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    } else {
        concerns.forEach { concern ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = concern.id in selectedConcernIds,
                    onCheckedChange = { viewModel.toggleConcern(concern.id) },
                    enabled = enabled,
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

@Composable
private fun SubmitArea(
    viewModel: SessionCreateViewModel,
    draft: SessionCreateDraft,
    onSubmissionStarted: () -> Unit,
    onContinueAfterConcernFailure: () -> Unit,
) {
    val preview by viewModel.preview.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    val concernAddFailures by viewModel.concernAddFailures.collectAsState()
    val concernRetryState by viewModel.concernRetryState.collectAsState()

    val priceValue = draft.finalPrice.trim().toDoubleOrNull()
    // #423 — a booked draft with an unparseable date shapes to null: submit disabled.
    val booking = bookingFields(draft.isBooked, draft.nextAppointmentDate, Clock.System.now())
    val canSubmit =
        preview is UiState.Success && priceValue != null && priceValue >= 0 &&
            booking != null && createResult !is UiState.Loading && createResult !is UiState.Success
    Button(
        onClick = {
            onSubmissionStarted()
            viewModel.createSession(
                draft.finalPrice.trim(),
                draft.remarks,
                draft.otherConcerns,
                requireNotNull(booking),
            )
        },
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
    if (concernAddFailures > 0) {
        ConcernAddFailureActions(
            failureCount = concernAddFailures,
            retryState = concernRetryState,
            onRetry = viewModel::retryConcernAdds,
            onContinue = onContinueAfterConcernFailure,
        )
    }
}

@Composable
private fun ConcernAddFailureActions(
    failureCount: Int,
    retryState: UiState<Unit>,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = "Session created, but $failureCount concern(s) could not be recorded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    val retryEnabled = retryState !is UiState.Loading
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRetry, enabled = retryEnabled) {
            Text(if (retryState is UiState.Loading) "Retrying…" else "Retry concerns")
        }
        TextButton(onClick = onContinue, enabled = retryEnabled) {
            Text("Continue without concerns")
        }
    }
    (retryState as? UiState.Error)?.let { state ->
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * #423 — booked vs walk-in selection plus the booked-only next-appointment date field.
 * Switching back to Walk-in clears the date draft so a stale draft can't leak into a later
 * Booked submit.
 */
@Composable
private fun BookingSection(
    draft: SessionCreateDraft,
    onBookedChange: (Boolean) -> Unit,
    onDateChange: (String) -> Unit,
    enabled: Boolean,
) {
    BookingTypePicker(
        isBooked = draft.isBooked,
        onSelect = onBookedChange,
        enabled = enabled,
    )
    if (!draft.isBooked) return
    val dateInvalid =
        draft.nextAppointmentDate.isNotBlank() &&
            bookingFields(true, draft.nextAppointmentDate, Clock.System.now()) == null
    OutlinedTextField(
        value = draft.nextAppointmentDate,
        onValueChange = onDateChange,
        label = { Text("Next appointment date (optional)") },
        placeholder = { Text("yyyy-MM-dd") },
        supportingText =
            if (dateInvalid) {
                { Text("Use the yyyy-MM-dd format") }
            } else {
                null
            },
        isError = dateInvalid,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * #423 — booked vs walk-in selector (the readOnly-dropdown pattern, the
 * RequestedPractitionerPicker shape). Walk-in stays the default — today's shipped behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingTypePicker(
    isBooked: Boolean,
    onSelect: (Boolean) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (isBooked) "Booked" else "Walk-in",
            onValueChange = {},
            readOnly = true,
            label = { Text("Session start") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Walk-in") },
                enabled = enabled,
                onClick = {
                    onSelect(false)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Booked") },
                enabled = enabled,
                onClick = {
                    onSelect(true)
                    expanded = false
                },
            )
        }
    }
}
