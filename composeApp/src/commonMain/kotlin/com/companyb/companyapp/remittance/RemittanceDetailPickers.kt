package com.companyb.companyapp.remittance

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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
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

/** #677 — the picker-entry id behind one queued line write (never the write id). */
internal fun incomeRequestEntryId(request: CreateRemittanceLineRequest): String =
    request.sessionId ?: request.productSaleId ?: request.id

/**
 * D3 — per-open picker session: tick selection + editable amounts + the one-POST-per-row
 * add queue. Owns the mutation interaction so the dialog composables stay thin.
 *
 * #677 — confirmed/failed batch lifecycle: a confirmed row is labeled Added and never
 * re-adds; a failed batch returns its unconfirmed queue to ticked selection for retry
 * (the dialog closes only on explicit Done/Cancel, never on batch completion).
 */
internal class IncomePickerSession<T>(
    val spec: IncomePickerSpec<T>,
) {
    var selectedIds by mutableStateOf(emptySet<String>())
    var amounts by mutableStateOf(emptyMap<String, String>())
    var pending by mutableStateOf(emptyList<CreateRemittanceLineRequest>())
    var confirmedIds by mutableStateOf(emptySet<String>())
    var batchError by mutableStateOf<String?>(null)

    fun toggle(
        entry: T,
        selected: Boolean,
        included: Boolean,
    ) {
        if (included) return
        val id = spec.idOf(entry)
        // #677 — the in-flight head cannot be re-ticked mid-batch (its POST owns it;
        // re-ticking would paint a bogus failed label via statusText).
        if (pending.any { incomeRequestEntryId(it) == id }) return
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
        // #490 — a selected id missing from a reloaded same-range cache must not throw in the
        // click handler: fall back to the amount captured at toggle time, drop only ids with
        // neither. #677 — confirmed rows never re-add.
        val requests =
            (selectedIds - confirmedIds).mapNotNull { id ->
                val amount = amounts[id] ?: entries.find { spec.idOf(it) == id }?.let { spec.amountOf(it) }
                amount?.let { spec.toRequest(id, it) }
            }
        if (requests.isEmpty()) return null
        batchError = null
        pending = requests
        selectedIds = emptySet()
        return requests
    }

    /** Marks the in-flight head confirmed and drops it (the queue effect sends the rest). */
    fun confirmFirst() {
        pending.firstOrNull()?.let { confirmedIds = confirmedIds + incomeRequestEntryId(it) }
        pending = pending.drop(1)
    }

    /**
     * Returns the unconfirmed queue to ticked selection for retry. An Idle abort is the
     * VM's 403/409 terminal reset (the draft reloaded underneath) — say so; an Error
     * abort already renders the mutation message in the dialog body.
     */
    fun restorePending(terminal: UiState<*>) {
        selectedIds = selectedIds + pending.map(::incomeRequestEntryId)
        pending = emptyList()
        batchError =
            if (terminal is UiState.Idle) {
                "The draft changed elsewhere — failed rows stayed selected for retry."
            } else {
                null
            }
    }

    /** One-line batch status: partial success is labeled, failures stay actionable. */
    fun statusText(): String? {
        if (confirmedIds.isEmpty() && batchError == null) return null
        val added = if (confirmedIds.isNotEmpty()) "Added ${confirmedIds.size}" else null
        val failed =
            if (selectedIds.isNotEmpty() && confirmedIds.isNotEmpty()) {
                "${selectedIds.size} failed — still selected for retry"
            } else {
                null
            }
        return listOfNotNull(added, failed, batchError).joinToString(" · ")
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
                type = com.companyb.companyapp.contracts.remittance.RemittanceLineType.SESSION,
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
                type = com.companyb.companyapp.contracts.remittance.RemittanceLineType.PRODUCT_SALE,
                productSaleId = id,
                amount = amount,
            )
        },
    )

/** D3 — sessions picker over the shared income picker. */
@Composable
internal fun SessionPickerDialog(
    rangeKey: String,
    data: IncomePickerData<RemittanceSessionPickerEntryResponse>,
    flow: IncomePickerFlow,
) {
    val session = remember(rangeKey) { IncomePickerSession(SessionIncomePicker) }
    IncomePickerDialog(session = session, data = data, flow = flow)
}

