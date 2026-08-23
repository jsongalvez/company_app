package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #382 — the pane's dialog hosts, extracted from [SessionDetailPane] verbatim: each host
 * renders nothing while its target slot is empty and clears the slot before invoking the
 * mutation (the confirm-first ordering the pane relied on).
 */

@Composable
internal fun RemoveConcernDialogHost(
    target: ConcernResponse?,
    mutating: Boolean,
    onConfirmed: (concernId: String) -> Unit,
    onCleared: () -> Unit,
) {
    target?.let { concern ->
        ConfirmRemoveDialog(
            title = "Remove concern?",
            body = "\"${concern.label}\" will be detached from this session.",
            inFlight = mutating,
            onConfirm = {
                onCleared()
                onConfirmed(concern.id)
            },
            onDismiss = onCleared,
        )
    }
}

@Composable
internal fun RemovePractitionerDialogHost(
    target: DashboardPractitionerResponse?,
    mutating: Boolean,
    onConfirmed: (practitionerId: String) -> Unit,
    onCleared: () -> Unit,
) {
    target?.let { practitioner ->
        ConfirmRemoveDialog(
            title = "Remove practitioner?",
            body = "${practitioner.displayName} will be removed from this session.",
            inFlight = mutating,
            onConfirm = {
                onCleared()
                onConfirmed(practitioner.practitionerId)
            },
            onDismiss = onCleared,
        )
    }
}

@Composable
internal fun RemarksDialogHost(
    target: DashboardPractitionerResponse?,
    mutating: Boolean,
    onConfirmed: (practitionerId: String, remarks: String?) -> Unit,
    onCleared: () -> Unit,
) {
    target?.let { practitioner ->
        PractitionerRemarksDialog(
            practitioner = practitioner,
            inFlight = mutating,
            onConfirm = { remarks ->
                onCleared()
                // Blank remarks clear the field server-side (#382).
                onConfirmed(practitioner.practitionerId, remarks.trim().ifBlank { null })
            },
            onDismiss = onCleared,
        )
    }
}

/** Directory picker over branch members minus the session's current roster ids. */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun AddPractitionerDialogHost(
    visible: Boolean,
    session: DashboardSessionResponse,
    sessionVm: SessionViewModel,
    members: UiState<List<BranchMemberResponse>>,
    onClose: () -> Unit,
) {
    if (!visible) return
    LaunchedEffect(session.branchId) { sessionVm.loadBranchMembers(session.branchId) }
    // Ids survive mergeRosterNames, so the merged row filters the directory exactly.
    val rosterIds = session.practitioners.map { it.practitionerId }.toSet()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add practitioner") },
        text = {
            Column {
                if (members is UiState.Loading) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(Spacing.lg))
                    }
                }
                (members as? UiState.Error)?.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { sessionVm.loadBranchMembers(session.branchId) }) { Text("Retry") }
                }
                val items = (members as? UiState.Success)?.data?.filterNot { it.id in rosterIds }.orEmpty()
                items.forEach { member ->
                    TextButton(onClick = {
                        onClose()
                        sessionVm.addPractitioner(
                            session.id,
                            // Idempotency key minted at submit (BR §390–392); duplicate adds are
                            // idempotent server-side anyway.
                            AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = member.id),
                        )
                    }) {
                        Text(member.displayName, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (members !is UiState.Loading && members !is UiState.Error && items.isEmpty()) {
                    Text("Nothing available", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

/**
 * #382 — promote the session's free-text "other concerns" note into a tracked catalog
 * concern; the backend clears the note atomically with the new link.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun PromoteOtherDialogHost(
    visible: Boolean,
    session: DashboardSessionResponse,
    sessionVm: SessionViewModel,
    mutating: Boolean,
    onClose: () -> Unit,
) {
    if (!visible) return
    PromoteConcernDialog(
        initialLabel = session.otherConcerns.orEmpty(),
        inFlight = mutating,
        onConfirm = { label ->
            onClose()
            sessionVm.promoteConcern(
                session.id,
                PromoteConcernRequest(id = Uuid.random().toString(), label = label.trim()),
            )
        },
        onDismiss = onClose,
    )
}

/**
 * #406 — void the session: the required reason is recorded server-side (blank is rejected)
 * and the idempotency key is minted at submit (the #382 add-practitioner shape).
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun VoidSessionDialogHost(
    visible: Boolean,
    mutating: Boolean,
    onConfirmed: (VoidSessionRequest) -> Unit,
    onDismissed: () -> Unit,
) {
    if (!visible) return
    ReasonConfirmDialog(
        title = "Void session?",
        caption = "The session stays on record but leaves commissions and reporting totals.",
        confirmLabel = "Void",
        destructive = true,
        inFlight = mutating,
        onConfirm = { reason ->
            onDismissed()
            onConfirmed(VoidSessionRequest(id = Uuid.random().toString(), voidReason = reason.trim()))
        },
        onDismiss = onDismissed,
    )
}

/** #406 — reverse a void; the reversal reason is likewise required. */
@Composable
internal fun UnvoidSessionDialogHost(
    visible: Boolean,
    mutating: Boolean,
    onConfirmed: (UnvoidSessionRequest) -> Unit,
    onDismissed: () -> Unit,
) {
    if (!visible) return
    ReasonConfirmDialog(
        title = "Unvoid session?",
        caption = "The session returns to commissions and reporting totals.",
        confirmLabel = "Unvoid",
        destructive = false,
        inFlight = mutating,
        onConfirm = { reason ->
            onDismissed()
            onConfirmed(UnvoidSessionRequest(unvoidedReason = reason.trim()))
        },
        onDismiss = onDismissed,
    )
}

/** #406 — shared confirm-with-required-reason dialog (server rejects blank reasons). */
@Composable
private fun ReasonConfirmDialog(
    title: String,
    caption: String,
    confirmLabel: String,
    destructive: Boolean,
    inFlight: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Reason") },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = !inFlight && draft.isNotBlank()) {
                val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Text(confirmLabel, color = color)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConfirmRemoveDialog(
    title: String,
    body: String,
    inFlight: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !inFlight) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PractitionerRemarksDialog(
    practitioner: DashboardPractitionerResponse,
    inFlight: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(practitioner.remarks.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remarks for ${practitioner.displayName}") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                enabled = !inFlight,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled =
                    !inFlight && draft.trim() != practitioner.remarks?.trim().orEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PromoteConcernDialog(
    initialLabel: String,
    inFlight: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote to tracked concern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Creates a structured concern and clears the free-text note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = !inFlight && draft.isNotBlank()) {
                Text("Promote")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
