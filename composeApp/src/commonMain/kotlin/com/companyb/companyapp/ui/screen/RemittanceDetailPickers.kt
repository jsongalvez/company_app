package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceDriftResponse
import com.companyb.companyapp.dto.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.addDayBreakdown
import com.companyb.companyapp.viewmodel.addLine
import com.companyb.companyapp.viewmodel.deleteDayBreakdown
import com.companyb.companyapp.viewmodel.deleteLine
import com.companyb.companyapp.viewmodel.loadDrift
import com.companyb.companyapp.viewmodel.remittanceListKey
import com.companyb.companyapp.viewmodel.submit
import com.companyb.companyapp.viewmodel.undo
import com.companyb.companyapp.viewmodel.updateHeader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius

/**
 * D3 — static per-type income-picker configuration: titles, per-entry labels, the amount
 * source, and the request mapping. One instance per picker kind (sessions, product sales).
 */
internal data class IncomePickerSpec<T>(
    val title: String,
    val emptyMessage: String,
    val idOf: (T) -> String,
    val mainLabel: (T) -> String,
    val secondaryLabel: (T) -> String,
    val amountOf: (T) -> String,
    val toRequest: (id: String, amount: String) -> CreateRemittanceLineRequest,
)

/** D3 — per-open picker inputs: the cached load, the line mutation state, already-added ids. */
internal class IncomePickerData<T>(
    val state: UiState<List<T>>,
    val mutationState: UiState<RemittanceLineResponse>,
    val includedIds: Set<String>,
)

