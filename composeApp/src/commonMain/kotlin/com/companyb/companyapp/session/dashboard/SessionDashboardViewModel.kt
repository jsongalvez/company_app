package com.companyb.companyapp.session.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branchday.BranchDayTodayResponse
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.contracts.session.UpdateSessionStatusRequest
import com.companyb.companyapp.contracts.session.isStatusCorrection
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

enum class DashboardPollStatus {
    FRESH,
    STALE,
    ERRORED,
}

private const val CONFLICT_MESSAGE =
    "This session was updated by someone else — reload to see the latest changes"
private const val INVALID_STATUS_MESSAGE = "Choose a legal status transition"
private const val INVALID_PRICE_MESSAGE = "Enter a valid amount (digits only, e.g. 2500.00)"
private const val DAY_STATE_UNAVAILABLE_MESSAGE = "Day state unavailable — refresh before retrying"
private const val REASON_REQUIRED_MESSAGE = "A reason is required to write on a REMITTED day"

private data class CapabilityUpdate(
    val contextChanged: Boolean,
    val wasCanEdit: Boolean,
    val canEdit: Boolean,
    val canCorrectStatus: Boolean,
)

private data class EditRequest(
    val path: String,
    val body: Any,
)

/**
 * Session dashboard (#147, graduated from #97). One fetch backs both the summary cards and the
 * session list (the Q6c "share an endpoint" escape hatch — the regions cannot diverge because
 * they consume the same response), polled every 30s.
 *
 * #556 — session/dashboard owns polling, last-successful data, editing policy and dashboard
 * rendering in one state owner. The #149 inline-edit machine (#97 Q4 + ADR-0022 pessimistic
 * model) and the #403 capability/day-status seam live here as private members — the #479
 * extension splits are folded back per #535 (a cohesive owner, not a function-count bundle).
 * Pure draft/status/price transitions stay adjacent in DashboardEditState/StatusPolicy/
 * PricePolicy with no rendering imports.
 *
 * #97 Q5 locked semantics:
 * - **Completion-then-wait**: the poll loop joins the fetch job before its 30s delay — a slow
 *   poll never overlaps the next one (no wall-clock stacking).
 * - **Silent + last-successful timestamp**: [lastUpdatedAt] is written only on success (a
 *   "last attempt" timestamp that reads fresh while failing is the misleading case).
 * - **Failure thresholds**: 2 consecutive failures → STALE (data preserved, banner);
 *   5 → ERRORED (error card with Retry). Success resets the counter.
 * - **401 is session termination, not degradation**: swallowed here (ApiClient.onUnauthorized
 *   drives the global redirect) and never counted against the thresholds.
 * - **403 (attendance ended server-side)**: forbidden flag + poll stops; retryAfterForbidden
 *   re-enables. The drawer clock-out remains the clean exit.
 * - **Keep-last-results** (Clients #113 D2 / Notifications #141): [lastData] survives the
 *   Loading frame of a retry/poll — the screen renders it whenever non-null.
 *
 * Lifecycle (Q5c): [pause] stops polling when the dashboard route leaves composition (mobile
 * SessionDetail push — desktop's inline pane never leaves), [resume] restarts it.
 *
 * UI contract: screens observe the read-only [StateFlow]s and invoke the public commands
 * ([refresh], [startEdit], [updateDraft], [updateReason], [commitEdit], [discardEdit],
 * [reloadAfterConflict], [retryAfterForbidden]); no composable touches mutable internals.
 */
class SessionDashboardViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "DashboardVM")

    private val _dashboardState = MutableStateFlow<UiState<DashboardResponse>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardResponse>> = _dashboardState.asStateFlow()

    private val lastDataCache = MutableStateFlow<DashboardResponse?>(null)
    val lastData: StateFlow<DashboardResponse?> = lastDataCache.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<Instant?>(null)
    val lastUpdatedAt: StateFlow<Instant?> = _lastUpdatedAt.asStateFlow()

    private val _pollStatus = MutableStateFlow(DashboardPollStatus.FRESH)
    val pollStatus: StateFlow<DashboardPollStatus> = _pollStatus.asStateFlow()

    private val _isForbidden = MutableStateFlow(false)
    val isForbidden: StateFlow<Boolean> = _isForbidden.asStateFlow()

    // #149 — inline editing (#97 Q4 + ADR-0022 pessimistic model). canEdit mirrors the
    // per-element capability guard (#92): EDIT_BRANCH_DATA held at the selected branch or
    // clocked-in branch day, matching the status PATCH route's branch-or-day gate; a PATCH 403
    // sets it false (Q4: silent exit + affordance vanishes).
    private val canEditState =
        MutableStateFlow(
            AppSessionState.snapshot.value.let { snap ->
                snap.capabilities.hasBranchOrDayCapability(
                    code = CapabilityCodes.EDIT_BRANCH_DATA,
                    branchId = snap.clock?.branchId,
                    dayId = snap.clock?.branchDayId,
                )
            },
        )
    val canEdit: StateFlow<Boolean> = canEditState.asStateFlow()

    // #425 — correction affordance mirrors the backend's Coordinator authority check. A
    // missing branch context fails closed through hasCapability's null-context behavior.
    private val canCorrectStatusState =
        MutableStateFlow(
            AppSessionState.snapshot.value.let { snap ->
                snap.capabilities.hasCapability(
                    CapabilityCodes.EDIT_PAST_DAY,
                    CapabilityContextType.BRANCH,
                    snap.clock?.branchId,
                )
            },
        )
    val canCorrectStatus: StateFlow<Boolean> = canCorrectStatusState.asStateFlow()

    private val currentEditState = MutableStateFlow<DashboardEditState?>(null)
    val editState: StateFlow<DashboardEditState?> = currentEditState.asStateFlow()

    // #403 — today's effective day status at the branch (the same evaluateStatus shape the
    // backend's write gate applies). Null = unknown: mutation affordances fail closed. A failed
    // read clears the value so mutation affordances fail closed until a fresh state lands.
    private val dayStatusState = MutableStateFlow<DayStatus?>(null)
    val dayStatus: StateFlow<DayStatus?> = dayStatusState.asStateFlow()

    // #403 — only the newest day read commits (a superseded OPEN landing must never
    // overwrite a fresh REMITTED one).
    private var dayGeneration = 0L
    private var dayStatusBranchId: String? =
        AppSessionState.snapshot.value.clock
            ?.branchId
    private var capabilityContext = AppSessionState.snapshot.value.let { it.clock?.branchId to it.clock?.branchDayId }
    private var capabilitySnapshot = AppSessionState.snapshot.value.capabilities
    private var locallyRevokedEditContext: Pair<String?, String?>? = null
    private var locallyRevokedCorrectionBranch: String? = null

    private var consecutiveFailures = 0
    private var pollJob: Job? = null
    private var capabilityJob: Job? = null
    private var editGeneration = 0L

    // #382 — set synchronously when refresh() is called while a landing is in flight; drained
    // (once) by that landing's invokeOnCompletion.
    @Volatile
    private var pendingRefresh = false

    init {
        observeCapabilities()
        resume()
    }

    private fun countFailure() {
        // NOTE the counting cannot live in a _dashboardState collector — StateFlow conflates
        // equal Error values, so consecutive identical errors would never re-emit
        // (probe-proven in the #147 build). Hooked from both failure paths below.
        consecutiveFailures++
        _pollStatus.value =
            if (consecutiveFailures >= ERROR_THRESHOLD) {
                DashboardPollStatus.ERRORED
            } else if (consecutiveFailures >= STALE_THRESHOLD) {
                DashboardPollStatus.STALE
            } else {
                DashboardPollStatus.FRESH
            }
    }

    fun resume() {
        if (pollJob?.isActive == true) return
        observeCapabilities()
        logInfo("DashboardVM", "poll resume")
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    refresh().join()
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    fun pause() {
        if (pollJob?.isActive == true) {
            logInfo("DashboardVM", "poll pause")
        }
        pollJob?.cancel()
        pollJob = null
        capabilityJob?.cancel()
        capabilityJob = null
    }

    fun refresh(): Job {
        val branchId =
            AppSessionState.snapshot.value.clock
                ?.branchId
        // #601 max-2: missing-branch and in-flight legs share one cancelled-Job exit.
        if (branchId == null || _dashboardState.value is UiState.Loading) {
            // Unreachable post-clock-in (the dashboard is only composed with a selected
            // branch); fail closed rather than fetch a malformed path. A CANCELLED job is
            // returned so the poll loop's join() returns immediately. In-flight guard
            // (manual refresh overlapping a poll): a cancelled job keeps the poll loop's
            // join() from hanging — a bare `Job()` never completes. The synchronous
            // Loading pre-set below makes the guard hold from the CALLER's frame (the #135
            // double-tap pattern — handler.launch pre-sets Loading only inside its coroutine,
            // so two back-to-back refresh() calls would both pass the guard otherwise).
            return Job().also { it.cancel() }
        }
        loadDayStatus()
        _dashboardState.value = UiState.Loading
        return launchReload(branchId)
    }

    /**
     * #382 — authoritative reload after a session mutation on the detail pane. Unlike
     * [refresh], a landing already in flight must not swallow it: the committed change has
     * to repaint promptly, so exactly one follow-up reload is queued and drained by the
     * active landing's completion — skipped on terminal legs (Idle = 401/403 paused the
     * surface; a dead session must not be re-fetched by the queue).
     */
    fun refreshAfterMutation(): Job {
        val branchId =
            AppSessionState.snapshot.value.clock
                ?.branchId ?: return Job().also { it.cancel() }
        if (_dashboardState.value is UiState.Loading) {
            pendingRefresh = true
            return Job().also { it.cancel() }
        }
        loadDayStatus()
        _dashboardState.value = UiState.Loading
        return launchReload(branchId)
    }

    private fun launchReload(branchId: String): Job =
        handler
            .launch(
                LaunchRequest(
                    state = _dashboardState,
                    operation = "loadDashboard",
                    endpoint = "GET /api/branches/$branchId/dashboard/today",
                    block = { apiClient.httpClient.get(ApiRoutes.branchDashboardToday(branchId)) },
                    transform = {
                        it.body<DashboardResponse>().also { data ->
                            // Q5a — the failure counter and the timestamp reset only on SUCCESS.
                            consecutiveFailures = 0
                            _pollStatus.value = DashboardPollStatus.FRESH
                            // #149 — monotonic per-row version merge: a poll response that started
                            // before a successful inline edit landed carries an older version and
                            // must never regress the committed row (stale-poll-after-commit).
                            // #672 — the actively edited row stays pinned at its index so a
                            // landing never reorders it under the pointer.
                            lastDataCache.value =
                                data.copy(
                                    sessions =
                                        mergeDashboardRows(
                                            lastDataCache.value?.sessions,
                                            data.sessions,
                                            currentEditState.value?.sessionId,
                                        ),
                                )
                            _lastUpdatedAt.value = Clock.System.now()
                        }
                    },
                    onNonSuccess = { response ->
                        when (response.status.value) {
                            // 401 — session termination: the global ApiClient.onUnauthorized path
                            // handles the redirect; never counts as poll degradation (Q5b). The
                            // handler pre-set Loading, so reset the state or the in-flight guard
                            // wedges every future poll. Polling stops too — the session is dead.
                            401 -> {
                                _dashboardState.value = UiState.Idle
                                pause()
                                true
                            }

                            // 403 — the attendance gate: clock-in ended server-side (e.g. clocked
                            // out elsewhere). Stop polling; the forbidden card + drawer clock-out.
                            403 -> {
                                _isForbidden.value = true
                                _dashboardState.value = UiState.Idle
                                pause()
                                true
                            }

                            // Generic HTTP failure: counted against the stale/error thresholds.
                            else -> {
                                countFailure()
                                false
                            }
                        }
                    },
                    // Transport/deserialization failures land here (onNonSuccess only sees HTTP
                    // statuses) — without this hook a dead network would silently freeze the last
                    // data with no stale banner, no escalation, no retry (pass-1 HARD).
                    onError = { countFailure() },
                ),
            ).also { job ->
                // #382 — drain exactly one queued post-mutation reload when the active
                // landing completes; skipped on terminal legs (Idle = 401/403 paused the
                // surface — a dead session must not be re-polled by the queue).
                job.invokeOnCompletion {
                    if (pendingRefresh && _dashboardState.value is UiState.Success) {
                        pendingRefresh = false
                        refreshAfterMutation()
                    }
                }
            }

    fun retryAfterForbidden() {
        _isForbidden.value = false
        resume()
        refresh()
    }

    // --- #149 inline editing (desktop only, #97 Q4) ---

    /**
     * Opens the editor on a cell. While a commit is in flight the click is ignored (the
     * blur-commit of a price editor dispatches first and owns the machine); otherwise an
     * existing editor is replaced — safe because a price editor with an edited draft
     * commits on blur (clicking another cell) before this runs, and a dropdown editor
     * only ever holds an unchanged draft until a selection commits.
     */
    fun startEdit(
        sessionId: String,
        field: DashboardEditField,
    ) {
        val current = currentEditState.value
        if (!canEditState.value || !canReplaceEdit(current)) return
        val row = lastDataCache.value?.sessions?.firstOrNull { it.id == sessionId }
        // #601 max-2: vanished-row, disallowed-field, and fresh-edit legs share one when-exit.
        when {
            row == null -> {
                if (current != null && lastDataCache.value?.sessions?.none { it.id == current.sessionId } == true) {
                    clearEdit()
                }
            }

            !fieldEditAllowed(row, field) -> {
                Unit
            }

            else -> {
                if (current?.error != null) clearEdit()
                editGeneration++
                currentEditState.value = beginEdit(row, field)
            }
        }
    }

    fun updateDraft(draft: String) {
        currentEditState.value = currentEditState.value?.withDraft(draft)
    }

    /** #403 — the reason input of the REMITTED-day editor. */
    fun updateReason(reason: String) {
        currentEditState.value = currentEditState.value?.withReason(reason)
    }

    fun discardEdit() {
        val state = currentEditState.value ?: return
        if (state.inFlight) return
        clearEdit()
    }

    private fun clearEdit() {
        if (currentEditState.value != null) {
            editGeneration++
            currentEditState.value = null
        }
    }

    /**
     * Pessimistic commit (ADR-0022): nothing changes on screen until the PATCH succeeds.
     * An unchanged draft exits without a request; an invalid price draft fails client-side
     * (the #135 parse-mirror pattern — the backend 400 never sees it); a conflict-state
     * commit is blocked (Reload is the sanctioned path). The post-dispatch machine
     * transitions all live inside the handler call (transform / onNonSuccess / onError) so
     * there is no second transition site to drift (the #142/#147 lesson); the pre-dispatch
     * guards above run before the request is sent.
     */
    fun commitEdit() {
        val state = currentEditState.value ?: return
        // The 409 conflict is resolved by Reload, not by re-dispatching the same stale
        // version (a retry without reload is a guaranteed 409 — pass-1 finding: the
        // blur-commit on the Reload click re-dispatched the doomed PATCH and swallowed
        // the first Reload click). In-flight commits are likewise single-shot.
        if (state.inFlight || state.conflict) return
        if (prepareEdit(state) == null) return
        dispatchEdit(state)
    }

    private fun prepareEdit(state: DashboardEditState): DashboardSessionResponse? {
        val row =
            if (canEditState.value) {
                lastDataCache.value?.sessions?.firstOrNull { it.id == state.sessionId }
            } else {
                null
            }
        val failure = row?.let { draftValidationFailure(state, it) }
        return when {
            row == null -> {
                clearEdit()
                null
            }

            !draftChanged(state, row) -> {
                logInfo("DashboardVM", "edit discarded — draft unchanged")
                clearEdit()
                null
            }

            failure == null -> {
                row
            }

            else -> {
                currentEditState.value = state.asFailed(failure)
                null
            }
        }
    }

    private fun dispatchEdit(state: DashboardEditState) {
        val requestGeneration = editGeneration
        currentEditState.value = state.asInFlight()
        val request = editRequest(state)
        handler.launchStatelessGuarded(
            operation = "updateSession",
            endpoint = "PATCH ${request.path}",
            block = {
                apiClient.httpClient.patch(request.path) {
                    setBody(request.body)
                }
            },
            guarded =
                GuardedStateless(
                    // #528 — decode/commit split: a discard/new-edit mid-decode must not commit the
                    // old row onto the new machine state.
                    decode = { it.body<SessionResponse>() },
                    commit = { updated ->
                        // The updated row is committed to the machine ([editState] carries what the UI
                        // renders — the #168 state-less launch has no result flow to write).
                        commitRow(updated)
                        clearEdit()
                    },
                    onNonSuccess = { response ->
                        when (response.status.value) {
                            // Q4: 403 — capability revoked mid-edit: silent exit + all status-edit
                            // affordances vanish. The route checks base edit authority before correction
                            // authority, so a status 403 must revoke both locally (fail closed).
                            403 -> {
                                logWarn("DashboardVM", "edit forbidden (403) — affordance hidden")
                                clearEdit()
                                locallyRevokedEditContext = capabilityContext
                                canEditState.value = false
                                if (state.field == DashboardEditField.STATUS) {
                                    locallyRevokedCorrectionBranch = capabilityContext.first
                                    canCorrectStatusState.value = false
                                }
                            }

                            // ADR-0022: 409 — version conflict: keep the draft + inline error +
                            // Reload action; reload re-baselines the version (never a silent
                            // lost update — the expectedVersion is the edit-start snapshot).
                            409 -> {
                                currentEditState.value = currentEditState.value?.asConflict(CONFLICT_MESSAGE)
                            }

                            // Model A: any other HTTP failure keeps the draft + inline error.
                            else -> {
                                currentEditState.value =
                                    currentEditState.value?.asFailed(
                                        "Update failed (${response.status.value}) — retry or discard",
                                    )
                            }
                        }
                    },
                    onError = {
                        currentEditState.value =
                            currentEditState.value?.asFailed("Update failed — check your connection and retry")
                    },
                    stale = { editGeneration != requestGeneration },
                ),
        )
    }

    /**
     * The 409 path's Reload action: refresh, then re-baseline the machine from the fresh
     * row. Guards (pass-1 findings): only re-baselines when the fresh row actually carries a
     * newer version (a failed or silently-cancelled refresh must NOT clear the conflict —
     * the user would retry a stale version forever with no visible failure); and only for
     * the machine still being edited (a discard or a new edit during the reload owns the
     * state).
     */
    fun reloadAfterConflict() {
        val state = currentEditState.value ?: return
        if (state.inFlight || !state.conflict) return
        viewModelScope.launch {
            refresh().join()
            val current =
                currentEditState.value
                    ?: return@launch
            if (current.sessionId != state.sessionId || current.field != state.field || !current.conflict) {
                return@launch
            }
            val row = lastDataCache.value?.sessions?.firstOrNull { it.id == current.sessionId } ?: return@launch
            if (row.version <= current.baselineVersion) {
                // No newer data landed (refresh was cancelled by an in-flight poll, or the
                // fetch failed) — the conflict stands; the user clicks Reload again.
                return@launch
            }
            currentEditState.value = current.afterReload(row)
        }
    }

    // --- Edit-policy support (#135 client-side 400 mirrors, commit-row merge, request build) ---

    private fun fieldEditAllowed(
        row: DashboardSessionResponse,
        field: DashboardEditField,
    ): Boolean =
        dayEditAllowed() &&
            (
                field != DashboardEditField.STATUS ||
                    statusEditAllowed(
                        isWalkIn = row.isWalkIn,
                        currentStatus = row.sessionStatus,
                        hasCorrectionAuthority = canCorrectStatusState.value,
                        dayStatus = dayStatusState.value,
                    )
            )

    private fun canReplaceEdit(current: DashboardEditState?): Boolean =
        current?.inFlight != true &&
            (
                current?.error == null ||
                    // Keep a failed draft until it is discarded, unless its row vanished from the list.
                    lastDataCache.value?.sessions?.none { it.id == current.sessionId } == true
            )

    private fun commitRow(updated: SessionResponse) {
        val data = lastDataCache.value ?: return
        val rows =
            data.sessions.map { row ->
                if (row.id == updated.id) {
                    mergeCommittedRow(row, updated)
                } else {
                    row
                }
            }
        lastDataCache.value = data.copy(sessions = rows)
    }

    private fun mergeCommittedRow(
        row: DashboardSessionResponse,
        updated: SessionResponse,
    ): DashboardSessionResponse =
        row.copy(
            sessionType = updated.sessionType,
            isWalkIn = updated.isWalkIn,
            sessionStatus = updated.sessionStatus,
            basePrice = updated.basePrice,
            finalPrice = updated.finalPrice,
            remarks = updated.remarks,
            otherConcerns = updated.otherConcerns,
            bookedAt = updated.bookedAt,
            nextAppointmentDate = updated.nextAppointmentDate,
            version = updated.version,
            // dashboard-only fields (clientName / isVoided / practitioners / concerns)
            // keep the existing row's — the PATCH response carries none of them.
        )

    /**
     * The client-side mirrors of the backend 400s (#135): an invalid price draft or a
     * missing REMITTED-day reason fail the edit before any request is sent.
     */
    private fun draftValidationFailure(
        state: DashboardEditState,
        row: DashboardSessionResponse,
    ): String? =
        when {
            state.field == DashboardEditField.STATUS && !dayEditAllowed() -> DAY_STATE_UNAVAILABLE_MESSAGE

            state.field == DashboardEditField.STATUS &&
                state.draft !in
                statusOptionsFor(
                    isWalkIn = row.isWalkIn,
                    currentStatus = baselineStatus(state, row),
                    hasCorrectionAuthority = canCorrectStatusState.value,
                    dayStatus = dayStatusState.value,
                ) -> INVALID_STATUS_MESSAGE

            state.field == DashboardEditField.FINAL_PRICE && !dayEditAllowed() -> DAY_STATE_UNAVAILABLE_MESSAGE

            state.field == DashboardEditField.FINAL_PRICE && !finalPriceInputValid(state.draft) -> INVALID_PRICE_MESSAGE

            remittedReasonRequired(dayStatusState.value) && state.reason.isBlank() -> REASON_REQUIRED_MESSAGE

            else -> null
        }

    private fun baselineStatus(
        state: DashboardEditState,
        row: DashboardSessionResponse,
    ): SessionStatus =
        runCatching { SessionStatus.valueOf(state.baselineValue) }
            .getOrElse { row.sessionStatus }

    private fun editRequest(state: DashboardEditState): EditRequest {
        val reason = normalizedReason(state.reason).ifEmpty { null }
        return when (state.field) {
            DashboardEditField.STATUS -> {
                EditRequest(
                    ApiRoutes.sessionStatus(state.sessionId),
                    UpdateSessionStatusRequest(
                        SessionStatus
                            .valueOf(state.draft),
                        state.baselineVersion,
                        reason,
                    ),
                )
            }

            DashboardEditField.FINAL_PRICE -> {
                EditRequest(
                    ApiRoutes.sessionFinalPrice(state.sessionId),
                    UpdateSessionFinalPriceRequest(state.draft.trim(), state.baselineVersion, reason),
                )
            }
        }
    }

    // --- Capability + day-status seam (#403 era) ---

    private fun observeCapabilities() {
        if (capabilityJob?.isActive == true) return
        capabilityJob =
            viewModelScope.launch {
                // #498 — one coherent snapshot per emission (no multi-flow combine/reconstruction).
                AppSessionState.snapshot.collect { snap ->
                    val capabilities = snap.capabilities
                    val branchId = snap.clock?.branchId
                    val dayId = snap.clock?.branchDayId
                    val baseCanEdit =
                        capabilities.hasBranchOrDayCapability(
                            code = CapabilityCodes.EDIT_BRANCH_DATA,
                            branchId = branchId,
                            dayId = dayId,
                        )
                    val baseCanCorrectStatus =
                        capabilities.hasCapability(
                            CapabilityCodes.EDIT_PAST_DAY,
                            CapabilityContextType.BRANCH,
                            branchId,
                        )
                    val context = branchId to dayId
                    val update =
                        updateCapabilities(
                            capabilities = capabilities,
                            baseCanEdit = baseCanEdit,
                            baseCanCorrectStatus = baseCanCorrectStatus,
                            context = context,
                        )
                    applyCapabilityUpdate(update)
                }
            }
    }

    private fun updateCapabilities(
        capabilities: List<UserCapabilityResponse>,
        baseCanEdit: Boolean,
        baseCanCorrectStatus: Boolean,
        context: Pair<String?, String?>,
    ): CapabilityUpdate {
        val wasCanEdit = canEditState.value
        val contextChanged = capabilityContext != context
        val branchChanged = capabilityContext.first != context.first
        val capabilitiesChanged = capabilitySnapshot != capabilities
        resetLocalRevocations(contextChanged, branchChanged, capabilitiesChanged)
        capabilitySnapshot = capabilities
        val canEdit = baseCanEdit && locallyRevokedEditContext != context
        val canCorrectStatus = baseCanCorrectStatus && locallyRevokedCorrectionBranch != context.first
        if (contextChanged || capabilitiesChanged || wasCanEdit != canEdit) {
            dayGeneration++
        }
        capabilityContext = context
        canEditState.value = canEdit
        canCorrectStatusState.value = canCorrectStatus
        return CapabilityUpdate(contextChanged, wasCanEdit, canEdit, canCorrectStatus)
    }

    private fun resetLocalRevocations(
        contextChanged: Boolean,
        branchChanged: Boolean,
        capabilitiesChanged: Boolean,
    ) {
        if (contextChanged || capabilitiesChanged) {
            locallyRevokedEditContext = null
        }
        if (branchChanged || capabilitiesChanged) {
            locallyRevokedCorrectionBranch = null
        }
    }

    private fun applyCapabilityUpdate(update: CapabilityUpdate) {
        when {
            !update.canEdit -> {
                dayStatusState.value = null
                clearEdit()
            }

            update.contextChanged || !update.wasCanEdit -> {
                dayStatusState.value = null
                clearEdit()
                loadDayStatus()
            }

            dayStatusState.value != null && dayStatusState.value != DayStatus.OPEN && !update.canCorrectStatus -> {
                clearEdit()
            }

            !update.canCorrectStatus && isCorrectionEdit() -> {
                clearEdit()
            }
        }
    }

    private fun isCorrectionEdit(): Boolean {
        val state = currentEditState.value ?: return false
        if (state.field != DashboardEditField.STATUS) return false
        val row = lastDataCache.value?.sessions?.firstOrNull { it.id == state.sessionId } ?: return false
        val target = runCatching { SessionStatus.valueOf(state.draft) }.getOrNull() ?: return false
        return isStatusCorrection(baselineStatus(state, row), target)
    }

    /**
     * #403 — the day-status read behind the reason-required gate. Fired with each dashboard
     * refresh; a failure clears status so the desktop affordance fails closed until a fresh
     * state lands.
     */
    private fun loadDayStatus() {
        val branchId =
            AppSessionState.snapshot.value.clock
                ?.branchId ?: return
        if (!canEditState.value) return
        dayStatusState.value = null
        if (dayStatusBranchId != branchId) {
            dayStatusBranchId = branchId
            clearEdit()
        }
        ++dayGeneration
        val generation = dayGeneration
        handler.launchStatelessGuarded(
            operation = "loadDayStatus",
            endpoint = "GET /api/branches/$branchId/today",
            block = { apiClient.httpClient.get(ApiRoutes.branchToday(branchId)) },
            guarded =
                GuardedStateless(
                    // #528 — decode/commit split: a context switch mid-decode drops the body instead
                    // of writing the old branch's status onto the new surface.
                    decode = { response -> response.body<BranchDayTodayResponse>().status },
                    commit = { status ->
                        dayStatusState.value = status
                        if (status != DayStatus.OPEN && !canCorrectStatusState.value) {
                            clearEdit()
                        }
                    },
                    onNonSuccess = { response ->
                        // 401 is the global auth path (ApiClient.onUnauthorized); 403 = grant revoked.
                        if (response.status.value == 401 || response.status.value == 403) {
                            dayStatusState.value = null
                            locallyRevokedEditContext = capabilityContext
                            locallyRevokedCorrectionBranch = capabilityContext.first
                            canEditState.value = false
                            canCorrectStatusState.value = false
                            clearEdit()
                        } else {
                            dayStatusState.value = null
                            logWarn("DashboardVM", "day-status read failed (${response.status.value}) — edit disabled")
                        }
                    },
                    onError = {
                        dayStatusState.value = null
                    },
                    stale = { generation != dayGeneration },
                ),
        )
    }

    private fun dayEditAllowed(): Boolean =
        dayStatusState.value?.let { it == DayStatus.OPEN || canCorrectStatusState.value } == true

    private companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val STALE_THRESHOLD = 2
        private const val ERROR_THRESHOLD = 5
    }
}
