package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #351 — the relief-access surface on the session dashboard, both BR sides:
 *
 * - **Requests targeting me** (any checked-in user): PENDING rows show explicit Grant/Deny;
 *   a resolved row stops rendering as actionable (the invite received-row shape).
 * - **My access requests** (relief users only): outcome chips per BR ~110–114 — PENDING
 *   waits, GRANTED reads "active until 04:00 (Manila)", DENIED informs; each day needs a
 *   fresh request, so the picker stays available after resolution.
 *
 * Visibility rule: the card renders only for relief users or callers with an incoming
 * pending row — users neither requesting nor targeted see neither control nor surface
 * (server-side row scoping backs this).
 */
@Composable
fun ReliefAccessCard(
    viewModel: ReliefAccessViewModel,
    branchDayId: String,
    currentUserId: String?,
    isReliefUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val freshest by viewModel.freshestRequests.collectAsState()
    val grantState by viewModel.grantResult.collectAsState()
    val denyState by viewModel.denyResult.collectAsState()
    val requestResult by viewModel.requestResult.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    val rows = freshest.orEmpty()
    val incomingPending = rows.incomingPending(currentUserId)
    val outgoing = rows.outgoing(currentUserId)

    if (!isReliefUser && incomingPending.isEmpty()) return

    // Surface action failures inline (the invite screens' inline-error shape); actions write
    // Unit states, so the keep-last list itself never drops to Error for them.
    val actionError =
        listOfNotNull(grantState as? UiState.Error, denyState as? UiState.Error, requestResult as? UiState.Error)
            .firstOrNull()
            ?.message

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        SectionTitle("Relief access")

        if (incomingPending.isNotEmpty()) {
            IncomingSection(viewModel, branchDayId, incomingPending, grantState, denyState)
        }

        if (isReliefUser) {
            OutgoingSection(outgoing, onShowPicker = { showPicker = true })
        }
    }

    if (showPicker) {
        CandidatePickerDialog(
            viewModel = viewModel,
            branchDayId = branchDayId,
            onDismiss = { showPicker = false },
        )
    }

    // Surface action failures once (the invite screens' inline-error shape).
    if (actionError != null) {
        Text(
            text = actionError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun IncomingSection(
    viewModel: ReliefAccessViewModel,
    branchDayId: String,
    incomingPending: List<ReliefAccessResponse>,
    grantState: UiState<Unit>,
    denyState: UiState<Unit>,
) {
    CardSection {
        incomingPending.forEach { row ->
            IncomingRequestRow(
                row = row,
                busy = grantState is UiState.Loading || denyState is UiState.Loading,
                onGrant = { viewModel.grantAccess(row.id, branchDayId) },
                onDeny = { viewModel.denyAccess(row.id, branchDayId) },
            )
        }
    }
}

@Composable
private fun OutgoingSection(
    outgoing: List<ReliefAccessResponse>,
    onShowPicker: () -> Unit,
) {
    CardSection {
        outgoing.forEach { row -> OutgoingRequestRow(row) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onShowPicker,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(if (outgoing.isEmpty()) "Request edit access" else "Request again")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = InkSubtle,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun CardSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        shape = RoundedCornerShape(CornerRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            content()
        }
    }
}

/** Checked-in-user picker (BR: the relief user selects a currently checked-in user). */
@Composable
private fun CandidatePickerDialog(
    viewModel: ReliefAccessViewModel,
    branchDayId: String,
    onDismiss: () -> Unit,
) {
    val candidates by viewModel.candidates.collectAsState()
    LaunchedEffect(branchDayId) { viewModel.loadCandidates(branchDayId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request edit access from") },
        text = {
            when (val state = candidates) {
                is UiState.Loading -> {
                    CircularProgressIndicator()
                }

                is UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }

                is UiState.Idle -> {
                    Text("Loading…")
                }

                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Text("No other user is checked in at this branch today.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            state.data.forEach { candidate ->
                                TextButton(
                                    onClick = {
                                        viewModel.requestAccess(
                                            ReliefAccessRequest(
                                                requestId = newRequestId(),
                                                branchDayId = branchDayId,
                                                targetUserId = candidate.userId,
                                            ),
                                            branchDayId,
                                        )
                                        onDismiss()
                                    },
                                ) {
                                    Text(candidate.displayName)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalUuidApi::class)
private fun newRequestId(): String = Uuid.random().toString()