/** D3 — dialog flow callbacks shared by the income pickers: retry the load, queue adds, close. */
internal class IncomePickerFlow(
    val onLoad: () -> Unit,
    val onAdd: (List<CreateRemittanceLineRequest>) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * D3 — per-open picker session: tick selection + editable amounts + the one-POST-per-row
 * add queue. Owns the mutation interaction so the dialog composables stay thin.
 */
internal class IncomePickerSession<T>(
    val spec: IncomePickerSpec<T>,
) {
    var selectedIds by mutableStateOf(emptySet<String>())
    var amounts by mutableStateOf(emptyMap<String, String>())
    var pending by mutableStateOf(emptyList<CreateRemittanceLineRequest>())

    fun toggle(
        entry: T,
        selected: Boolean,
        included: Boolean,
    ) {
        if (included) return
        val id = spec.idOf(entry)
        selectedIds = if (selected) selectedIds - id else selectedIds + id
        amounts = amounts + (id to spec.amountOf(entry))
    }

    fun setAmount(
        id: String,
        value: String,
    ) {
        amounts = amounts + (id to value)
    }

    /** Builds the queued requests when Add is allowed; null keeps the button blocked. */
    fun takeConfirmed(data: IncomePickerData<T>): List<CreateRemittanceLineRequest>? {
        val entries = (data.state as? UiState.Success)?.data ?: return null
        if (data.mutationState is UiState.Loading || pending.isNotEmpty() || selectedIds.isEmpty()) {
            return null
        }
        val requests =
            selectedIds.map { id ->
                spec.toRequest(id, amounts[id] ?: spec.amountOf(entries.first { spec.idOf(it) == id }))
            }
        pending = requests
        selectedIds = emptySet()
        return requests
    }
}

/** D3 — sessions picker preset: client name, time (bookedAt) + status, amount from finalPrice. */
@OptIn(ExperimentalUuidApi::class)
private val SessionIncomePicker =
    IncomePickerSpec<RemittanceSessionPickerEntryResponse>(
        title = "Add session income",
        emptyMessage = "No sessions in this date range",
        idOf = { it.id },
        mainLabel = { it.clientName ?: "Anonymized" },
        secondaryLabel = { entry ->
            val time = entry.bookedAt?.let { formatRelativeTimestamp(it) }
            listOfNotNull(time, entry.sessionStatus).joinToString(" · ")
        },
        amountOf = { it.finalPrice },
        toRequest = { id, amount ->
            CreateRemittanceLineRequest(
                id = Uuid.random().toString(),
                type = com.companyb.companyapp.domain.RemittanceLineType.SESSION,
                sessionId = id,
                amount = amount,
            )
        },
    )

/** D3 — product-sales preset: product name + quantity, amount from totalAmountAtTime. */
@OptIn(ExperimentalUuidApi::class)
private val ProductSaleIncomePicker =
    IncomePickerSpec<RemittanceProductSalePickerEntryResponse>(
        title = "Add product sales income",
        emptyMessage = "No product sales in this date range",
        idOf = { it.id },
        mainLabel = { it.productName },
        secondaryLabel = { "Qty ${it.quantity}" },
        amountOf = { it.totalAmountAtTime },
        toRequest = { id, amount ->
            CreateRemittanceLineRequest(
                id = Uuid.random().toString(),
                type = com.companyb.companyapp.domain.RemittanceLineType.PRODUCT_SALE,
                productSaleId = id,
                amount = amount,
            )
        },
    )

/** D3 — sessions picker over the shared income picker. */
@Composable
internal fun SessionPickerDialog(
    data: IncomePickerData<RemittanceSessionPickerEntryResponse>,
    flow: IncomePickerFlow,
) {
    val session = remember { IncomePickerSession(SessionIncomePicker) }
    IncomePickerDialog(session = session, data = data, flow = flow)
}

/** D3 — product-sales picker over the shared income picker. */
@Composable
internal fun ProductSalePickerDialog(
    data: IncomePickerData<RemittanceProductSalePickerEntryResponse>,
    flow: IncomePickerFlow,
) {
    val session = remember { IncomePickerSession(ProductSaleIncomePicker) }
    IncomePickerDialog(session = session, data = data, flow = flow)
}

/**
 * D3 — shared tick-to-include income picker (sessions + product sales): per-row selection
 * with prefilled editable amounts, one mutation POST per selected row via [PendingQueueEffect].
 */
@Composable
private fun <T> IncomePickerDialog(
    session: IncomePickerSession<T>,
    data: IncomePickerData<T>,
    flow: IncomePickerFlow,
) {
    PickerPendingHost(
        pending = session.pending,
        mutationState = data.mutationState,
        onAdd = flow.onAdd,
        onDismiss = flow.onDismiss,
        onPendingChange = { session.pending = it },
    )
    AlertDialog(
        onDismissRequest = {
            if (data.mutationState !is UiState.Loading && session.pending.isEmpty()) flow.onDismiss()
        },
        title = { Text(session.spec.title) },
        text = {
            IncomePickerBody(
                session = session,
                data = data,
                flow = flow,
            )
        },
        confirmButton = {
            IncomePickerConfirmButton(
                session = session,
                data = data,
                flow = flow,
            )
        },
        dismissButton = { IncomePickerDismissButton(mutationState = data.mutationState, onDismiss = flow.onDismiss) },
    )
}

@Composable
private fun <T> PickerPendingHost(
    pending: List<T>,
    mutationState: UiState<*>,
    onAdd: (List<T>) -> Unit,
    onDismiss: () -> Unit,
    onPendingChange: (List<T>) -> Unit,
) {
    // One POST per selected row, advanced on each success; a failure or the VM's 403/409 Idle
    // reset clears the queue (the dialog stays open showing the error / the reloaded state).
    PendingQueueEffect(
        pending = pending,
        mutationState = mutationState,
        onNext = { remaining ->
            onPendingChange(remaining)
            onAdd(remaining)
        },
        onFinished = {
            onPendingChange(emptyList())
            onDismiss()
        },
        onAborted = { onPendingChange(emptyList()) },
    )
}

@Composable
private fun <T> IncomePickerConfirmButton(
    session: IncomePickerSession<T>,
    data: IncomePickerData<T>,
    flow: IncomePickerFlow,
) {
    TextButton(
        onClick = { session.takeConfirmed(data)?.let(flow.onAdd) },
        enabled =
            data.mutationState !is UiState.Loading && session.pending.isEmpty() &&
                session.selectedIds.isNotEmpty(),
    ) {
        Text("Add")
    }
}

@Composable
private fun <T> IncomePickerBody(
    session: IncomePickerSession<T>,
    data: IncomePickerData<T>,
    flow: IncomePickerFlow,
) {
    val spec = session.spec
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (val s = data.state) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = flow.onLoad) { Text("Retry") }
            }

            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState(spec.emptyMessage)
                } else {
                    s.data.forEach { entry ->
                        val id = spec.idOf(entry)
                        val included = id in data.includedIds
                        val selected = id in session.selectedIds
                        PickerEntryRow(
                            model =
                                PickerEntryRowModel(
                                    label = spec.mainLabel(entry),
                                    secondary = spec.secondaryLabel(entry),
                                    amountText = peso(spec.amountOf(entry)),
                                    selected = selected,
                                    enabled = !included,
                                    amount = session.amounts[id],
                                ),
                            onAmountChange = { value -> session.setAmount(id, value) },
                            onToggle = { session.toggle(entry, selected, included) },
                        )
                    }
                }
            }
        }
        (data.mutationState as? UiState.Error)?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Shared add-queue driver for the tick-to-include pickers (D3/D4): one mutation per selected
 * row, advanced on each success; an Error or the VM's 403/409 Idle reset aborts the batch
 * (the dialog stays open — the error / reloaded state is visible — and the queue clears, so
 * dismissal is never blocked by a stalled batch).
 */
