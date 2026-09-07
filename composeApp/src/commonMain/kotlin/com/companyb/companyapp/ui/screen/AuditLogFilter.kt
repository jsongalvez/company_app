package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AuditLogFilters

// #479 — the audit-log filter-bar seam (#104 D4/D8), extracted from AuditLogScreen.kt so the
// file-function wall (TMF) stays honest: server-driven table dropdown, action dropdown,
// date/caller fields, the hoisted draft holder + saver, and the validate-on-apply mirror.
// Every entry point is already at or under the LongParameterList threshold.

// D4/D8 — filter bar: server-driven table dropdown (loading/error states + retry; never
// hardcode labels), action dropdown, date range from/to (inclusive Manila days, yyyy-MM-dd),
// caller-name text. Validation is light (format + ordering); the backend remains authoritative
// (400 → in-place error card). The draft state is hoisted to the screen so tab switches don't
// dispose the typed values.

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
        OutlinedTextField(
            value = draft.dateFrom,
            onValueChange = {
                draft.dateFrom = it
                // Both errors clear: format is per-field, but the ordering violation spans
                // the pair — a stale To-side error must not outlive its cause.
                draft.dateFromError = null
                draft.dateToError = null
            },
            label = { Text("From") },
            placeholder = { Text("yyyy-MM-dd") },
            isError = draft.dateFromError != null,
            supportingText = { draft.dateFromError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.dateTo,
            onValueChange = {
                draft.dateTo = it
                draft.dateToError = null
            },
            label = { Text("To") },
            placeholder = { Text("yyyy-MM-dd") },
            isError = draft.dateToError != null,
            supportingText = { draft.dateToError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.callerName,
            onValueChange = { draft.callerName = it },
            label = { Text("Caller") },
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
) {
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

    fun reset() {
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
                TextButton(onClick = { reset() }) {
                    Text("Reset")
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
            label = { Text("Table") },
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
            value = selectedAction ?: "All actions",
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
                    text = { Text(action) },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

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
