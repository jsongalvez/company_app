package com.companyb.companyapp.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.audit.AuditLogTableResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// #479 — the audit-log filter-bar seam (#104 D4/D8), extracted from AuditLogScreen.kt so the
// file-function wall (TMF) stays honest: server-driven table dropdown, action dropdown,
// date/caller fields, the hoisted draft holder + saver, and the validate-on-apply mirror.
// Every entry point is already at or under the LongParameterList threshold.

// D4/D8 — filter bar: server-driven table dropdown (loading/error states + retry; never
// hardcode labels), action dropdown, date range from/to (inclusive Manila days, yyyy-MM-dd),
// caller-name text. Validation is light (format + ordering); the backend remains authoritative
// (400 → in-place error card). The draft state is hoisted to the screen so tab switches don't
// dispose the typed values.

// #686 — applied-filter count for the Filters badge. Blank/absent sides count as no filter;
// the backend stays authoritative (exact identifiers travel internally).
internal fun auditAppliedFilterCount(filters: AuditLogFilters): Int {
    var count = 0
    if (!filters.tableName.isNullOrBlank()) count++
    if (!filters.action.isNullOrBlank()) count++
    if (!filters.callerName.isNullOrBlank()) count++
    if (!filters.dateFrom.isNullOrBlank()) count++
    if (!filters.dateTo.isNullOrBlank()) count++
    return count
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditDateField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("yyyy-MM-dd") },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { pickerOpen = true }) {
                Text("Pick")
            }
        },
        modifier = modifier,
    )
    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = auditIsoToPickerMillis(value))
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        auditPickerMillisToIso(pickerState.selectedDateMillis)?.let(onValueChange)
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

// The Material3 DatePicker speaks epoch millis while audit filters speak yyyy-MM-dd Manila
// calendar dates (the remittance-picker precedent): seed and read back in UTC so confirming
// an unchanged date can never shift it a day.
@OptIn(ExperimentalTime::class)
internal fun auditIsoToPickerMillis(value: String): Long? {
    if (!DATE_PATTERN.matches(value.trim())) return null
    // #686 P4 HARD — pattern-valid but nonexistent dates (2026-02-30) must seed today, never
    // crash composition when the picker opens from typed input.
    val date = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: return null
    return date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}

