@file:OptIn(ExperimentalUuidApi::class)

package com.companyb.companyapp.session.rates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.session.SetRateRequest
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.ColdLoadPlaceholder
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #418 — coordinator base-rate admin (the "No base rate configured" dead end's UI half).
 * Branch-scoped to the clocked-in branch (`selectedBranchId`, the Inventory/Remittance shape);
 * the route gate lives at the NavHost call sites ([canManageRates] — exact-scope MANAGE_PRODUCTS
 * mirroring `SessionBaseRateRoutes`' filter, backend authoritative).
 *
 * #573 — moved from `ui/screen` to the `session/rates` owner with its state owner
 * ([SessionRatesViewModel.loadRates]/[SessionRatesViewModel.setRate]); workforce
 * `BranchViewModel` no longer carries rate state.
 *
 * #685 — explicit branch-scoped editor: the heading names the clocked-in branch, rows rest
 * as labeled currency values with a single Edit on each editable row, and at most one row
 * edits at a time.
 * The editor stays mounted through save and the authoritative reload (pessimistic,
 * ADR-0022): progress shows only on the saving row, a failed save keeps its draft with an
 * explicit same-id Retry, and a save whose reload then fails reads "Rate saved; could not
 * refresh" with a reload-only Retry (never another POST). MEDICAL_MISSION stays locked at
 * ₱0 ([missionPriceLocked]; the server additionally normalizes, #405 invariant shape).
 */
@Suppress("LongMethod") // #685 screen orchestrator stays whole per #535
@Composable
fun BaseRatesScreen(
    viewModel: SessionRatesViewModel,
    branchId: String?,
    branchName: String?,
    onSelectBranch: () -> Unit,
) {
    val ratesState by viewModel.rates.collectAsState()
    val setRateState by viewModel.setRateState.collectAsState()

    // Keep-last list (the UserViewModel freshest-shape): a reload's Loading/Error never blanks
    // populated rows — the post-save reload and its failure banner overlay the old values.
    var lastRates by remember { mutableStateOf(emptyList<RateResponse>()) }
    // Single-row edit session. The type name (not the enum) is saveable; the draft survives
    // rotation and reloads — a landing reload adopts or flags the authoritative value, it
    // never overwrites the typed text.
    var editingTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var saveBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftBaseline by rememberSaveable { mutableStateOf<String?>(null) }
    var staleCurrent by rememberSaveable { mutableStateOf<String?>(null) }
    var staleAcknowledged by rememberSaveable { mutableStateOf(true) }
    // Save confirmed, authoritative reload pending or failed. Survives rotation: completion
    // is driven by the rates landing below, not by refiring the save effect.
    var saveConfirmedReload by rememberSaveable { mutableStateOf(false) }
    var handledSaveId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSavedTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var refocusEditTypeName by remember { mutableStateOf<String?>(null) }
    val editFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val amountFocus = remember { FocusRequester() }

    val editingType = editingTypeName?.let { runCatching { SessionType.valueOf(it) }.getOrNull() }

    // A branch switch rekeys the whole editor: another branch's rows must never surface here.
    LaunchedEffect(branchId) {
        editingTypeName = null
        draft = ""
        pendingRequestId = null
        savingTypeName = null
        saveBranchId = null
        draftBaseline = null
        staleCurrent = null
        staleAcknowledged = true
        saveConfirmedReload = false
        handledSaveId = null
        lastSavedTypeName = null
        refocusEditTypeName = null
        if (branchId != null) viewModel.loadRates(branchId)
    }
    // Terminal save outcomes. Success arms the reload half (the editor closes on its
    // landing); failure keeps the draft with its original id for an explicit same-id Retry.
    // Terminals tagged to another branch (a switch mid-save) are ignored: they must not arm
    // a reload or clear state on the branch the user now views.
    LaunchedEffect(setRateState) {
        when (val state = setRateState) {
            is UiState.Success -> {
                if (handledSaveId != state.data.id && saveBranchId == branchId) {
                    handledSaveId = state.data.id
                    lastSavedTypeName = state.data.sessionType.name
                    saveConfirmedReload = true
                    logInfo("BaseRatesScreen", "setRate succeeded, reloading rates")
                    if (branchId != null) viewModel.loadRates(branchId)
                }
            }

            is UiState.Error -> {
                if (saveBranchId == branchId) {
                    savingTypeName = null
                }
                logWarn("BaseRatesScreen", "setRate failed: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(ratesState) {
        when (val state = ratesState) {
            is UiState.Success -> {
                lastRates = state.data
                if (saveConfirmedReload) {
                    saveConfirmedReload = false
                    savingTypeName = null
                    if (editingTypeName != null && editingTypeName == lastSavedTypeName) {
                        editingTypeName = null
                        draft = ""
                        pendingRequestId = null
                        draftBaseline = null
                        staleCurrent = null
                        staleAcknowledged = true
                    }
                    if (lastSavedTypeName != null) {
                        refocusEditTypeName = lastSavedTypeName
                    }
                    lastSavedTypeName = null
                } else if (editingTypeName != null) {
                    val current = state.data.firstOrNull { it.sessionType.name == editingTypeName }?.rate
                    if (draftBaseline == null) {
                        draftBaseline = current
                    } else if (rateChangedWhileEditing(draftBaseline, current)) {
                        staleCurrent = current
                        staleAcknowledged = false
                        draftBaseline = current
                    }
                }
            }

            is UiState.Error -> {
                // A confirmed save whose reload then fails keeps its banner but closes the
                // editor: the draft served its purpose, and no open Save may invite another
                // POST once the save is confirmed. Retry here reloads only.
                if (saveConfirmedReload && editingTypeName != null) {
                    editingTypeName = null
                    draft = ""
                    pendingRequestId = null
                    draftBaseline = null
                    staleCurrent = null
                    staleAcknowledged = true
                }
                logWarn("BaseRatesScreen", "loadRates failed: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(editingTypeName) {
        if (editingTypeName != null) amountFocus.requestFocus()
    }
    // Successful save returns focus to the saved row's Edit (nearest surviving control).
    LaunchedEffect(refocusEditTypeName, editingTypeName) {
        if (refocusEditTypeName != null && editingTypeName == null) {
            editFocusRequesters[refocusEditTypeName]?.requestFocus()
            refocusEditTypeName = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = OperationalUiContract.isCompactViewport(maxWidth)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BaseRatesHeader(branchName = branchName)
            if (branchId == null) {
                NullBranchCard(onSelectBranch = onSelectBranch)
            } else {
                val scopedBranchId = branchId

                // Save and explicit Retry share one submit: the edit's original idempotency
                // id is reused, never rotated to resolve a timeout.
                fun submitRateSave(row: RateDisplayRow) {
                    val id = resolveSaveRequestId(pendingRequestId) { Uuid.random().toString() }
                    pendingRequestId = id
                    savingTypeName = row.spec.sessionType.name
                    saveBranchId = scopedBranchId
                    viewModel.setRate(
                        scopedBranchId,
                        SetRateRequest(
                            id = id,
                            sessionType = row.spec.sessionType,
                            rate = draft.trim(),
                        ),
                    )
                }
                val loaded = ratesState is UiState.Success || lastRates.isNotEmpty()
                when {
                    !loaded && ratesState is UiState.Error -> {
                        ErrorCard(
                            message = (ratesState as UiState.Error).message,
                            onRetry = { viewModel.loadRates(scopedBranchId) },
                        )
                    }

                    !loaded -> {
                        ColdLoadPlaceholder(message = "Loading rates…")
                    }

                    else -> {
                        if (saveConfirmedReload && ratesState is UiState.Error) {
                            InlineStatus(
                                message = "Rate saved; could not refresh",
                                kind = InlineStatusKind.FAILURE,
                                onRetry = { viewModel.loadRates(scopedBranchId) },
                            )
                        } else if (saveConfirmedReload && ratesState is UiState.Loading) {
                            InlineStatus(message = "Refreshing rates…", kind = InlineStatusKind.UPDATING)
                        }
                        RateRows(
                            rates = lastRates,
                            editingType = editingType,
                            draft = draft,
                            onDraftChange = { draft = it },
                            staleCurrent = staleCurrent,
                            staleAcknowledged = staleAcknowledged,
                            onStaleAcknowledge = { staleAcknowledged = true },
                            savingTypeName = savingTypeName,
                            setRateState = setRateState,
                            isCompact = isCompact,
                            editFocusRequesters = editFocusRequesters,
                            amountFocus = amountFocus,
                            onOpenEdit = { row ->
                                viewModel.resetSetRateState()
                                editingTypeName = row.spec.sessionType.name
                                draft = row.rateText.orEmpty()
                                draftBaseline = row.rateText
                                pendingRequestId = Uuid.random().toString()
                                staleCurrent = null
                                staleAcknowledged = true
                            },
                            onCancelEdit = {
                                viewModel.resetSetRateState()
                                editingTypeName = null
                                draft = ""
                                pendingRequestId = null
                                savingTypeName = null
                                saveBranchId = null
                                draftBaseline = null
                                staleCurrent = null
                                staleAcknowledged = true
                            },
                            onSaveEdit = ::submitRateSave,
                            onRetrySave = ::submitRateSave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BaseRatesHeader(branchName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Session rates", style = MaterialTheme.typography.titleLarge)
        if (branchName != null) {
            Text(branchName, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = "Used to price new sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
private fun NullBranchCard(onSelectBranch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Clock in to manage session rates",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(label = "Select branch", onClick = onSelectBranch)
    }
}

@Composable
// #685 16-param rows stay whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #685
private fun RateRows(
    rates: List<RateResponse>,
    editingType: SessionType?,
    draft: String,
    onDraftChange: (String) -> Unit,
    staleCurrent: String?,
    staleAcknowledged: Boolean,
    onStaleAcknowledge: () -> Unit,
    savingTypeName: String?,
    setRateState: UiState<RateResponse>,
    isCompact: Boolean,
    editFocusRequesters: MutableMap<String, FocusRequester>,
    amountFocus: FocusRequester,
    onOpenEdit: (RateDisplayRow) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: (RateDisplayRow) -> Unit,
    onRetrySave: (RateDisplayRow) -> Unit,
) {
    val rows = toRateDisplayRows(rates)
    rows.forEachIndexed { index, row ->
        val typeName = row.spec.sessionType.name
        if (editingType == row.spec.sessionType) {
            RateRowEditor(
                row = row,
                draft = draft,
                onDraftChange = onDraftChange,
                isBusy = savingTypeName == typeName && setRateState is UiState.Loading,
                saveError = (setRateState as? UiState.Error)?.message,
                staleNotice = staleCurrent?.let { peso(it) },
                staleAcknowledged = staleAcknowledged,
                onStaleAcknowledge = onStaleAcknowledge,
                isCompact = isCompact,
                amountFocus = amountFocus,
                onSave = { onSaveEdit(row) },
                onCancel = onCancelEdit,
                onRetry = { onRetrySave(row) },
            )
        } else {
            RateRowRest(
                row = row,
                // No second editor opens until the in-flight save resolves; values stay readable.
                editEnabled = savingTypeName == null,
                editFocus = editFocusRequesters.getOrPut(typeName) { FocusRequester() },
                onEdit = { onOpenEdit(row) },
            )
        }
        if (index < rows.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun RateRowRest(
    row: RateDisplayRow,
    editEnabled: Boolean,
    editFocus: FocusRequester,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.spec.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = rateValueLabel(row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.missionLocked) {
                Text(
                    text = "Always free.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        }
        if (!row.missionLocked) {
            TertiaryActionButton(
                label = "Edit",
                onClick = onEdit,
                enabled = editEnabled,
                modifier = Modifier.focusRequester(editFocus),
            )
        }
    }
}

@Composable
// #685 13-param editor stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #685
private fun RateRowEditor(
    row: RateDisplayRow,
    draft: String,
    onDraftChange: (String) -> Unit,
    isBusy: Boolean,
    saveError: String?,
    staleNotice: String?,
    staleAcknowledged: Boolean,
    onStaleAcknowledge: () -> Unit,
    isCompact: Boolean,
    amountFocus: FocusRequester,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val validationError = rateDraftError(draft)
    // A flagged external change blocks the next save until the editor reviews it.
    val saveEnabled = validationError == null && draft.trim().isNotBlank() && staleAcknowledged

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(row.spec.label, style = MaterialTheme.typography.bodyLarge)
        rateEditingContextLabel(row)?.let { context ->
            Text(text = context, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
        if (staleNotice != null && !staleAcknowledged) {
            InlineStatus(
                message = "Current value changed to $staleNotice. Review before saving.",
                kind = InlineStatusKind.STALE,
                onRetry = onStaleAcknowledge,
                retryLabel = "Reviewed",
            )
        }
        if (isCompact) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Rate amount") },
                prefix = { Text("₱") },
                enabled = !isBusy,
                singleLine = true,
                isError = validationError != null || saveError != null,
                supportingText = validationError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).operationalFocusRing(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryActionButton(label = "Save rate", onClick = onSave, enabled = saveEnabled, isBusy = isBusy)
                TertiaryActionButton(label = "Cancel", onClick = onCancel, enabled = !isBusy)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("Rate amount") },
                    prefix = { Text("₱") },
                    enabled = !isBusy,
                    singleLine = true,
                    isError = validationError != null || saveError != null,
                    supportingText = validationError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).focusRequester(amountFocus).operationalFocusRing(),
                )
                PrimaryActionButton(label = "Save rate", onClick = onSave, enabled = saveEnabled, isBusy = isBusy)
                TertiaryActionButton(label = "Cancel", onClick = onCancel, enabled = !isBusy)
            }
        }
        if (saveError != null) {
            InlineStatus(message = saveError, kind = InlineStatusKind.FAILURE, onRetry = onRetry)
        }
    }
}
