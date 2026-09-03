@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.remittanceListKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #120 — Remittance list screen per the locked #103 D1/D2/D7.
 *
 * - D1: filter tabs Drafts / Submitted / All (default Drafts); rows = type badge, method, date
 *   range, status, Net on submitted SESSION rows (desktop dense table / mobile cards via the
 *   expect split); the RouteGateCard lives at the NavHost call site (SUBMIT_REMITTANCE, #99 D7).
 * - D2: create popup (type/method/date range, defaults today → today, client-generated UUID —
 *   idempotent create; a duplicate (branch, type, date) returns the existing draft server-side,
 *   treated as success: popup closes + the Drafts list refreshes).
 * - D7: load on entry + refresh button, no polling, branch-scoped (selectedBranchId); keep-last
 *   list per tab (VM-held mirror, #161 port — survives pop-back; a failed refresh/switch keeps
 *   the previously loaded rows).
 */
@Composable
fun RemittanceListScreen(
    viewModel: RemittanceViewModel,
    branchId: String?,
    onRemittanceClick: (RemittanceResponse) -> Unit,
) {
    val listState by viewModel.remittanceList.collectAsState()
    val lastByTab by viewModel.lastByTab.collectAsState()
    val createDraftState by viewModel.createDraftResult.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(RemittanceTab.DRAFTS) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logInfo("RemittanceListScreen", "composable entered (first composition)")
        if (branchId != null) {
            viewModel.loadRemittances(branchId, RemittanceTab.DRAFTS.status)
        }
    }

    LaunchedEffect(selectedTab) {
        if (branchId != null) {
            viewModel.loadRemittances(branchId, selectedTab.status)
        }
    }

    LaunchedEffect(createDraftState) {
        when (val state = createDraftState) {
            is UiState.Success -> {
                // D2 — draft created (or already existed server-side): close the popup and refresh
                // the Drafts list so it appears under the default tab.
                showCreateDialog = false
                if (branchId != null) {
                    viewModel.loadRemittances(branchId, RemittanceTab.DRAFTS.status)
                }
            }

            is UiState.Error -> {
                logWarn("RemittanceListScreen", "createDraftState=Error: ${state.message}")
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
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Remittances",
                style = MaterialTheme.typography.titleLarge,
            )
            Row {
                TextButton(
                    onClick = { showCreateDialog = true },
                    enabled = branchId != null,
                ) {
                    Text("New draft")
                }
                TextButton(
                    onClick = {
                        branchId?.let { viewModel.loadRemittances(it, selectedTab.status) }
                    },
                    enabled = branchId != null && listState !is UiState.Loading,
                ) {
                    Text("Refresh")
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            RemittanceTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }

        // Keep-last render source per tab (#161 port, the #143 VM-held-list shape — the VM
        // mirror survives pop-back, the old screen-side remember died): the selected tab's last
        // successful list renders through Loading/Error (a reload never flashes a spinner over
        // held rows, a failed reload never replaces the list), and a response belonging to
        // ANOTHER tab can never render here (the gate keys on the selected tab's mirror, not on
        // the single list flow the last-writer fetch owns).
        val mirrorKey = branchId?.let { remittanceListKey(it, selectedTab.status) }
        val held = mirrorKey?.let { lastByTab[it] }
        when {
            held != null -> {
                RemittanceTabContent(
                    remittances = held,
                    emptyMessage = selectedTab.emptyMessage,
                    onRemittanceClick = onRemittanceClick,
                )
            }

            listState is UiState.Error -> {
                ErrorCard(
                    message = (listState as UiState.Error).message,
                    onRetry = {
                        branchId?.let { viewModel.loadRemittances(it, selectedTab.status) }
                    },
                )
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showCreateDialog && branchId != null) {
        CreateRemittanceDialog(
            branchId = branchId,
            createState = createDraftState,
            onCreate = viewModel::createDraft,
            onDismiss = {
                if (createDraftState !is UiState.Loading) {
                    showCreateDialog = false
                }
            },
        )
    }
}

@Composable
private fun RemittanceTabContent(
    remittances: List<RemittanceResponse>,
    emptyMessage: String,
    onRemittanceClick: (RemittanceResponse) -> Unit,
) {
    if (remittances.isEmpty()) {
        EmptyState(emptyMessage)
    } else {
        RemittanceRowList(
            remittances = remittances,
            onRemittanceClick = onRemittanceClick,
        )
    }
}

/**
 * D2 — create popup (desktop dialog / mobile centered, #97/#99 modal treatment). Type + method
 * dropdowns, date range defaulting to today → today, freely extendable (BR guidance, not
 * enforcement — F13). Client-generated UUID for idempotent create (#100 D7 precedent).
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun CreateRemittanceDialog(
    branchId: String,
    createState: UiState<RemittanceResponse>,
    onCreate: (CreateRemittanceDraftRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(RemittanceTypeChoice.SESSIONS) }
    var method by remember { mutableStateOf(RemittanceMethodChoice.BANK_TRANSFER) }
    var startDate by remember { mutableStateOf(todayIso()) }
    var endDate by remember { mutableStateOf(todayIso()) }
    var dateError by remember { mutableStateOf<String?>(null) }

    val inFlight = createState is UiState.Loading

    fun commit() {
        if (inFlight) return
        val rangeProblem = remittanceRangeError(startDate, endDate)
        if (rangeProblem != null) {
            dateError = rangeProblem
            return
        }
        dateError = null
        onCreate(
            CreateRemittanceDraftRequest(
                id = Uuid.random().toString(),
                type =
                    com.companyb.companyapp.domain.RemittanceType
                        .valueOf(type.raw),
                branchId = branchId,
                method =
                    com.companyb.companyapp.domain.RemittanceMethod
                        .valueOf(method.raw),
                dateRangeStart = startDate,
                dateRangeEnd = endDate,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New remittance draft") },
        text = {
            Column {
                LabeledDropdown(
                    label = "Type",
                    displayValue = type.label,
                    options = RemittanceTypeChoice.entries.map { it.label },
                    onSelect = { label ->
                        type = RemittanceTypeChoice.entries.first { it.label == label }
                    },
                )
                LabeledDropdown(
                    label = "Method",
                    displayValue = method.label,
                    options = RemittanceMethodChoice.entries.map { it.label },
                    onSelect = { label ->
                        method = RemittanceMethodChoice.entries.first { it.label == label }
                    },
                )
                RemittanceDatePickerField(
                    label = "Date range start",
                    value = startDate,
                    onValueChange = { startDate = it },
                )
                RemittanceDatePickerField(
                    label = "Date range end",
                    value = endDate,
                    onValueChange = { endDate = it },
                )
                dateError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                (createState as? UiState.Error)?.let {
                    Text(
                        text = it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::commit,
                enabled = !inFlight,
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !inFlight,
            ) {
                Text("Cancel")
            }
        },
    )
}

/** D2/D9 — dropdown built on the GenderFieldEditor pattern (read-only field + DropdownMenu). */
@Composable
internal fun LabeledDropdown(
    label: String,
    displayValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                singleLine = true,
                readOnly = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            menuOpen = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * #447 — date-picker range entry (owner verdict on #429): the raw yyyy-MM-dd text inputs
 * die. Read-only field + Material3 DatePickerDialog; the value only ever changes to a
 * calendar-picked valid ISO date, and the dialog's submit path still fails closed via
 * [remittanceRangeError]. No preset-range buttons (prototype fakes, never carried over).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemittanceDatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            placeholder = { Text("Pick a date") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { pickerOpen = true },
        )
    }

    if (pickerOpen) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = isoToPickerMillis(value),
            )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerMillisToIso(pickerState.selectedDateMillis)?.let(onValueChange)
                        pickerOpen = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** D1 — list tabs; the status query value per tab (backend filter, uppercase enum names). */
private enum class RemittanceTab(
    val label: String,
    val status: String,
    val emptyMessage: String,
) {
    DRAFTS("Drafts", "DRAFT", "No drafts"),
    SUBMITTED("Submitted", "SUBMITTED", "No submitted remittances"),
    ALL("All", "ALL", "No remittances"),
}
