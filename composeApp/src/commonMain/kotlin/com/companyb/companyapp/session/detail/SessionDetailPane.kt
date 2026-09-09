package com.companyb.companyapp.session.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.app.hasDayGrant
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.commerce.stock.InventoryViewModel
import com.companyb.companyapp.commerce.stock.PaneSaleContext
import com.companyb.companyapp.commerce.stock.PaneSaleDialogHost
import com.companyb.companyapp.commerce.stock.ProductSaleViewModel
import com.companyb.companyapp.commerce.stock.SaleEffects
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.commerce.ProductSaleResponse
import com.companyb.companyapp.contracts.session.AddPractitionerRequest
import com.companyb.companyapp.contracts.session.DashboardPractitionerResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionPractitionerResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionVoidResponse
import com.companyb.companyapp.contracts.session.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.contracts.session.UpdateSessionStatusRequest
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.session.dashboard.remittedReasonRequired
import com.companyb.companyapp.ui.screen.EmptySessionPlaceholder
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #382 — the stateful session-detail surface: [SessionDetailContent] plus the post-create
 * concern/practitioner affordances and their dialogs. One implementation serves BOTH hosts —
 * the desktop master-detail inline pane and the mobile pushed SessionDetail route (#152's
 * byte-identical-content rule, extended to the editable path).
 *
 * A per-selection side-loaded [SessionViewModel] (keyed by session id, #486) owns
 * the roster (`GET .../practitioners`) and every
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
 * stricter `SessionAuthz.requireBranchCapabilityForSession(VOID_SESSION)`: BRANCH-scoped VOID_SESSION
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
    // #486 — selection-owned scopes: every side-loaded VM is keyed by session.id, so
    // a new selection starts from a fresh scope (roster/members/results start
    // Idle — no stale rows for mergeRosterNames, no sticky result replay into the new
    // pane). A late landing from a switched-away selection commits into its dead scope,
    // never the visible pane (the roster/members stamp guards still cover same-scope
    // races: a superseded load never deserializes over the newer commit). Revisiting
    // a selection reuses its cached keyed VM (#489): any retained terminal refreshes
    // on re-entry (the void/unvoid drains refresh unconditionally, mirroring the
    // practitioner/concern drains) instead of replaying silently.
    val sessionVm: SessionViewModel =
        viewModel(key = "session-detail-${session.id}") { SessionViewModel(apiClient) }
    val productSaleVm: ProductSaleViewModel =
        viewModel(key = "session-sale-${session.id}") { ProductSaleViewModel(apiClient) }
    val inventoryVm: InventoryViewModel =
        viewModel(key = "session-inventory-${session.id}") { InventoryViewModel(apiClient) }
    val snapshot by AppSessionState.snapshot.collectAsState()
    val currentUser = snapshot.user
    val roster by sessionVm.practitioners.collectAsState()
    val practitionerResult by sessionVm.practitionerResult.collectAsState()
    val concernResult by sessionVm.concernResult.collectAsState()
    val voidResult by sessionVm.voidResult.collectAsState()
    val unvoidResult by sessionVm.unvoidResult.collectAsState()
    val saleResult by productSaleVm.saleResult.collectAsState()
    val statusResult by sessionVm.statusResult.collectAsState()
    val statusConflict by sessionVm.statusConflict.collectAsState()
    val dayStatusState by sessionVm.dayStatus.collectAsState()
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
                            statusResult = statusResult,
                        ),
                    members = members,
                    allowVoid = allowVoid,
                    statusConflict = statusConflict,
                    dayStatus = (dayStatusState as? UiState.Success)?.data,
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
    // #675 — the detail status transition (completion/corrections) drains with the rest.
    val statusResult: UiState<SessionResponse>,
) {
    /** Every terminal error message across the flows, deduplicated for display. */
    internal fun errorMessages(): List<String> =
        // Status failures render in their own retry row ([StatusErrorRow]), not here.
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
 * (the [com.companyb.companyapp.commerce.stock.InventoryScreen] header idiom).
 */
private class PaneState(
    val currentUserId: String?,
    val rosterRows: List<SessionPractitionerResponse>?,
    val results: PaneResults,
    val members: UiState<List<BranchMemberResponse>>,
    // #406 — void/unvoid is a desktop-only mutation surface (ADR-0020): the desktop inline
    // pane opts in; the mobile pushed route keeps the default read-only detail.
    val allowVoid: Boolean,
    // #675 — the handled 409 flag (banner + Reload) and the day status behind the
    // shared action model (null = unknown, the model fails closed).
    val statusConflict: Boolean,
    val dayStatus: DayStatus?,
)

/** The pane's side-loaded VM trio (#419 added the sale pair); one value, low arity (#412). */
private class PaneViewModels(
    val session: SessionViewModel,
    val productSale: ProductSaleViewModel,
    val inventory: InventoryViewModel,
)

/** The pane's client-side gate mirrors (#382 edit, #406 void, #419 sale) from one snapshot. */
internal class PaneGates(
    val canEdit: Boolean,
    val canVoid: Boolean,
    val canSell: Boolean,
)

/**
 * The exact backend-mirror predicates (#382/#406/#419), computed from one capability snapshot:
 * edit is branch-or-day `EDIT_BRANCH_DATA`, void is BRANCH-scoped `VOID_SESSION` only, sale is
 * branch-or-day `EDIT_BRANCH_DATA` fail-closed without the clocked-in day.
 * Internal for the #675 capability-matrix tests (same module).
 */
internal fun paneGates(
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
        // #406 — the void gate mirrors the backend's `SessionAuthz.requireBranchCapabilityForSession`:
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
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val branchDayId = snapshot.clock?.branchDayId
    val gates = paneGates(capabilities, session, branchDayId, state.allowVoid)
    // #675 — Coordinator-only correction authority mirrors the backend's
    // SessionAuthz.requireStatusCorrectionCapability (EDIT_PAST_DAY at the branch).
    val canCorrectStatus =
        capabilities.hasCapability(
            CapabilityCodes.EDIT_PAST_DAY,
            CapabilityContextType.BRANCH,
            session.branchId,
        )
    val mutating =
        state.results.practitionerResult is UiState.Loading ||
            state.results.concernResult is UiState.Loading ||
            state.results.voidResult is UiState.Loading ||
            state.results.unvoidResult is UiState.Loading ||
            state.results.saleResult is UiState.Loading ||
            state.results.statusResult is UiState.Loading
    val gate = SessionEditGate(canEdit = gates.canEdit, mutating = mutating)
    val displaySession = mergeRosterNames(session, state.rosterRows)
    // #675 — one shared action model for the desktop pane and the compact detail,
    // driven by the refreshed row plus the existing status-policy/capability predicates.
    val model =
        sessionDetailActionModel(
            session = displaySession,
            rosterIds = state.rosterRows?.map { it.practitionerId }?.toSet(),
            currentUserId = state.currentUserId,
            canEdit = gates.canEdit,
            canCorrectStatus = canCorrectStatus,
            dayStatus = state.dayStatus,
        )
    val voidAffordance = sessionVoidAffordance(gates.canVoid, displaySession.isVoided)
    // #419 — the sale gate mirrors POST /api/product-sales; voided sessions stay excluded.
    val showSale = gates.canSell && !displaySession.isVoided
    // #675 — ordinary completion dispatches directly; REMITTED-day writes collect the
    // audit reason first (the backend 400s blank reasons server-side).
    val requiresReason = remittedReasonRequired(state.dayStatus)
    val primaryFocus = remember(session.id) { FocusRequester() }
    val menuTriggerFocus = remember(session.id) { FocusRequester() }
    val saleFocus = remember(session.id) { FocusRequester() }

    PaneEffects(vms.session, session.id, session.branchId, state.results, refreshSession)
    SaleEffects(vms.productSale, state.results.saleResult, refreshSession)
    // #675 — a 409 repaints the authoritative row under the banner; the flag stays until
    // the operator taps Reload, so the explanation survives to be read. Open dialog
    // drafts are pane-held (keyed by session id) and survive the refresh, so unsent
    // permitted edits stay for review instead of being discarded with the stale version.
    StatusConflictEffects(conflict = state.statusConflict) {
        refreshSession()
    }
    // #675 — action-bar-originated terminals keep focus in the action region: the primary
    // slot while it survives, else the menu trigger (a detached requester is a silent
    // no-op, e.g. completion dissolving the primary into the state line).
    ActionFocusEffects(
        focusTarget = if (model.primary != null) primaryFocus else menuTriggerFocus,
        results = state.results,
    )

    fun requestStatus(
        target: SessionStatus,
        reason: String? = null,
    ) {
        targets.lastStatusTarget = target
        vms.session.updateSessionStatus(
            session.id,
            UpdateSessionStatusRequest(status = target, version = session.version, reason = reason),
        )
    }

    /** REMITTED-day writes collect the audit reason first; open days dispatch directly. */
    fun requestTarget(target: SessionStatus) {
        if (requiresReason) {
            targets.pendingStatus = target
        } else {
            requestStatus(target)
        }
    }

    Column(modifier = modifier) {
        // weight(1f): the detail content owns the flexible space — the action bar and
        // inline mutation errors stay visible below it instead of past the viewport.
        Box(modifier = Modifier.weight(1f)) {
            SessionDetailContent(
                session = displaySession,
                gate = gate,
                actions = paneActions(targets),
            )
        }
        if (state.statusConflict) {
            StatusConflictBanner(onReload = {
                vms.session.clearStatusConflict()
                refreshSession()
            })
        }
        // The bar renders for editors and sellers only — read-only callers keep the
        // exact pre-#675 rendering (content plus inline errors, no action region).
        // #695 — named bar gate: editor/seller affordances in one readable branch.
        val showActionBar =
            model.primary != null || model.showCompletedState || showSale ||
                model.statusMenuOptions.isNotEmpty() || voidAffordance != null
        if (showActionBar) {
            DetailActionBar(
                model = model,
                showSale = showSale,
                voidAffordance = voidAffordance,
                mutating = mutating,
                primaryBusy =
                    (
                        model.primary == SessionDetailPrimary.COMPLETE &&
                            state.results.statusResult is UiState.Loading
                    ) ||
                        (
                            model.primary == SessionDetailPrimary.ADD_SELF &&
                                state.results.practitionerResult is UiState.Loading
                        ),
                primaryFocus = primaryFocus,
                menuTriggerFocus = menuTriggerFocus,
                saleFocus = saleFocus,
                onPrimary = { primary ->
                    when (primary) {
                        SessionDetailPrimary.ADD_SELF -> {
                            // Reachable only with a signed-in user (the policy offers no
                            // primary otherwise); the guard documents it instead of `!!`.
                            val uid = state.currentUserId
                            if (uid != null) {
                                vms.session.addPractitioner(
                                    session.id,
                                    // Idempotency key minted at submit (BR §390–392); duplicate adds are
                                    // idempotent server-side anyway (plus the VM's double-tap guard).
                                    AddPractitionerRequest(
                                        id = Uuid.random().toString(),
                                        practitionerId = uid,
                                    ),
                                )
                            }
                        }

                        SessionDetailPrimary.COMPLETE -> {
                            requestTarget(SessionStatus.COMPLETED)
                        }
                    }
                },
                onStatusOption = { target ->
                    requestTarget(target)
                },
                onSell = { targets.showSell = true },
                onVoid = { targets.showVoid = true },
                onUnvoid = { targets.showUnvoid = true },
                saleBusy = state.results.saleResult is UiState.Loading,
            )
        }
        // #675 — the failed status target stays redispatchable with the freshly refreshed
        // version (Retry hides once the target leaves the legal set). A revisit reuses the
        // cached VM while targets reset, so the message renders without Retry rather than
        // going silent when the target is gone.
        val statusError = state.results.statusResult as? UiState.Error
        val lastTarget = targets.lastStatusTarget
        if (statusError != null) {
            val retryTarget = lastTarget
            StatusErrorRow(
                message = statusError.message,
                canRetry =
                    retryTarget != null &&
                        (
                            retryTarget in model.statusMenuOptions ||
                                (
                                    model.primary == SessionDetailPrimary.COMPLETE &&
                                        retryTarget == SessionStatus.COMPLETED
                                )
                        ),
                onRetry = {
                    if (retryTarget != null) requestTarget(retryTarget)
                },
            )
        }
        InlineMutationErrors(state.results.errorMessages().distinct())
    }

    PaneDialogs(
        sessionVm = vms.session,
        session = displaySession,
        mutating = gate.mutating,
        targets = targets,
        members = state.members,
        dismissFocus = menuTriggerFocus,
    )
    StatusReasonDialogHost(
        target = targets.pendingStatus,
        mutating = mutating,
        onConfirmed = { target, reason -> requestStatus(target, reason) },
        onCleared = { targets.pendingStatus = null },
        onDismissFocus = { menuTriggerFocus.requestFocus() },
    )
    PaneSaleDialogHost(
        visible = targets.showSell,
        session = displaySession,
        sale = PaneSaleContext(branchDayId, vms.inventory, vms.productSale),
        // #675 — cancelling the sale dialog restores the invoking sale control.
        onClose = {
            targets.showSell = false
            saleFocus.requestFocus()
        },
    )
}

/** Roster/day-status loads + the one-shot mutation-result drains (#382): any terminal landing refreshes. */
@Composable
private fun PaneEffects(
    sessionVm: SessionViewModel,
    sessionId: String,
    branchId: String,
    results: PaneResults,
    refreshSession: () -> Unit,
) {
    LaunchedEffect(sessionId) {
        // Fresh roster for the affordance set even when the enriched row arrived seeded.
        sessionVm.loadSessionPractitioners(sessionId)
    }
    LaunchedEffect(branchId) {
        // #675 — the day status behind the shared action model; a blank branch fails
        // closed (no read, the model offers nothing until the day is known).
        if (branchId.isNotBlank()) sessionVm.loadDayStatus(branchId)
    }
    LaunchedEffect(results.practitionerResult) {
        when (val result = results.practitionerResult) {
            is UiState.Success -> {
                sessionVm.loadSessionPractitioners(sessionId)
                refreshSession()
            }

            is UiState.Error -> {
                logWarn("SessionDetailVM", "practitioner mutation failed: ${result.message}")
                // A failed mutation may still have moved the roster server-side — re-read
                // it with the row so the membership shortcut never disagrees.
                sessionVm.loadSessionPractitioners(sessionId)
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
    LaunchedEffect(results.statusResult) {
        when (val result = results.statusResult) {
            is UiState.Success -> {
                // Membership is untouched by status writes — only the authoritative row
                // (status badge, version, primary/menu state) repaints, in place.
                refreshSession()
                sessionVm.consumeStatusResult()
            }

            is UiState.Error -> {
                logWarn("SessionDetailVM", "status change failed: ${result.message}")
                // Sticky failure: no consume — the retry row keeps the failed target
                // redispatchable with the freshly refreshed version below. The next
                // dispatch replaces it (Loading), a success clears it.
                refreshSession()
            }

            else -> {
                return@LaunchedEffect
            }
        }
    }
    // #406 — the void/unvoid drains live with their dialogs in SessionDetailVoidFlow.kt.
    VoidUnvoidEffects(sessionVm, results, refreshSession)
}

/**
 * #675 — the handled-conflict refresher: fires once when the 409 flag rises (the banner
 * explains it; Reload clears the flag). Kept separate from the result drains because a
 * handled conflict never lands in [PaneResults.statusResult].
 */
@Composable
private fun StatusConflictEffects(
    conflict: Boolean,
    onResolve: () -> Unit,
) {
    LaunchedEffect(conflict) {
        if (conflict) onResolve()
    }
}

/**
 * #675 — action-bar-originated terminals (status, membership, void/unvoid, sale) keep
 * focus on the pane-chosen region target — the primary slot while it survives, else the
 * menu trigger. Content-section flows (concerns) keep their own focus: their dialogs
 * close back onto the invoking row. A detached requester is a silent no-op.
 */
@Composable
private fun ActionFocusEffects(
    focusTarget: FocusRequester,
    results: PaneResults,
) {
    LaunchedEffect(results.statusResult) {
        if (results.statusResult.isTerminal()) focusTarget.requestFocus()
    }
    LaunchedEffect(results.practitionerResult) {
        if (results.practitionerResult.isTerminal()) focusTarget.requestFocus()
    }
    LaunchedEffect(results.voidResult) {
        if (results.voidResult.isTerminal()) focusTarget.requestFocus()
    }
    LaunchedEffect(results.unvoidResult) {
        if (results.unvoidResult.isTerminal()) focusTarget.requestFocus()
    }
    LaunchedEffect(results.saleResult) {
        if (results.saleResult.isTerminal()) focusTarget.requestFocus()
    }
}

private fun UiState<*>.isTerminal(): Boolean = this is UiState.Success || this is UiState.Error

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
    dismissFocus: FocusRequester,
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
    PaneVoidDialogs(sessionVm, session, mutating, targets, dismissFocus)
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
