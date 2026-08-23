package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.state.hasDayGrant
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
        EditableSessionPane(
            state =
                PaneState(
                    capabilities = capabilities,
                    currentUserId = currentUser?.id,
                    rosterRows = (roster as? UiState.Success)?.data,
                    practitionerResult = practitionerResult,
                    concernResult = concernResult,
                    members = members,
                ),
            sessionVm = sessionVm,
            session = session,
            refreshSession = refreshSession,
            modifier = modifier,
        )
    }
}

/** Snapshot of the pane's observed flows, bundled to keep the editable surface's arity low. */
private class PaneState(
    val capabilities: List<UserCapabilityResponse>,
    val currentUserId: String?,
    val rosterRows: List<SessionPractitionerResponse>?,
    val practitionerResult: UiState<SessionPractitionerResponse>,
    val concernResult: UiState<Unit>,
    val members: UiState<List<BranchMemberResponse>>,
)

/** Per-selection dialog targets; see the `key(session.id)` note on [SessionDetailPane]. */
private class PaneDialogTargets {
    var removeConcern by mutableStateOf<ConcernResponse?>(null)
    var removePractitioner by mutableStateOf<DashboardPractitionerResponse?>(null)
    var remarksTarget by mutableStateOf<DashboardPractitionerResponse?>(null)
    var showAddPractitioner by mutableStateOf(false)
    var showPromoteOther by mutableStateOf(false)
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun EditableSessionPane(
    state: PaneState,
    sessionVm: SessionViewModel,
    session: DashboardSessionResponse,
    refreshSession: () -> Unit,
    modifier: Modifier,
) {
    val targets = remember(session.id) { PaneDialogTargets() }
    val canEdit =
        state.capabilities.hasCapability(
            CapabilityCodes.EDIT_BRANCH_DATA,
            CapabilityContextType.BRANCH,
            session.branchId,
        ) || state.capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
    val mutating = state.practitionerResult is UiState.Loading || state.concernResult is UiState.Loading
    val gate = SessionEditGate(canEdit = canEdit, mutating = mutating)
    val displaySession = mergeRosterNames(session, state.rosterRows)

    PaneEffects(sessionVm, session.id, state.practitionerResult, state.concernResult, refreshSession)

    Column(modifier = modifier) {
        // weight(1f): the detail content owns the flexible space — AddSelf and inline
        // mutation errors stay visible below it instead of past the viewport.
        Box(modifier = Modifier.weight(1f)) {
            SessionDetailContent(
                session = displaySession,
                gate = gate,
                actions = paneActions(targets),
            )
        }
        AddSelfSection(
            sessionStatus = session.sessionStatus,
            roster = state.rosterRows,
            currentUserId = state.currentUserId,
            gate = gate,
            onAddSelf = {
                sessionVm.addPractitioner(
                    session.id,
                    // Idempotency key minted at submit (BR §390–392); duplicate adds are
                    // idempotent server-side anyway.
                    AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = state.currentUserId!!),
                )
            },
        )
        InlineMutationErrors(
            listOfNotNull(
                (state.practitionerResult as? UiState.Error)?.message,
                (state.concernResult as? UiState.Error)?.message,
            ).distinct(),
        )
    }

    PaneDialogs(sessionVm, displaySession, gate.mutating, targets, state.members)
}

/** Roster load + the one-shot mutation-result drains (#382): any terminal landing refreshes. */
@Composable
private fun PaneEffects(
    sessionVm: SessionViewModel,
    sessionId: String,
    practitionerResult: UiState<SessionPractitionerResponse>,
    concernResult: UiState<Unit>,
    refreshSession: () -> Unit,
) {
    LaunchedEffect(sessionId) {
        // Fresh roster for the affordance set even when the enriched row arrived seeded.
        sessionVm.loadSessionPractitioners(sessionId)
    }
    LaunchedEffect(practitionerResult) {
        when (val result = practitionerResult) {
            is UiState.Success -> {
                sessionVm.loadSessionPractitioners(sessionId)
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
}

private fun paneActions(targets: PaneDialogTargets): SessionDetailActions =
    SessionDetailActions(
        onRemoveConcern = { targets.removeConcern = it },
        onPromoteOtherConcern = { targets.showPromoteOther = true },
        onAddPractitioner = { targets.showAddPractitioner = true },
        onUpdatePractitionerRemarks = { targets.remarksTarget = it },
        onRemovePractitioner = { targets.removePractitioner = it },
    )

@Composable
private fun InlineMutationErrors(errors: List<String>) {
    errors.forEach { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun PaneDialogs(
    sessionVm: SessionViewModel,
    session: DashboardSessionResponse,
    mutating: Boolean,
    targets: PaneDialogTargets,
    members: UiState<List<BranchMemberResponse>>,
) {
    RemoveConcernDialogHost(
        target = targets.removeConcern,
        mutating = mutating,
        onConfirmed = { sessionVm.removeSessionConcern(session.id, it) },
        onCleared = { targets.removeConcern = null },
    )
    RemovePractitionerDialogHost(
        target = targets.removePractitioner,
        mutating = mutating,
        onConfirmed = { sessionVm.removePractitioner(session.id, it) },
        onCleared = { targets.removePractitioner = null },
    )
    RemarksDialogHost(
        target = targets.remarksTarget,
        mutating = mutating,
        onConfirmed = { practitionerId, remarks ->
            sessionVm.updatePractitionerRemarks(
                session.id,
                practitionerId,
                UpdatePractitionerRemarksRequest(remarks = remarks),
            )
        },
        onCleared = { targets.remarksTarget = null },
    )
    AddPractitionerDialogHost(
        visible = targets.showAddPractitioner,
        session = session,
        sessionVm = sessionVm,
        members = members,
        onClose = { targets.showAddPractitioner = false },
    )
    PromoteOtherDialogHost(
        visible = targets.showPromoteOther,
        session = session,
        sessionVm = sessionVm,
        mutating = mutating,
        onClose = { targets.showPromoteOther = false },
    )
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
    gate: SessionEditGate,
    onAddSelf: () -> Unit,
) {
    if (!gate.canEdit || sessionStatus != SessionStatus.PENDING || currentUserId == null) return
    val rosterIds = roster?.map { it.practitionerId } ?: return
    if (gate.mutating) return
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

private const val FALLBACK_PRACTITIONER_NAME = "Practitioner"
