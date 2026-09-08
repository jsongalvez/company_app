package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

/** #462 LPL burn — the 3 per-day export params as one object (expect + 3 actuals + mobile
 * helper drop 7 params to 5; data classes are LPL-free, the SlotOrderCallbacks precedent). */
internal data class DayExport(
    val onExportDay: (String) -> Unit,
    val downloadStates: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
    /**
     * #678 — the Edit-day entry lives in the selected-day header (branch/date/state named
     * beside the action), never as a global toolbar toggle. Null hides it (relief and
     * read-only surfaces).
     */
    val onEditDay: (() -> Unit)? = null,
    val canEditDay: Boolean = false,
    /** #678 — the viewed branch name, so compact card/dialog titles name the day's branch. */
    val branchName: String = "",
)

/** #479 LPL burn — the export-note pair as one object ([DayExport] is the +callback shape). */
internal data class FinanceExportUi(
    val downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
)

/** #479 LPL burn — the day-editor section state cluster (mutation errors, flight, gates). */
internal data class FinanceSectionUi(
    val errors: Map<String, String>,
    val inFlight: Set<String>,
    val readOnly: Boolean,
    val conflicts: Set<String>,
)

/** #479 LPL burn — assign-section state: [FinanceSectionUi]'s cluster plus the ASSIGN gate. */
internal data class FinanceAssignUi(
    val errors: Map<String, String>,
    val inFlight: Set<String>,
    val readOnly: Boolean,
    val conflicts: Set<String>,
    val canAssign: Boolean,
)

/** #479 LPL burn — the save-in-progress + error pair dialogs render as one object. */
internal data class DialogProgress(
    val busy: Boolean,
    val error: String?,
)

@Composable
internal fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        if (showAction) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** #479 LPL burn — the static reason-dialog copy + progress as one object. */
internal data class ReasonDialogSpec(
    val title: String,
    val message: String,
    val requireReason: Boolean,
    val progress: DialogProgress,
)

@Composable
internal fun ReasonDialog(
    spec: ReasonDialogSpec,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val reasonBlank = spec.requireReason && reason.isBlank()
    AlertDialog(
        onDismissRequest = { if (!spec.progress.busy) onDismiss() },
        title = { Text(spec.title) },
        text = {
            Column {
                Text(
                    text = spec.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(if (spec.requireReason) "Reason" else "Reason (optional)") },
                    singleLine = true,
                    isError = reasonBlank,
                )
                if (spec.progress.error != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = spec.progress.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim().ifBlank { null }) },
                enabled = !spec.progress.busy && !reasonBlank,
            ) {
                Text(if (spec.progress.busy) "Saving…" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !spec.progress.busy) { Text("Cancel") }
        },
    )
}

@Composable
internal fun PublicReportsSection(
    export: FinanceExportUi,
    onExport: (String, String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = "Public reports",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Provincial Tour / Medical Mission — downloadable by any logged-in user (#128).",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        listOf("provincial" to "Provincial Tour", "medical-mission" to "Medical Mission").forEach { (kind, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ExportMenu(
                    baseKey = "public:$kind",
                    onExport = { format -> onExport(kind, format) },
                    errors = export.exportErrors,
                    downloads = export.downloads,
                )
            }
        }
    }
}

@Composable
internal fun ExportMenu(
    baseKey: String,
    onExport: (String) -> Unit,
    errors: Map<String, String>,
    downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>> = emptyMap(),
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    // #678 — export is a menu of the existing formats: one trigger keeps the stable
    // header narrow at 390dp; the download itself still flows through saveDownload
    // (no fake completion before the native result).
    val busy = exportFormats.any { (format, _) -> downloads["$baseKey:$format"] is UiState.Loading }
    Column {
        Box {
            TextButton(
                onClick = { open = true },
                enabled = enabled && !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(Spacing.sm).height(Spacing.sm),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Export")
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                exportFormats.forEach { (format, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            open = false
                            onExport(format)
                        },
                    )
                }
            }
        }
        // #678 — a local failure explains itself beside the trigger with an explicit retry —
        // the report underneath is never replaced.
        exportFormats.forEach { (format, label) ->
            val error = errors["$baseKey:$format"]
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$label: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = { onExport(format) }) { Text("Retry") }
                }
            }
        }
    }
}

private val exportFormats = listOf("csv" to "CSV", "pdf" to "PDF")
