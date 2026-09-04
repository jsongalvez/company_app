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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasBranchOrDayCapability
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.state.hasDayGrant
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.InventoryViewModel
import com.companyb.companyapp.viewmodel.ProductSaleViewModel
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.addPractitioner
import com.companyb.companyapp.viewmodel.consumeConcernResult
import com.companyb.companyapp.viewmodel.consumePractitionerResult
import com.companyb.companyapp.viewmodel.loadSessionPractitioners
import com.companyb.companyapp.viewmodel.removePractitioner
import com.companyb.companyapp.viewmodel.removeSessionConcern
import com.companyb.companyapp.viewmodel.updatePractitionerRemarks
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
 * 403 and vanishes on the next capability refresh). #406 — void/unvoid instead mirrors the
 * stricter `requireBranchCapabilityForSession(VOID_SESSION)`: BRANCH-scoped VOID_SESSION
 * only, no day-grant leg, desktop-only via [allowVoid] (ADR-0020).
 *
 * #419 — the session-linked product-sale entry ("Record sale"): side-loaded
 * [ProductSaleViewModel]/[InventoryViewModel] pair, gated by the exact branch-or-day
 * EDIT_BRANCH_DATA mirror of `POST /api/product-sales` (fail-closed without the caller's
 * clocked-in branchDayId); any terminal sale outcome refreshes authoritatively.
 */
@Composable
internal fun SessionDetailPane(
    session: DashboardSessionResponse?,
    apiClient: ApiClient,
    refreshSession: () -> Unit,
    modifier: Modifier = Modifier,
    // #406 — void/unvoid is a desktop-only mutation surface (ADR-0020): the desktop inline
    // pane opts in; the mobile pushed route keeps the default read-only detail.
    allowVoid: Boolean = false,
) {
    if (session == null) {
        EmptySessionPlaceholder(modifier)
        return
    }
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(apiClient) }
    val productSaleVm: ProductSaleViewModel = viewModel { ProductSaleViewModel(apiClient) }
    val inventoryVm: InventoryViewModel = viewModel { InventoryViewModel(apiClient) }
    val currentUser by SessionState.currentUser.collectAsState()
    val roster by sessionVm.practitioners.collectAsState()
    val practitionerResult by sessionVm.practitionerResult.collectAsState()
    val concernResult by sessionVm.concernResult.collectAsState()
    val voidResult by sessionVm.voidResult.collectAsState()
    val unvoidResult by sessionVm.unvoidResult.collectAsState()
    val saleResult by productSaleVm.saleResult.collectAsState()
    val members by sessionVm.branchMembers.collectAsState()

    // Per-selection dialog/dialog-input state — keyed so switching sessions on the desktop
    // pane never leaks a stale target into a fresh selection.
    key(session.id) {
        EditableSessionPane(
            state =
                PaneState(
                    currentUserId = currentUser?.id,
                    rosterRows = (roster as? UiState.Success)?.data,
                    results =
                        PaneResults(
                            practitionerResult = practitionerResult,
                            concernResult = concernResult,
                            voidResult = voidResult,
                            unvoidResult = unvoidResult,
                            saleResult = saleResult,
                        ),
                    members = members,
                    allowVoid = allowVoid,
                ),
            vms = PaneViewModels(sessionVm, productSaleVm, inventoryVm),
            session = session,
            refreshSession = refreshSession,
            modifier = modifier,
        )
    }
}

/** The pane's mutation-flow terminals, bundled so every consumer takes one value (#412). */
internal class PaneResults(
    val practitionerResult: UiState<SessionPractitionerResponse>,
    val concernResult: UiState<Unit>,
    val voidResult: UiState<SessionVoidResponse>,
    val unvoidResult: UiState<SessionVoidResponse>,
    // #419 — the session-linked product-sale flow rides its own VM but drains with the rest.
    val saleResult: UiState<ProductSaleResponse>,
) {
    /** Every terminal error message across the flows, deduplicated for display. */
    internal fun errorMessages(): List<String> =
        listOfNotNull(
            (practitionerResult as? UiState.Error)?.message,
            (concernResult as? UiState.Error)?.message,
            (voidResult as? UiState.Error)?.message,
            (unvoidResult as? UiState.Error)?.message,
            (saleResult as? UiState.Error)?.message,
        ).distinct()
}

/**
 * Snapshot of the pane's observed flows (plus static host config), bundled for low arity.
 * The capability snapshot and clocked-in day are collected inside [EditableSessionPane]
 * (the [com.companyb.companyapp.ui.screen.InventoryScreen] header idiom).
 */
private class PaneState(
    val currentUserId: String?,
    val rosterRows: List<SessionPractitionerResponse>?,
    val results: PaneResults,
    val members: UiState<List<BranchMemberResponse>>,
    // #406 — void/unvoid is a desktop-only mutation surface (ADR-0020): the desktop inline
    // pane opts in; the mobile pushed route keeps the default read-only detail.
    val allowVoid: Boolean,
)

/** The pane's side-loaded VM trio (#419 added the sale pair); one value, low arity (#412). */
private class PaneViewModels(
    val session: SessionViewModel,
    val productSale: ProductSaleViewModel,
    val inventory: InventoryViewModel,
)

