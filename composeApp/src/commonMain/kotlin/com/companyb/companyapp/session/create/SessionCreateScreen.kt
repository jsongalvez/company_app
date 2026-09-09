package com.companyb.companyapp.session.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientCreateDialog
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.client.ClientViewModel
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.CreateClientRequest
import com.companyb.companyapp.contracts.session.ConcernResponse
import com.companyb.companyapp.contracts.session.SessionPreviewResponse
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.contract.operationalTouchTarget
import com.companyb.companyapp.ui.screen.missionPriceLocked
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn

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
// #594 7-param entry stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #594
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
    val preview by viewModel.preview.collectAsState()
    val selectedConcernIds by viewModel.selectedConcernIds.collectAsState()
    val selectedPractitioner by viewModel.selectedPractitioner.collectAsState()
    val isSubmissionLocked = isSessionCreateLocked(createResult)
    // #674 — abandoning a dirty draft (Back, Cancel, system back) offers Keep
    // editing / Discard; untouched forms and mid-flight submissions pop silently
    // (locked backs stay swallowed by the platform handler).
    val dirty =
        isSessionCreateDirty(selectedClient, draft, selectedConcernIds, selectedPractitioner)
    // User-authored content only (auto price excluded): abandoning it via Change
    // client loses a typed price with no recovery, so Change confirms while plain
    // re-selection stays in-flow.
    val userAuthored =
        draft.hasUserEdits() || selectedConcernIds.isNotEmpty() || selectedPractitioner != null
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    // False = Discard only abandons the draft and stays (Change-client path).
    var discardPopsAfter by rememberSaveable { mutableStateOf(true) }
    val requestBack = {
        if (!isSubmissionLocked) {
            if (dirty) {
                discardPopsAfter = true
                showDiscardDialog = true
            } else {
                onBack()
            }
        }
    }
    val requestChangeClient = {
        if (!isSubmissionLocked) {
            if (userAuthored) {
                discardPopsAfter = false
                showDiscardDialog = true
            } else {
                viewModel.clearSelectedClient()
            }
        }
    }
    // System back dismisses the discard dialog first (stays editing), matching
    // the dialog's own Escape behavior; buttons share requestBack directly.
    SessionCreateNavigationGuard(
        isSubmissionLocked = isSubmissionLocked,
        onSubmissionLockChanged = onSubmissionLockChanged,
        onSystemBack = {
            if (showDiscardDialog) {
                showDiscardDialog = false
            } else {
                requestBack()
            }
        },
    )
    // #674 — selecting a client focuses the first required intake control (price);
    // the generation guard below keeps profile-pop recompositions from stealing focus.
    val priceFocus = remember { FocusRequester() }
    val dateFocus = remember { FocusRequester() }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var lastFocusedClientId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedClient?.id) {
        val id = selectedClient?.id
        if (id == null) {
            lastFocusedClientId = null
        } else if (id != lastFocusedClientId) {
            lastFocusedClientId = id
            // A new client's validation state never inherits the previous one.
            showValidation = false
            priceFocus.requestFocus()
        }
    }
    // #674 — submit validates client-side first: the first invalid field receives
    // focus with its inline explanation; values are always retained. The server
    // stays authoritative once the request leaves.
    val attemptSubmit: () -> Unit = {
        val price = parseSessionPrice(draft.finalPrice)
        val booking = bookingFields(draft.isBooked, draft.nextAppointmentDate)
        if (selectedClient == null || preview !is UiState.Success) {
            // Unreachable via the bar (primary needs a client + ready preview);
            // the card owns preview recovery.
            if (preview !is UiState.Success) viewModel.retryPreview()
        } else if (price == null) {
            showValidation = true
            priceFocus.requestFocus()
        } else if (booking == null) {
            showValidation = true
            dateFocus.requestFocus()
        } else {
            onSubmissionLockChanged(true)
            viewModel.createSession(
                draft.finalPrice.trim(),
                draft.remarks,
                draft.otherConcerns,
                booking,
            )
        }
    }
    // The dialog's create path lives in the caller-provided ClientViewModel (entry-scoped).
    val createState by clientViewModel.createClientResult.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var sessionNavigationStarted by rememberSaveable { mutableStateOf(false) }
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
                .imePadding()
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SessionCreateHeader(branchName, isSubmissionLocked, requestBack)

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
                    priceFocus = priceFocus,
                    dateFocus = dateFocus,
                    showValidation = showValidation,
                    onChangeClient = requestChangeClient,
                ),
            onCreateNewClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        // #674 — persistent action bar: Cancel + Start/Create with a reserved
        // validation slot. Outside the scrolling form so Start stays reachable
        // at 1366x768 and 390x844 with the keyboard visible.
        SessionCreateActionBar(
            viewModel = viewModel,
            draft = draft,
            hasClient = selectedClient != null,
            showValidation = showValidation,
            onCancel = requestBack,
            onSubmit = attemptSubmit,
            onContinueAfterConcernFailure = {
                (createResult as? UiState.Success)?.data?.id?.let(navigateToCreatedSession)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDiscardDialog) {
        DiscardDraftDialog(
            onKeepEditing = { showDiscardDialog = false },
            onDiscard = {
                showDiscardDialog = false
                viewModel.discardDraft()
                if (discardPopsAfter) onBack()
            },
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
    onSystemBack: () -> Unit,
) {
    LaunchedEffect(isSubmissionLocked) {
        onSubmissionLockChanged(isSubmissionLocked)
    }
    val lockedNow by rememberUpdatedState(isSubmissionLocked)
    DisposableEffect(Unit) {
        // #674 — rotation disposes without abandoning the entry: only release the
        // shell lock when no submission is in flight, or the parent unlocks
        // mid-submit until recomposition re-locks.
        onDispose { if (!lockedNow) onSubmissionLockChanged(false) }
    }
    SessionCreateBackHandler(locked = isSubmissionLocked, onBack = onSystemBack)
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

/**
 * #674 — the intake workspace: prominent price beside the history-derived
 * type/count, explicit Walk-in/Booked intent with its date, clinical concerns
 * (including Other) in the main flow, and requested practitioner + remarks under
 * a collapsed Additional details section. Submit lives in the persistent bottom
 * bar — never inside this scrolling column.
 */
@Composable
@Suppress("LongParameterList") // #674 declarative-UI form signature stays whole per #535.
internal fun SessionFormFields(
    viewModel: SessionCreateFormApi,
    draft: SessionCreateDraft,
    selectedClient: ClientResponse?,
    isSubmissionLocked: Boolean,
    priceFocus: FocusRequester,
    dateFocus: FocusRequester,
    showValidation: Boolean,
) {
    val preview by viewModel.preview.collectAsState()
    val controlsEnabled = !isSubmissionLocked

    PriceAndTypeRow(
        viewModel = viewModel,
        preview = preview,
        draft = draft,
        selectedClient = selectedClient,
        controlsEnabled = controlsEnabled,
        priceFocus = priceFocus,
        showValidation = showValidation,
    )

    IntentSection(
        draft = draft,
        onBookedChange = viewModel::setBooked,
        onDateChange = viewModel::setNextAppointmentDate,
        dateFocus = dateFocus,
        enabled = controlsEnabled,
    )

    val concernsState by viewModel.concerns.collectAsState()
    val selectedConcernIds by viewModel.selectedConcernIds.collectAsState()
    ConcernsBlock(viewModel, concernsState, selectedConcernIds, controlsEnabled)

    OutlinedTextField(
        value = draft.otherConcerns,
        onValueChange = viewModel::setOtherConcerns,
        label = { Text("Other concerns (optional)") },
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth().operationalField(),
    )

    AdditionalDetails(
        viewModel = viewModel,
        draft = draft,
        enabled = controlsEnabled,
    )
}

/**
 * #674 — price prominent beside the history-derived type/count. Wide content
 * keeps them side by side; narrow stacks the type card above the price so both
 * stay reachable at 390dp.
 */
@Composable
@Suppress("LongParameterList") // #674 declarative-UI row signature stays whole per #535.
private fun PriceAndTypeRow(
    viewModel: SessionCreateFormApi,
    preview: UiState<SessionPreviewResponse>,
    draft: SessionCreateDraft,
    selectedClient: ClientResponse?,
    controlsEnabled: Boolean,
    priceFocus: FocusRequester,
    showValidation: Boolean,
) {
    // #405 — the mission price is not editable input; ₱0 is shown locked (server normalizes
    // authoritatively regardless).
    val previewData = (preview as? UiState.Success)?.data
    val missionPrice = previewData != null && missionPriceLocked(previewData.sessionType)
    val priceInvalid = preview is UiState.Success && parseSessionPrice(draft.finalPrice) == null
    val showPriceError = priceInvalid && (showValidation || draft.finalPriceEdited)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= WIDE_INTAKE_BREAKPOINT) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                PriceField(
                    value = draft.finalPrice,
                    onValueChange = viewModel::setFinalPrice,
                    missionPrice = missionPrice,
                    controlsEnabled = controlsEnabled,
                    priceFocus = priceFocus,
                    showPriceError = showPriceError,
                    modifier = Modifier.weight(1f),
                )
                TypeCountCard(
                    viewModel = viewModel,
                    preview = preview,
                    selectedClient = selectedClient,
                    controlsEnabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TypeCountCard(
                    viewModel = viewModel,
                    preview = preview,
                    selectedClient = selectedClient,
                    controlsEnabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                PriceField(
                    value = draft.finalPrice,
                    onValueChange = viewModel::setFinalPrice,
                    missionPrice = missionPrice,
                    controlsEnabled = controlsEnabled,
                    priceFocus = priceFocus,
                    showPriceError = showPriceError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val WIDE_INTAKE_BREAKPOINT = 560.dp

@Composable
@Suppress("LongParameterList") // #674 declarative-UI field signature stays whole per #535.
private fun PriceField(
    value: String,
    onValueChange: (String) -> Unit,
    missionPrice: Boolean,
    controlsEnabled: Boolean,
    priceFocus: FocusRequester,
    showPriceError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Final price (₱)") },
        supportingText =
            if (missionPrice) {
                { Text("Medical mission visit — always free") }
            } else if (showPriceError) {
                { Text("Enter a valid price of 0 or more") }
            } else {
                null
            },
        isError = showPriceError,
        singleLine = true,
        enabled = !missionPrice && controlsEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.operationalField().focusRequester(priceFocus),
    )
}

/**
 * #674 — the history-derived type/count card shown beside the price: the
 * server-previewed session type (never a client-side choice) plus the client's
 * total session count for context.
 */
@Composable
private fun TypeCountCard(
    viewModel: SessionCreateFormApi,
    preview: UiState<SessionPreviewResponse>,
    selectedClient: ClientResponse?,
    controlsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    when (preview) {
        is UiState.Idle, is UiState.Loading -> {
            Box(modifier.padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            ErrorCard(
                message = preview.message,
                onRetry = { viewModel.retryPreview() },
                retryEnabled = controlsEnabled,
            )
        }

        is UiState.Success -> {
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = modifier.fillMaxWidth(),
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
                    if (selectedClient != null) {
                        Text(
                            text = "Total sessions: ${selectedClient.sessionCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                        )
                    }
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
    val errorState = membersState as? UiState.Error
    val isError = errorState != null
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
    // #674 — the failure stays local to this field with a real Retry button that
    // survives even when selection itself is unavailable; the rest of the form
    // keeps working and a previous explicit choice is never cleared by the error.
    if (errorState != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        ) {
            Text(
                text = errorState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TertiaryActionButton(
                label = "Retry",
                onClick = onRetry,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ConcernsBlock(
    viewModel: SessionCreateFormApi,
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
    viewModel: SessionCreateFormApi,
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

/**
 * #674 — explicit Walk-in / Booked intent controls (#423 meanings preserved:
 * walk-in sends no booking fields; booked carries the optional ISO
 * next-appointment date with bookedAt server-owned). Switching back to Walk-in
 * clears the date draft in the VM so a stale draft can't leak into a later
 * Booked submit. No new appointment scheduler: date stays yyyy-MM-dd.
 */
@Composable
private fun IntentSection(
    draft: SessionCreateDraft,
    onBookedChange: (Boolean) -> Unit,
    onDateChange: (String) -> Unit,
    dateFocus: FocusRequester,
    enabled: Boolean,
) {
    // #672 — filter chips meet the #670 48dp touch target + 2dp focus ring (raw M3
    // chips default to ~32dp with no contract ring).
    val chipModifier = Modifier.operationalTouchTarget().operationalFocusRing()
    Text(
        text = "Session start",
        style = MaterialTheme.typography.labelSmall,
        color = InkSubtle,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !draft.isBooked,
            onClick = { onBookedChange(false) },
            label = { Text("Walk-in") },
            enabled = enabled,
            modifier = chipModifier,
        )
        FilterChip(
            selected = draft.isBooked,
            onClick = { onBookedChange(true) },
            label = { Text("Booked") },
            enabled = enabled,
            modifier = chipModifier,
        )
    }
    if (!draft.isBooked) return
    val dateInvalid =
        draft.nextAppointmentDate.isNotBlank() &&
            bookingFields(true, draft.nextAppointmentDate) == null
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
        modifier = Modifier.fillMaxWidth().operationalField().focusRequester(dateFocus),
    )
}

/**
 * #674 — requested practitioner + remarks under a collapsed section with a
 * populated summary ("Name · Remarks added"). Concerns stay in the main flow:
 * they are essential intake, not advanced details.
 */
@Composable
private fun AdditionalDetails(
    viewModel: SessionCreateFormApi,
    draft: SessionCreateDraft,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val membersState by viewModel.members.collectAsState()
    val selectedPractitioner by viewModel.selectedPractitioner.collectAsState()
    val summary =
        additionalDetailsSummary(selectedPractitioner?.displayName, draft.remarks)
            // The practitioner failure lives inside this collapsed section: surface it
            // on the header so it is discoverable without expanding.
            ?: if (membersState is UiState.Error && !expanded) {
                "Colleagues unavailable — expand to retry"
            } else {
                null
            }
    Surface(
        onClick = { if (enabled) expanded = !expanded },
        enabled = enabled,
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Additional details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = summary ?: "Optional",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSubtle,
                )
            }
            if (expanded) {
                RequestedPractitionerPicker(
                    membersState = membersState,
                    selected = selectedPractitioner,
                    onSelect = viewModel::selectPractitioner,
                    onRetry = viewModel::retryMembers,
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = draft.remarks,
                    onValueChange = viewModel::setRemarks,
                    label = { Text("Remarks (optional)") },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().operationalField(),
                )
            }
        }
    }
}

/**
 * #674 — abandoning a dirty draft offers Keep editing (safe default, initial
 * focus) or Discard changes. Honors the #670 dialog rules (max 560dp, scrolling
 * body, fixed actions, no entrance animation); Escape/Back stays editing.
 */
@Composable
private fun DiscardDraftDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keepFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard unsent changes?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text =
                        "This clears the selected client and everything entered. " +
                            "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            SecondaryActionButton(label = "Discard changes", onClick = onDiscard)
        },
        dismissButton = {
            PrimaryActionButton(
                label = "Keep editing",
                onClick = onKeepEditing,
                modifier = Modifier.focusRequester(keepFocus),
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = OperationalUiContract.dialogMaxWidth)
                .padding(horizontal = Spacing.md),
        shape = MaterialTheme.shapes.medium,
    )
}
