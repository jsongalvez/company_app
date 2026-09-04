package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #403 — the REMITTED-day editor. The backend rejects any write on a REMITTED day without a
 * non-blank audit reason (BranchDayService.assertEditableState), so the desktop cell editor
 * becomes this dialog whenever today's day status reads REMITTED: the value control (no
 * auto-commit — the Confirm button owns it) plus a required reason input with inline
 * validation before submit. Non-REMITTED days keep the plain inline editors.
 */
@Composable
internal fun RemittedReasonDialog(
    edit: DashboardEditState,
    canEdit: Boolean = true,
    statusValues: List<String>,
    actions: RemittedEditActions,
) {
    // #477 — the value control without auto-commit (the Confirm button owns it);
    // the reason must be collected before submit.
    val inline = InlineEditActions(actions.onDraftChange, {}, actions.onDiscard)
    AlertDialog(
        onDismissRequest = actions.onDiscard,
        title = { Text("Reason required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text =
                        "This session's day has been remitted. Describe why you are " +
                            "correcting it — the note goes to the audit log.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                EditControl(edit, canEdit, statusValues, inline, autoCommit = false)
                OutlinedTextField(
                    value = edit.reason,
                    onValueChange = actions.onReasonChange,
                    label = { Text("Reason") },
                    singleLine = true,
                    enabled = canEdit && !edit.inFlight,
                    isError = edit.error != null,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                EditStatusLine(edit = edit, onReload = actions.onReload)
            }
        },
        confirmButton = {
            Button(onClick = actions.onCommit, enabled = canEdit && !edit.inFlight) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDiscard) {
                Text("Cancel")
            }
        },
    )
}
