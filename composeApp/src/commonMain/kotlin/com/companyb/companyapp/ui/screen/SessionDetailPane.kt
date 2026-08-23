package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.state.hasDayGrant
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #382 — the stateful session-detail surface: [SessionDetailContent] plus the post-create
 * concern/practitioner affordances and their dialogs. One implementation serves BOTH hosts —
 * the desktop master-detail inline pane and the mobile pushed SessionDetail route (#152's
 * byte-identical-content rule, extended to the editable path).
 *
 * A side-loaded [SessionViewModel] owns the roster (`GET .../practitioners`) and every
 * mutation; the authoritative row arrives via the [session] param and is reloaded by
 * [refreshSession] after each landing — pessimistic model (ADR-0022): nothing commits
 * locally, server truth repaints. ANY non-success mutation landing also calls
 * [refreshSession]: a 409/version conflict reloads instead of wedging, a 403 revocation
 * repaints the affordance set against refreshed capabilities, and a day-closed 4xx surfaces
 * inline while the read-only truth stays on screen.
 *
 * The edit gate mirrors the backend's branch-or-day filter client-side: strict BRANCH-context
 * `EDIT_BRANCH_DATA` at the session's branch, OR an active day grant (the row carries no
 * day id, so that leg is context-type-wide; a wrong-context attempt gets the authoritative
 * 403 and vanishes on the next capability refresh).
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun SessionDetailPane(
    session: DashboardSessionResponse?,
    apiClient: ApiClient,
    refreshSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        EmptySessionPlaceholder(modifier)
        return
    }
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(apiClient) }
    val capabilities by SessionState.capabilities.collectAsState()
    val currentUser by SessionState.currentUser.collectAsState()
    val roster by sessionVm.practitioners.collectAsState()
    val practitionerResult by sessionVm.practitionerResult.collectAsState()
    val concernResult by sessionVm.concernResult.collectAsState()
    val members by sessionVm.branchMembers.collectAsState()

    // Per-selection dialog/dialog-input state — keyed so switching sessions on the desktop
    // pane never leaks a stale target into a fresh selection.
    key(session.id) {
        var confirmRemoveConcern by remember { mutableStateOf<ConcernResponse?>(null) }
        var confirmRemovePractitioner by remember { mutableStateOf<DashboardPractitionerResponse?>(null) }
        var remarksTarget by remember { mutableStateOf<DashboardPractitionerResponse?>(null) }
        var showAddPractitioner by remember { mutableStateOf(false) }
        var showPromoteOther by remember { mutableStateOf(false) }

        val canEdit =
            capabilities.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                session.branchId,
            ) || capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
        val mutating = practitionerResult is UiState.Loading || concernResult is UiState.Loading
        val displaySession = mergeRosterNames(session, (roster as? UiState.Success)?.data)

        LaunchedEffect(session.id) {
            // Fresh roster for the affordance set even when the enriched row arrived seeded.
            sessionVm.loadSessionPractitioners(session.id)
        }
        LaunchedEffect(practitionerResult) {
            when (val result = practitionerResult) {
                is UiState.Success -> {
                    sessionVm.loadSessionPractitioners(session.id)
                    refreshSession()
                }

                is UiState.Error -> {
                    logWarn("SessionDetailVM", "practitioner mutation failed: ${result.message}")
                    refreshSession()
                }

                else -> {
                    return@LaunchedEffect
                }
            }
            // One-shot drain (#382): a terminal landing is handled exactly once — the sticky
            // flow must not replay into a re-entered pane or a switched selection.
            sessionVm.consumePractitionerResult()
        }
        LaunchedEffect(concernResult) {
            when (val result = concernResult) {
                is UiState.Success -> {
                    refreshSession()
                }

                is UiState.Error -> {
                    logWarn("SessionDetailVM", "concern mutation failed: ${result.message}")
                    refreshSession()
                }

                else -> {
                    return@LaunchedEffect
                }
            }
            sessionVm.consumeConcernResult()
        }

        Column(modifier = modifier) {
            // weight(1f): the detail content owns the flexible space — AddSelf and inline
            // mutation errors stay visible below it instead of past the viewport.
            Box(modifier = Modifier.weight(1f)) {
                SessionDetailContent(
                    session = displaySession,
                    canEdit = canEdit,
                    mutating = mutating,
                    onRemoveConcern = { confirmRemoveConcern = it },
                    onPromoteOtherConcern = { showPromoteOther = true },
                    onAddPractitioner = { showAddPractitioner = true },
                    onUpdatePractitionerRemarks = { remarksTarget = it },
                    onRemovePractitioner = { confirmRemovePractitioner = it },
                )
            }
            AddSelfSection(
                sessionStatus = session.sessionStatus,
                roster = (roster as? UiState.Success)?.data,
                currentUserId = currentUser?.id,
                canEdit = canEdit,
                mutating = mutating,
                onAddSelf = {
                    sessionVm.addPractitioner(
                        session.id,
                        // Idempotency key minted at submit (BR §390–392); duplicate adds are
                        // idempotent server-side anyway.
                        AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = currentUser!!.id),
                    )
                },
            )
            val inlineErrors =
                listOfNotNull(
                    (practitionerResult as? UiState.Error)?.message,
                    (concernResult as? UiState.Error)?.message,
                ).distinct()
            inlineErrors.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                )
            }
        }

        confirmRemoveConcern?.let { concern ->
            ConfirmRemoveDialog(
                title = "Remove concern?",
                body = "\"${concern.label}\" will be detached from this session.",
                inFlight = mutating,
                onConfirm = {
                    confirmRemoveConcern = null
                    sessionVm.removeSessionConcern(session.id, concern.id)
                },
                onDismiss = { confirmRemoveConcern = null },
            )
        }
        confirmRemovePractitioner?.let { practitioner ->
            ConfirmRemoveDialog(
                title = "Remove practitioner?",
                body = "${practitioner.displayName} will be removed from this session.",
                inFlight = mutating,
                onConfirm = {
                    confirmRemovePractitioner = null
                    sessionVm.removePractitioner(session.id, practitioner.practitionerId)
                },
                onDismiss = { confirmRemovePractitioner = null },
            )
        }
        remarksTarget?.let { practitioner ->
            PractitionerRemarksDialog(
                practitioner = practitioner,
                inFlight = mutating,
                onConfirm = { remarks ->
                    remarksTarget = null
                    sessionVm.updatePractitionerRemarks(
                        session.id,
                        practitioner.practitionerId,
                        UpdatePractitionerRemarksRequest(remarks = remarks.trim().ifBlank { null }),
                    )
                },
                onDismiss = { remarksTarget = null },
            )
        }
        if (showAddPractitioner) {
            LaunchedEffect(session.branchId) { sessionVm.loadBranchMembers(session.branchId) }
            val rosterIds = displaySession.practitioners.map { it.practitionerId }.toSet()
            PickerDialog(
                title = "Add practitioner",
                items = (members as? UiState.Success)?.data?.filterNot { it.id in rosterIds }.orEmpty(),
                loading = members is UiState.Loading,
                error = (members as? UiState.Error)?.message,
                label = { it.displayName },
                onSelect = { member ->
                    showAddPractitioner = false
                    sessionVm.addPractitioner(
                        session.id,
                        AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = member.id),
                    )
                },
                onRetry = { sessionVm.loadBranchMembers(session.branchId) },
                onDismiss = { showAddPractitioner = false },
            )
        }
        if (showPromoteOther) {
            PromoteConcernDialog(
                initialLabel = session.otherConcerns.orEmpty(),
                inFlight = mutating,
                onConfirm = { label ->
                    showPromoteOther = false
                    sessionVm.promoteConcern(
                        session.id,
                        PromoteConcernRequest(id = Uuid.random().toString(), label = label.trim()),
                    )
                },
                onDismiss = { showPromoteOther = false },
            )
        }
    }
}