@Composable
private fun <T> PendingQueueEffect(
    pending: List<T>,
    mutationState: UiState<*>,
    onNext: (List<T>) -> Unit,
    onFinished: () -> Unit,
    onAborted: () -> Unit,
) {
    LaunchedEffect(mutationState) {
        if (pending.isEmpty()) return@LaunchedEffect
        when (mutationState) {
            is UiState.Success -> {
                val remaining = pending.drop(1)
                if (remaining.isEmpty()) {
                    onFinished()
                } else {
                    onNext(remaining)
                }
            }

            is UiState.Error, is UiState.Idle -> {
                onAborted()
            }

            is UiState.Loading -> {
                Unit
            }
        }
    }
}

/** D4 — per-open day-picker inputs: the cached load, the breakdown mutation state, covered ids. */
internal class DayPickerData(
    val state: UiState<List<RemittanceDayPickerEntryResponse>>,
    val mutationState: UiState<RemittanceDayBreakdownResponse>,
    val includedIds: Set<String>,
)

/** D4 — days-covered picker: effective statuses from #118 G4; already-remitted greyed out. */
@Composable
internal fun DayPickerDialog(
    data: DayPickerData,
    onLoad: () -> Unit,
    onAdd: (List<AddDayBreakdownRequest>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pending by remember { mutableStateOf<List<AddDayBreakdownRequest>>(emptyList()) }

    // One POST per selected day, advanced on each success; a failure or the VM's 403/409 Idle
    // reset clears the queue (the dialog stays open showing the error / the reloaded state).
    PickerPendingHost(
        pending = pending,
        mutationState = data.mutationState,
        onAdd = onAdd,
        onDismiss = onDismiss,
        onPendingChange = { pending = it },
    )

    AlertDialog(
        onDismissRequest = {
            if (data.mutationState !is UiState.Loading && pending.isEmpty()) {
                onDismiss()
            }
        },
        title = { Text("Add days") },
        text = {
            DayPickerBody(
                data = data,
                selectedIds = selectedIds,
                onLoad = onLoad,
                onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
            )
        },
        confirmButton = {
            DayPickerConfirmButton(
                data = data,
                pending = pending,
                selectedIds = selectedIds,
                onConfirm = { requests ->
                    pending = requests
                    selectedIds = emptySet()
                    onAdd(requests)
                },
            )
        },
        dismissButton = {
            IncomePickerDismissButton(mutationState = data.mutationState, onDismiss = onDismiss)
        },
    )
}

/** D4 — day-picker text column: load/error/empty/day rows + mutation error. */
@Composable
private fun DayPickerBody(
    data: DayPickerData,
    selectedIds: Set<String>,
    onLoad: () -> Unit,
    onToggle: (id: String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (val s = data.state) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onLoad) { Text("Retry") }
            }

            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState("No days in this date range")
                } else {
                    s.data.forEach { entry ->
                        DayPickerDayRow(
                            entry = entry,
                            includedIds = data.includedIds,
                            selectedIds = selectedIds,
                            onToggle = onToggle,
                        )
                    }
                }
            }
        }
        (data.mutationState as? UiState.Error)?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun DayPickerConfirmButton(
    data: DayPickerData,
    pending: List<AddDayBreakdownRequest>,
    selectedIds: Set<String>,
    onConfirm: (List<AddDayBreakdownRequest>) -> Unit,
) {
    TextButton(
        onClick = {
            val entriesLoaded = data.state is UiState.Success
            val addBlocked =
                data.mutationState is UiState.Loading || pending.isNotEmpty() ||
                    selectedIds.isEmpty() || !entriesLoaded
            if (addBlocked) {
                return@TextButton
            }
            onConfirm(
                selectedIds.map { id ->
                    AddDayBreakdownRequest(
                        id = Uuid.random().toString(),
                        branchDayId = id,
                    )
                },
            )
        },
        enabled =
            data.mutationState !is UiState.Loading && pending.isEmpty() && selectedIds.isNotEmpty(),
    ) {
        Text("Add")
    }
}