/** D3 — product-sales picker over the shared income picker. */
@Composable
internal fun ProductSalePickerDialog(
    rangeKey: String,
    data: IncomePickerData<RemittanceProductSalePickerEntryResponse>,
    flow: IncomePickerFlow,
) {
    val session = remember(rangeKey) { IncomePickerSession(ProductSaleIncomePicker) }
    IncomePickerDialog(session = session, data = data, flow = flow)
}

/**
 * D3 — shared tick-to-include income picker (sessions + product sales): per-row selection
 * with prefilled editable amounts, one mutation POST per selected row via [PendingQueueEffect].
 * #677 — the dialog closes only on explicit Done/Cancel: batch completion labels the
 * outcome (partial success included) and leaves the dialog open.
 */
@Composable
private fun <T> IncomePickerDialog(
    session: IncomePickerSession<T>,
    data: IncomePickerData<T>,
    flow: IncomePickerFlow,
) {
    // #677 — Done/Cancel are the only exits: completion confirms rows in place (no
    // auto-dismiss), failure restores the unconfirmed queue to selection for retry.
    val dismissible = data.mutationState !is UiState.Loading && session.pending.isEmpty()
    PickerPendingHost(
        pending = session.pending,
        mutationState = data.mutationState,
        onAdd = flow.onAdd,
        onPendingChange = { session.pending = it },
        onAdvanced = { session.confirmFirst() },
        onAborted = { session.restorePending(data.mutationState) },
    )
    AlertDialog(
        onDismissRequest = {
            if (dismissible) flow.onDismiss()
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
        dismissButton = {
            IncomePickerDoneButton(
                session = session,
                mutationState = data.mutationState,
                onDismiss = flow.onDismiss,
            )
        },
    )
}

@Composable
private fun <T> PickerPendingHost(
    pending: List<T>,
    mutationState: UiState<*>,
    onAdd: (List<T>) -> Unit,
    onPendingChange: (List<T>) -> Unit,
    onAdvanced: () -> Unit = {},
    onAborted: () -> Unit = {},
) {
    // One POST per selected row, advanced on each success; completion confirms the batch
    // in place (Done/Cancel are the only exits — never auto-dismiss). A failure or the
    // VM's 403/409 Idle reset restores the unconfirmed queue to selection for retry.
    PendingQueueEffect(
        pending = pending,
        mutationState = mutationState,
        onNext = { remaining ->
            onAdvanced()
            onPendingChange(remaining)
            onAdd(remaining)
        },
        onFinished = {
            onAdvanced()
            onPendingChange(emptyList())
        },
        onAborted = {
            onAborted()
            onPendingChange(emptyList())
        },
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
private fun <T> IncomePickerDoneButton(
    session: IncomePickerSession<T>,
    mutationState: UiState<*>,
    onDismiss: () -> Unit,
) {
    // #677 — Done/Cancel are the only exits: Done once rows confirmed, Cancel before.
    TextButton(
        onClick = onDismiss,
        enabled = mutationState !is UiState.Loading && session.pending.isEmpty(),
    ) {
        Text(if (session.confirmedIds.isEmpty()) "Cancel" else "Done")
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
                        // #677 — server-confirmed and batch-confirmed rows alike render
                        // Added and never re-add; the in-flight head renders Adding.
                        val included = id in data.includedIds || id in session.confirmedIds
                        val adding = session.pending.any { incomeRequestEntryId(it) == id }
                        val selected = id in session.selectedIds
                        PickerEntryRow(
                            model =
                                PickerEntryRowModel(
                                    label = spec.mainLabel(entry),
                                    secondary = if (adding) "Adding…" else spec.secondaryLabel(entry),
                                    amountText = peso(spec.amountOf(entry)),
                                    selected = selected,
                                    enabled = !included && !adding,
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
        // #677 — partial success is labeled in place; failures stay selected for retry.
        session.statusText()?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shared add-queue driver for the tick-to-include pickers (D3/D4): one mutation per selected
 * row, advanced on each success with the batch confirmed in place; an Error or the VM's
 * 403/409 Idle reset restores the unconfirmed queue to selection for retry (the dialog
 * stays open — the error / reloaded state is visible — and Done/Cancel are the only exits).
 *
 * #677 — the dialog wiring stays whole (pending + mutation + advance + abort travel
 * together per picker); no DTO wrapper — the hooks are the ownership decision.
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
    rangeKey: String,
    data: DayPickerData,
    onLoad: () -> Unit,
    onAdd: (List<AddDayBreakdownRequest>) -> Unit,
    onDismiss: () -> Unit,
) {
    // #483 — selections key on the loaded range: a header range edit resets ticked days
    // instead of silently keeping out-of-range selections. #490 — the in-flight add queue
    // keys the same way: old-range requests never survive a range change with the dialog
    // composed. #677 — batch-confirmed days render Added and never re-add; failures
    // return to selection for retry; the dialog closes only on Done/Cancel.
    var selectedIds by remember(rangeKey) { mutableStateOf(emptySet<String>()) }
    var pending by remember(rangeKey) { mutableStateOf<List<AddDayBreakdownRequest>>(emptyList()) }
    var confirmedIds by remember(rangeKey) { mutableStateOf(emptySet<String>()) }
    var batchError by remember(rangeKey) { mutableStateOf<String?>(null) }

    // One POST per selected day, advanced on each success; completion confirms in place
    // (no auto-dismiss) and failure restores the unconfirmed queue to selection.
    PickerPendingHost(
        pending = pending,
        mutationState = data.mutationState,
        onAdd = onAdd,
        onPendingChange = { pending = it },
        onAdvanced = {
            pending.firstOrNull()?.let { confirmedIds = confirmedIds + it.branchDayId }
        },
        onAborted = {
            selectedIds = selectedIds + pending.map { it.branchDayId }
            pending = emptyList()
            batchError =
                if (data.mutationState is UiState.Idle) {
                    "The draft changed elsewhere — failed days stayed selected for retry."
                } else {
                    null
                }
        },
    )

    val dismissible = data.mutationState !is UiState.Loading && pending.isEmpty()
    AlertDialog(
        onDismissRequest = {
            if (dismissible) {
                onDismiss()
            }
        },
        title = { Text("Add days") },
        text = {
            DayPickerBody(
                data = data,
                selectedIds = selectedIds,
                confirmedIds = confirmedIds,
                addingIds = pending.map { it.branchDayId }.toSet(),
                batchError = batchError,
                onLoad = onLoad,
                onToggle = { id ->
                    // #677 — the in-flight head cannot be re-ticked mid-batch.
                    if (pending.none { it.branchDayId == id }) {
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    }
                },
            )
        },
        confirmButton = {
            DayPickerConfirmButton(
                data = data,
                pending = pending,
                selectedIds = selectedIds - confirmedIds,
                onConfirm = { requests ->
                    batchError = null
                    pending = requests
                    selectedIds = emptySet()
                    onAdd(requests)
                },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = dismissible,
            ) {
                Text(if (confirmedIds.isEmpty()) "Cancel" else "Done")
            }
        },
    )
}

/** D4 — day-picker text column: load/error/empty/day rows + mutation error.
 * #677 — the seven legs (cached load, selection, confirmed/adding sets, batch error,
 * load + toggle) travel together per open; kept whole per the coherent-owner rule. */
@Composable
private fun DayPickerBody(
    data: DayPickerData,
    selectedIds: Set<String>,
    confirmedIds: Set<String>,
    addingIds: Set<String>,
    batchError: String?,
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
                    // #677 — batch-confirmed days render Added alongside server-included ones;
                    // the in-flight head renders Adding.
                    val includedIds = data.includedIds + confirmedIds
                    s.data.forEach { entry ->
                        DayPickerDayRow(
                            entry = entry,
                            includedIds = includedIds,
                            addingIds = addingIds,
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
        // #677 — partial success is labeled in place; failures stay selected for retry.
        val status =
            listOfNotNull(
                "Added ${confirmedIds.size}".takeIf { confirmedIds.isNotEmpty() },
                "${selectedIds.size} failed — still selected for retry"
                    .takeIf { selectedIds.isNotEmpty() && confirmedIds.isNotEmpty() },
                batchError,
            ).joinToString(" · ").takeIf { it.isNotEmpty() }
        status?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