/**
 * #348 — add-self affordance, moved here so both hosts render it identically. Gated to
 * PENDING sessions where the caller holds the edit capability but is not yet on the roster.
 */
@Composable
private fun AddSelfSection(
    sessionStatus: SessionStatus,
    roster: List<SessionPractitionerResponse>?,
    currentUserId: String?,
    canEdit: Boolean,
    mutating: Boolean,
    onAddSelf: () -> Unit,
) {
    if (!canEdit || sessionStatus != SessionStatus.PENDING || currentUserId == null) return
    val rosterIds = roster?.map { it.practitionerId } ?: return
    if (mutating) return
    if (currentUserId in rosterIds) return

    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        TextButton(onClick = onAddSelf) {
            Text("Add self as practitioner")
        }
    }
}

/** Moves the side-loaded roster rows over the enriched row, keeping known display names. */
private fun mergeRosterNames(
    session: DashboardSessionResponse,
    freshRows: List<SessionPractitionerResponse>?,
): DashboardSessionResponse {
    if (freshRows == null) return session
    val namesById = session.practitioners.associate { it.practitionerId to it.displayName }
    return session.copy(
        practitioners =
            freshRows.map { row ->
                DashboardPractitionerResponse(
                    practitionerId = row.practitionerId,
                    displayName = namesById[row.practitionerId] ?: FALLBACK_PRACTITIONER_NAME,
                    remarks = row.remarks,
                    slotAtTime = row.slotAtTime,
                )
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

/**
 * #382 — promote the session's free-text "other concerns" note into a tracked catalog
 * concern; the backend clears the note atomically with the new link.
 */
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

/** Simple list picker over directory reads (branch members / concern catalog). */
@Composable
private fun <T> PickerDialog(
    title: String,
    items: List<T>,
    loading: Boolean,
    error: String?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (loading) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(Spacing.lg))
                    }
                }
                error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                items.forEach { item ->
                    TextButton(onClick = { onSelect(item) }) {
                        Text(label(item), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (!loading && error == null && items.isEmpty()) {
                    Text("Nothing available", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private const val FALLBACK_PRACTITIONER_NAME = "Practitioner"