/** The pane's client-side gate mirrors (#382 edit, #406 void, #419 sale) from one snapshot. */
private class PaneGates(
    val canEdit: Boolean,
    val canVoid: Boolean,
    val canSell: Boolean,
)

/**
 * The exact backend-mirror predicates (#382/#406/#419), computed from one capability snapshot:
 * edit is branch-or-day `EDIT_BRANCH_DATA`, void is BRANCH-scoped `VOID_SESSION` only, sale is
 * branch-or-day `EDIT_BRANCH_DATA` fail-closed without the clocked-in day.
 */
private fun paneGates(
    capabilities: List<UserCapabilityResponse>,
    session: DashboardSessionResponse,
    branchDayId: String?,
    allowVoid: Boolean,
): PaneGates =
    PaneGates(
        canEdit =
            capabilities.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                session.branchId,
            ) || capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA),
        // #406 — the void gate mirrors the backend's `requireBranchCapabilityForSession`:
        // branch-scoped VOID_SESSION only — no day-grant leg (see [canVoidSession]).
        canVoid = allowVoid && canVoidSession(capabilities, session.branchId),
        // #419 — the sale gate mirrors `requireBranchOrBranchDayCapability(EDIT_BRANCH_DATA)`
        // on POST /api/product-sales; fail-closed without the clocked-in day (the request's
        // branchDayId).
        canSell =
            branchDayId != null &&
                capabilities.hasBranchOrDayCapability(
                    CapabilityCodes.EDIT_BRANCH_DATA,
                    session.branchId,
                    branchDayId,
                ),
    )

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun EditableSessionPane(
    state: PaneState,
    vms: PaneViewModels,
    session: DashboardSessionResponse,
    refreshSession: () -> Unit,
    modifier: Modifier,
) {
    val targets = remember(session.id) { PaneDialogTargets() }
    val capabilities by SessionState.capabilities.collectAsState()
    val branchDayId by SessionState.branchDayId.collectAsState()
    val gates = paneGates(capabilities, session, branchDayId, state.allowVoid)
    val mutating =
        state.results.practitionerResult is UiState.Loading ||
            state.results.concernResult is UiState.Loading ||
            state.results.voidResult is UiState.Loading ||
            state.results.unvoidResult is UiState.Loading ||
            state.results.saleResult is UiState.Loading
    val gate = SessionEditGate(canEdit = gates.canEdit, mutating = mutating)
    val displaySession = mergeRosterNames(session, state.rosterRows)

    PaneEffects(vms.session, session.id, state.results, targets, refreshSession)
    SaleEffects(vms.productSale, state.results.saleResult, refreshSession)

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
        VoidActionSection(
            affordance = sessionVoidAffordance(gates.canVoid, displaySession.isVoided),
            mutating = mutating,
            onVoid = { targets.showVoid = true },
            onUnvoid = { targets.showUnvoid = true },
        )
        SellSection(
            affordance = gates.canSell && !mutating && !displaySession.isVoided,
            onSell = { targets.showSell = true },
        )
        AddSelfSection(
            sessionStatus = session.sessionStatus,
            roster = state.rosterRows,
            currentUserId = state.currentUserId,
            gate = gate,
            onAddSelf = {
                vms.session.addPractitioner(
                    session.id,
                    // Idempotency key minted at submit (BR §390–392); duplicate adds are
                    // idempotent server-side anyway.
                    AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = state.currentUserId!!),
                )
            },
        )
        InlineMutationErrors(state.results.errorMessages().distinct())
    }

    PaneDialogs(vms.session, displaySession, gate.mutating, targets, state.members)
    PaneSaleDialogHost(
        visible = targets.showSell,
        session = displaySession,
        sale = PaneSaleContext(branchDayId, vms.inventory, vms.productSale),
        onClose = { targets.showSell = false },
    )
}

/**
 * #419 — the in-session sale entry point, rendered like the other pane action sections;
 * hidden entirely for read-only callers (the backend's gate stays authoritative).
 */
@Composable
private fun SellSection(
    affordance: Boolean,
    onSell: () -> Unit,
) {
    if (!affordance) return
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        TextButton(onClick = onSell) {
            Text("Record sale")
        }
    }
}

/** Roster load + the one-shot mutation-result drains (#382): any terminal landing refreshes. */
@Composable
private fun PaneEffects(
    sessionVm: SessionViewModel,
    sessionId: String,
    results: PaneResults,
    targets: PaneDialogTargets,
    refreshSession: () -> Unit,
) {
    LaunchedEffect(sessionId) {
        // Fresh roster for the affordance set even when the enriched row arrived seeded.
        sessionVm.loadSessionPractitioners(sessionId)
    }
    LaunchedEffect(results.practitionerResult) {
        when (val result = results.practitionerResult) {
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
    LaunchedEffect(results.concernResult) {
        when (val result = results.concernResult) {
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
    // #406 — the armed void/unvoid drains live with their dialogs in SessionDetailVoidFlow.kt.
    VoidUnvoidEffects(sessionVm, sessionId, results, targets, refreshSession)
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
    PaneVoidDialogs(sessionVm, session, mutating, targets)
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