@OptIn(ExperimentalTime::class)
private fun auditPickerMillisToIso(millis: Long?): String? {
    if (millis == null) return null
    return Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogDateCallerFields(draft: AuditLogFilterDraft) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AuditDateField(
            label = "From",
            value = draft.dateFrom,
            error = draft.dateFromError,
            onValueChange = {
                draft.dateFrom = it
                // Both errors clear: format is per-field, but the ordering violation spans
                // the pair — a stale To-side error must not outlive its cause.
                draft.dateFromError = null
                draft.dateToError = null
            },
            modifier = Modifier.weight(1f),
        )
        AuditDateField(
            label = "To",
            value = draft.dateTo,
            error = draft.dateToError,
            onValueChange = {
                draft.dateTo = it
                draft.dateToError = null
            },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.callerName,
            onValueChange = { draft.callerName = it },
            label = { Text("Changed by") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuditLogFilterBar(
    tables: UiState<List<AuditLogTableResponse>>,
    onRetryTables: () -> Unit,
    draft: AuditLogFilterDraft,
    onApply: (AuditLogFilters) -> Unit,
    applied: AuditLogFilters,
) {
    // #686 — record-type/actor/date-range sit behind Filters; review tabs and row branch
    // context stay visible. The badge counts applied filters; Clear resets to the unfiltered
    // browse. Exact identifiers travel internally; labels stay human-readable.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val appliedCount = auditAppliedFilterCount(applied)

    fun apply() {
        val (fromError, toError) = auditDateRangeErrors(draft.dateFrom, draft.dateTo)
        if (fromError != null || toError != null) {
            draft.dateFromError = fromError
            draft.dateToError = toError
            return
        }
        draft.dateFromError = null
        draft.dateToError = null
        onApply(
            AuditLogFilters(
                tableName = draft.selectedTableName,
                action = draft.selectedAction,
                callerName = draft.callerName.trim().takeIf { it.isNotBlank() },
                dateFrom = draft.dateFrom.trim().takeIf { it.isNotEmpty() },
                dateTo = draft.dateTo.trim().takeIf { it.isNotEmpty() },
            ),
        )
    }

    fun clear() {
        draft.reset()
        onApply(AuditLogFilters())
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (appliedCount > 0) "Filters ($appliedCount)" else "Filters")
            }
            if (appliedCount > 0) {
                TextButton(onClick = { clear() }) {
                    Text("Clear")
                }
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TableDropdown(
                    tables = tables,
                    selectedTableName = draft.selectedTableName,
                    onTableSelected = { draft.selectedTableName = it },
                    onRetryTables = onRetryTables,
                    modifier = Modifier.weight(1f),
                )
                ActionDropdown(
                    selectedAction = draft.selectedAction,
                    onActionSelected = { draft.selectedAction = it },
                    modifier = Modifier.weight(1f),
                )
            }
            AuditLogDateCallerFields(draft)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    TextButton(onClick = { apply() }) {
                        Text("Apply")
                    }
                    TextButton(onClick = { clear() }) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

// Hoisted filter-bar draft (see AuditLogScreen) — plain state holder, remembered at screen scope.
// Mutation lives on the holder (#142 BpDraftState precedent); the composable only reads + applies.
internal class AuditLogFilterDraft {
    var selectedTableName by mutableStateOf<String?>(null)
    var selectedAction by mutableStateOf<String?>(null)
    var callerName by mutableStateOf("")
    var dateFrom by mutableStateOf("")
    var dateTo by mutableStateOf("")
    var dateFromError by mutableStateOf<String?>(null)
    var dateToError by mutableStateOf<String?>(null)

    fun reset() {
        selectedTableName = null
        selectedAction = null
        callerName = ""
        dateFrom = ""
        dateTo = ""
        dateFromError = null
        dateToError = null
    }
}

private const val FILTER_DATE_FROM_INDEX = 3
private const val FILTER_DATE_TO_INDEX = 4

internal val AuditLogFilterDraftSaver =
    Saver<AuditLogFilterDraft, List<String?>>(
        save = { draft ->
            listOf(
                draft.selectedTableName,
                draft.selectedAction,
                draft.callerName,
                draft.dateFrom,
                draft.dateTo,
                // Field-level date errors deliberately excluded: a stale validation message
                // must not resurrect across a save/restore cycle.
            )
        },
        restore = { values ->
            if (values.size == 5) {
                AuditLogFilterDraft().apply {
                    selectedTableName = values[0]
                    selectedAction = values[1]
                    callerName = values[2] ?: ""
                    dateFrom = values[FILTER_DATE_FROM_INDEX] ?: ""
                    dateTo = values[FILTER_DATE_TO_INDEX] ?: ""
                }
            } else {
                // Shape drift (code updated between save and restore) — degrade to defaults
                // rather than crash the composition (TimestampFormat precedent).
                logWarn("AuditLogScreen", "filter draft saver shape drift: ${values.size} values")
                AuditLogFilterDraft()
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableDropdownErrorEffect(tables: UiState<List<AuditLogTableResponse>>) {
    LaunchedEffect(tables) {
        if (tables is UiState.Error) {
            logWarn("AuditLogScreen", "tablesState=Error: ${tables.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableDropdown(
    tables: UiState<List<AuditLogTableResponse>>,
    selectedTableName: String?,
    onTableSelected: (String?) -> Unit,
    onRetryTables: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isError = tables is UiState.Error
    val isIdle = tables is UiState.Idle
    val isLoading = tables is UiState.Loading
    TableDropdownErrorEffect(tables)
    val tableOptions = (tables as? UiState.Success<List<AuditLogTableResponse>>)?.data.orEmpty()
    val selectedLabel =
        tableOptions.find { it.tableName == selectedTableName }?.label ?: "All tables"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (isError || isIdle) {
                onRetryTables()
            } else if (!isLoading) {
                expanded = !expanded
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value =
                when {
                    isError -> "Tables unavailable — tap to retry"
                    isLoading || isIdle -> "Loading tables…"
                    else -> selectedLabel
                },
            onValueChange = {},
            readOnly = true,
            label = { Text("Record type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = !isLoading && !isIdle,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All tables") },
                onClick = {
                    onTableSelected(null)
                    expanded = false
                },
            )
            tableOptions.forEach { table ->
                DropdownMenuItem(
                    text = { Text("${table.label} (${table.tableName})") },
                    onClick = {
                        onTableSelected(table.tableName)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(
    selectedAction: String?,
    onActionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            // #686 — human verbs in the UI, exact enum names internally (the pill/row vocabulary).
            value = selectedAction?.let { auditActionDisplayName(it) } ?: "All actions",
            onValueChange = {},
            readOnly = true,
            label = { Text("Action") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All actions") },
                onClick = {
                    onActionSelected(null)
                    expanded = false
                },
            )
            AUDIT_ACTIONS.forEach { action ->
                DropdownMenuItem(
                    text = { Text(auditActionDisplayName(action)) },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

// #686 — filter display verbs share the pill vocabulary; unknown names stay faithful and exact
// identifiers travel internally.
internal fun auditActionDisplayName(raw: String): String =
    runCatching { auditOperationLabel(AuditAction.valueOf(raw)) }.getOrDefault(raw)

internal const val AUDIT_DATE_FORMAT_ERROR = "Dates must be yyyy-MM-dd"

internal const val AUDIT_DATE_ORDER_ERROR = "From must be before To"

// #383 — validate-on-apply, surfaced per field on the inputs themselves. Blank = no filter
// (absent); the backend stays authoritative for real calendar validity (a lexically valid
// non-existent date 400s into the error card). The ordering violation lands on To — From is
// where the range starts.
internal fun auditDateRangeErrors(
    fromRaw: String,
    toRaw: String,
): Pair<String?, String?> {
    val from = fromRaw.trim().takeIf { it.isNotEmpty() }
    val to = toRaw.trim().takeIf { it.isNotEmpty() }
    val fromError = if (from != null && !DATE_PATTERN.matches(from)) AUDIT_DATE_FORMAT_ERROR else null
    val toError =
        when {
            to != null && !DATE_PATTERN.matches(to) -> AUDIT_DATE_FORMAT_ERROR
            fromError == null && from != null && to != null && from > to -> AUDIT_DATE_ORDER_ERROR
            else -> null
        }
    return fromError to toError
}

@Composable
internal expect fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
)

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

// #116 precedent: backend enums serialize as name strings; the frontend mirrors the vocabulary
// in one place so the dropdown, pill colors, and diff rendering share it (D4's "never hardcode
// labels" targets table labels; the action list is the backend enum contract).
private val AUDIT_ACTIONS = AuditAction.entries.map { it.name }
