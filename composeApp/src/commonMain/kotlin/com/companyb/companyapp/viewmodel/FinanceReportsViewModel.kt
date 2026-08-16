package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.dto.RestoreExpenseRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.dto.UpdateExpenseRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.CapabilityContext
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasBranchOrDayCapability
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.ui.screen.FeedWindow
import com.companyb.companyapp.ui.screen.ReportMode
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

/**
 * ViewModel for the merged Finance & Reports screen (#101 D1-D8 + #105 D1-D7, built #154).
 *
 * Two layered surfaces on one VM:
 * - **Feed (read-only default)**: branch picker (accessible branches) + mode tabs +
 *   windowed day feed (keyset paging, keep-last, generation-guarded) + day detail
 *   (figures + per-day export) + monthly rollup card + public branch-type exports.
 * - **Edit mode** (toggle visible only for holders of any edit capability — #105 D1):
 *   per-day expense log (create/update/delete/restore, dimmed deleted rows), compensation
 *   list + assign/PATCH, allowances ("not in P&L"), all pessimistic per ADR-0022
 *   (409 → inline error + section reload; 403 → silent exit; failure keeps the attempted
 *   value with an inline error).
 *
 * The backend 403/400/409 paths stay authoritative; the frontend capability gates are the
 * #156 context model (branch-scoped triples resolved against the VIEWED branch — the branch
 * the backend gates via the day row).
 */
@OptIn(ExperimentalUuidApi::class)
class FinanceReportsViewModel(
    private val apiClient: ApiClient,
    private val now: Instant = Clock.System.now(),
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "FinanceVM")
    private val today: LocalDate =
        now.toLocalDateTime(TimeZone.of("Asia/Manila")).date
    private val defaultMonth: YearMonth = YearMonth(today.year, today.month.ordinal + 1)

    // ─────────────────────────── branches ───────────────────────────

    private val _branches = MutableStateFlow<UiState<List<BranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<BranchResponse>>> = _branches.asStateFlow()

    private val _selectedBranchId = MutableStateFlow<String?>(null)
    val selectedBranchId: StateFlow<String?> = _selectedBranchId.asStateFlow()

    fun loadBranches() {
        if (_branches.value is UiState.Loading) return
        handler.launch(
            state = _branches,
            operation = "loadBranches",
            endpoint = "GET /api/branches/accessible",
            block = { apiClient.httpClient.get("/api/branches/accessible") },
            transform = {
                val list = it.body<List<BranchResponse>>()
                _branches.value = UiState.Success(list)
                if (_selectedBranchId.value == null) {
                    // Default: the clocked-in branch when the user can see it, else the first
                    // accessible branch (Accountant: GLOBAL view, no selected branch — D3).
                    val default =
                        list.firstOrNull { b -> b.id == SessionState.selectedBranchId.value }
                            ?: list.firstOrNull()
                    if (default != null) {
                        _selectedBranchId.value = default.id
                        refreshWindowAndFeed()
                    }
                }
                list
            },
        )
    }

    fun selectBranch(branchId: String) {
        if (branchId == _selectedBranchId.value) return
        _selectedBranchId.value = branchId
        _selectedDay.value = null
        _editMode.value = false
        _paramError.value = null
        clearEditData()
        refreshWindowAndFeed()
        // #105 D4 — the MONTHLY rollup card is pinned; a branch switch must refetch it (and a
        // superseded in-flight rollup must stay inert — rollupGeneration bump in loadMonthlyRollup).
        _monthlyRollup.value = UiState.Idle
        if (_mode.value == ReportMode.MONTHLY) {
            loadMonthlyRollup()
        }
    }

    // ─────────────────────────── mode + params ───────────────────────────

    private val _mode = MutableStateFlow(ReportMode.DAILY)
    val mode: StateFlow<ReportMode> = _mode.asStateFlow()

    /** Editable `yyyy-MM` text for the MONTHLY mode and the ALL_TIME calendar jump. */
    private val _monthInput = MutableStateFlow(defaultMonth.toString())
    val monthInput: StateFlow<String> = _monthInput.asStateFlow()

    private val _rangeFromInput = MutableStateFlow("")
    val rangeFromInput: StateFlow<String> = _rangeFromInput.asStateFlow()

    private val _rangeToInput = MutableStateFlow("")
    val rangeToInput: StateFlow<String> = _rangeToInput.asStateFlow()

    /** Applied date-range window (validated at apply time). */
    private val _appliedRange = MutableStateFlow<Pair<String, String>?>(null)
    val appliedRange: StateFlow<Pair<String, String>?> = _appliedRange.asStateFlow()

    /** Applied ALL_TIME jump month (null = unbounded all-time). */
    private val _jumpMonth = MutableStateFlow<YearMonth?>(null)
    val jumpMonth: StateFlow<YearMonth?> = _jumpMonth.asStateFlow()

    /** Mode-parameter validation errors (rendered inline next to the field). */
    private val _paramError = MutableStateFlow<String?>(null)
    val paramError: StateFlow<String?> = _paramError.asStateFlow()

    private val _monthlyRollup = MutableStateFlow<UiState<MonthlyRemittanceSummaryResponse?>>(UiState.Idle)
    val monthlyRollup: StateFlow<UiState<MonthlyRemittanceSummaryResponse?>> = _monthlyRollup.asStateFlow()

    /** Rollup responses from a superseded branch/mode are inert (P4 pass-1 HARD). */
    private var rollupGeneration = 0

    fun setMode(mode: ReportMode) {
        if (mode == _mode.value) return
        _mode.value = mode
        _paramError.value = null
        refreshWindowAndFeed()
        if (mode == ReportMode.MONTHLY) {
            loadMonthlyRollup()
        } else {
            _monthlyRollup.value = UiState.Idle
        }
    }

    fun setMonthInput(raw: String) {
        _monthInput.value = raw
    }

    fun applyMonth() {
        val month =
            com.companyb.companyapp.ui.screen
                .parseYearMonthInput(_monthInput.value)
        if (month == null) {
            _paramError.value = "Month must be yyyy-MM"
            return
        }
        _paramError.value = null
        _monthlyRollup.value = UiState.Idle
        refreshWindowAndFeed()
        loadMonthlyRollup()
    }

    fun clearRange() {
        _appliedRange.value = null
        _paramError.value = null
        refreshWindowAndFeed()
    }

    fun setRangeInputs(
        from: String,
        to: String,
    ) {
        _rangeFromInput.value = from
        _rangeToInput.value = to
    }

    fun applyRange() {
        val from =
            com.companyb.companyapp.ui.screen
                .parseDateInput(_rangeFromInput.value)
        val to =
            com.companyb.companyapp.ui.screen
                .parseDateInput(_rangeToInput.value)
        when {
            from == null -> {
                _paramError.value = "From must be yyyy-MM-dd"
            }

            to == null -> {
                _paramError.value = "To must be yyyy-MM-dd"
            }

            from > to -> {
                _paramError.value = "From must be on or before To"
            }

            else -> {
                _paramError.value = null
                _appliedRange.value = from.toString() to to.toString()
                refreshWindowAndFeed()
            }
        }
    }

    fun setJumpInput(raw: String) {
        _monthInput.value = raw
    }

    fun applyJump() {
        val month =
            com.companyb.companyapp.ui.screen
                .parseYearMonthInput(_monthInput.value)
        if (month == null) {
            _paramError.value = "Month must be yyyy-MM"
            return
        }
        _paramError.value = null
        _jumpMonth.value = month
        refreshWindowAndFeed()
    }

    fun clearJump() {
        _jumpMonth.value = null
        _monthInput.value = defaultMonth.toString()
        _paramError.value = null
        refreshWindowAndFeed()
    }

    // ─────────────────────────── feed ───────────────────────────

    private val _feedEntries = MutableStateFlow<UiState<List<DailySalesSummaryResponse>>>(UiState.Idle)
    val feedEntries: StateFlow<UiState<List<DailySalesSummaryResponse>>> = _feedEntries.asStateFlow()

    private val _nextCursor = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    // Every window/mode/branch change bumps the generation; an in-flight page from a superseded
    // generation is inert (no list/cursor/error writes) — the AuditLog #144 shape.
    private var feedGeneration = 0

    fun refreshFeed() {
        if (_mode.value == ReportMode.DATE_RANGE && _appliedRange.value == null) return
        val branchId = _selectedBranchId.value ?: return
        if (_isRefreshing.value || _isLoadingMore.value) return
        // A refresh replaces the day rows — a stale selection would keep showing the
        // pre-refresh figures (pass-9 SOFT).
        _selectedDay.value = null
        _editMode.value = false
        _refreshError.value = null
        _loadMoreError.value = null
        fetchPage(FetchMode.Refresh, cursor = null, branchId = branchId)
    }

    fun loadMore() {
        val branchId = _selectedBranchId.value ?: return
        val cursor = _nextCursor.value ?: return
        if (_isLoadingMore.value || _isRefreshing.value) return
        _loadMoreError.value = null
        fetchPage(FetchMode.LoadMore, cursor = cursor, branchId = branchId)
    }

    fun retryFeed() {
        if (_mode.value == ReportMode.DATE_RANGE && _appliedRange.value == null) return
        val branchId = _selectedBranchId.value ?: return
        _feedEntries.value = UiState.Loading
        fetchPage(FetchMode.Cold, cursor = null, branchId = branchId)
    }

    private fun refreshWindowAndFeed() {
        feedGeneration++
        _isRefreshing.value = false
        _isLoadingMore.value = false
        _nextCursor.value = null
        _refreshError.value = null
        _loadMoreError.value = null
        _selectedDay.value = null
        _editMode.value = false
        // Pass-3 HARD — a mode/window switch while editing must not leave the OLD day's
        // section data armed: re-entering edit on another day would render the old rows
        // during the load window and Edit/Delete would mutate the wrong day's records.
        clearEditData()
        val branchId = _selectedBranchId.value ?: return
        // Pass-5 SOFT — DATE_RANGE has no window before Apply: no unbounded all-time fetch
        // fires behind the hint (the hint branch renders instead of the feed).
        if (_mode.value == ReportMode.DATE_RANGE && _appliedRange.value == null) {
            _feedEntries.value = UiState.Idle
            return
        }
        _feedEntries.value = UiState.Loading
        fetchPage(FetchMode.Cold, cursor = null, branchId = branchId)
    }

    private fun currentWindow(): FeedWindow =
        com.companyb.companyapp.ui.screen.feedWindowFor(
            mode = _mode.value,
            today = today,
            month =
                com.companyb.companyapp.ui.screen
                    .parseYearMonthInput(_monthInput.value)
                    ?: defaultMonth,
            rangeFrom = _appliedRange.value?.first,
            rangeTo = _appliedRange.value?.second,
            jumpMonth = _jumpMonth.value,
        )

    private fun fetchPage(
        mode: FetchMode,
        cursor: String?,
        branchId: String,
    ) {
        val generation = feedGeneration
        if (mode == FetchMode.Refresh) _isRefreshing.value = true
        if (mode == FetchMode.LoadMore) _isLoadingMore.value = true
        handler.launchStateless(
            operation = mode.operationName,
            endpoint = "GET /api/branches/$branchId/daily-summaries",
            block = {
                apiClient.httpClient.get("/api/branches/$branchId/daily-summaries") {
                    cursor?.let { parameter("cursor", it) }
                    parameter("limit", FEED_PAGE_SIZE)
                    currentWindow().from?.let { parameter("from", it) }
                    currentWindow().to?.let { parameter("to", it) }
                }
            },
            transform = {
                val page = it.body<DailySalesSummaryBrowseResponse>()
                if (generation == feedGeneration) {
                    val listAlreadyCommitted =
                        mode == FetchMode.Cold && _feedEntries.value is UiState.Success
                    if (listAlreadyCommitted) {
                        logWarn("FinanceVM", "cold feed success suppressed — feed superseded")
                    } else {
                        _refreshError.value = null
                        _nextCursor.value = page.nextCursor
                        applyPage(mode, page.entries)
                    }
                    finish(mode)
                }
            },
            onNonSuccess = { response ->
                if (generation == feedGeneration) {
                    handlePageFailure(mode, "feed failed: ${response.status.value}")
                    finish(mode)
                }
            },
            onError = { e ->
                // Network or deserialization failure — same error surface + flag cleanup so the
                // list state and buttons never freeze (keep-last-list); gated on the current
                // feed generation (a superseded fetch's failure must not surface on the new
                // list). onError is that single surface (#169).
                if (generation == feedGeneration) {
                    handlePageFailure(mode, "feed failed: ${e.message ?: "network error"}")
                    finish(mode)
                }
            },
        )
    }

    private fun applyPage(
        mode: FetchMode,
        entries: List<DailySalesSummaryResponse>,
    ) {
        when (mode) {
            FetchMode.Cold, FetchMode.Refresh -> {
                _feedEntries.value = UiState.Success(entries)
            }

            FetchMode.LoadMore -> {
                val current =
                    (_feedEntries.value as? UiState.Success<List<DailySalesSummaryResponse>>)
                        ?.data
                        .orEmpty()
                _feedEntries.value = UiState.Success(current + entries)
            }
        }
    }

    private fun handlePageFailure(
        mode: FetchMode,
        message: String,
    ) {
        when (mode) {
            FetchMode.Cold -> {
                if (_feedEntries.value !is UiState.Success) {
                    _feedEntries.value = UiState.Error(message)
                } else {
                    logWarn("FinanceVM", "cold feed failure suppressed — feed superseded: $message")
                }
            }

            FetchMode.Refresh -> {
                _refreshError.value = message
            }

            FetchMode.LoadMore -> {
                _loadMoreError.value = message
            }
        }
    }

    private fun finish(mode: FetchMode) {
        if (mode == FetchMode.Refresh) _isRefreshing.value = false
        if (mode == FetchMode.LoadMore) _isLoadingMore.value = false
    }

    // ─────────────────────────── monthly rollup ───────────────────────────

    private fun loadMonthlyRollup() {
        val branchId = _selectedBranchId.value ?: return
        val month =
            com.companyb.companyapp.ui.screen
                .parseYearMonthInput(_monthInput.value)
                ?: defaultMonth
        rollupGeneration++
        val generation = rollupGeneration
        _monthlyRollup.value = UiState.Loading
        handler.launchStateless(
            operation = "loadMonthlyRollup",
            endpoint = "GET /api/branches/$branchId/monthly-summary",
            block = {
                apiClient.httpClient.get("/api/branches/$branchId/monthly-summary") {
                    parameter("year", month.year)
                    parameter("month", month.month.ordinal + 1)
                }
            },
            transform = {
                if (generation == rollupGeneration) {
                    // 404 (no remittance submitted that month — the #105 F5 shape) is NOT an
                    // error: the rollup card simply doesn't render. Transform only sees 2xx —
                    // the 404 branch lives in onNonSuccess below.
                    _monthlyRollup.value = UiState.Success(it.body<MonthlyRemittanceSummaryResponse>())
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == rollupGeneration) {
                    if (response.status == HttpStatusCode.NotFound) {
                        _monthlyRollup.value = UiState.Success(null)
                    } else {
                        _monthlyRollup.value = UiState.Error("monthly rollup failed: ${response.status.value}")
                    }
                }
            },
            // #170 — the loadReliefDay shape: a transport failure moves Loading → Error
            // (terminal) instead of parking on Loading forever (the #168/#169 P5 sibling —
            // pre-fix the sole onError-less stateless site parking a UiState on Loading;
            // loadSent is onError-less by keep-last design — no state to park).
            onError = { e ->
                if (generation == rollupGeneration) {
                    _monthlyRollup.value = UiState.Error(e.message ?: "Unknown error")
                }
            },
        )
    }

    // ─────────────────────────── selection + edit mode ───────────────────────────

    private val _selectedDay = MutableStateFlow<DailySalesSummaryResponse?>(null)
    val selectedDay: StateFlow<DailySalesSummaryResponse?> = _selectedDay.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun selectDay(day: DailySalesSummaryResponse?) {
        _selectedDay.value = day
    }

    // ─────────────────────────── relief day entry (#158) ───────────────────────────

    private val _reliefDay = MutableStateFlow<UiState<DailySalesSummaryResponse>>(UiState.Idle)
    val reliefDay: StateFlow<UiState<DailySalesSummaryResponse>> = _reliefDay.asStateFlow()

    /** Generation guard for [loadReliefDay] — a superseded relief fetch stays inert (the #143 class). */
    private var reliefGeneration = 0

    /**
     * #158 — the relief day-scoped read entry: a BRANCH_DAY grant holder (no VIEW_BRANCH_DATA)
     * fetches a single day by date via the day-scoped summary read. The branch is the
     * clocked-in branch (the relief branch — the picker lists only BRANCH-granted branches,
     * which a relief delegate has none of). Success seeds [selectedDay] so the day detail +
     * editor flows work off the same state.
     */
    fun loadReliefDay(date: String) {
        val branchId =
            SessionState.selectedBranchId.value
                ?: run {
                    _reliefDay.value = UiState.Error("No clocked-in branch")
                    return
                }
        // Pass-1 HARD (P3/P4) — the #144-ack/browse stale-state class: a relief-day switch
        // while editing must not leave the OLD day's edit sections armed (editExpenses etc.
        // hold the previous day's rows; a later Edit toggle would mutate the wrong day).
        // Mirrors refreshWindowAndFeed's reset + loadSection's generation guard. The state
        // write is manual (the state-less #168 launch writes no Loading/Success) so a
        // superseded response never lands Success on the UI. The VM's [_selectedBranchId] is
        // deliberately NOT touched:
        // the relief surface reads the clocked-in branch from SessionState (pass-2 HARD —
        // writing it would pin the hybrid's reports surface to the relief branch).
        reliefGeneration++
        val generation = reliefGeneration
        _editMode.value = false
        _selectedDay.value = null
        clearEditData()
        _reliefDay.value = UiState.Loading
        handler.launchStateless(
            operation = "loadReliefDay",
            endpoint = "GET /api/branches/$branchId/daily-summary?date=$date",
            block = { apiClient.httpClient.get("/api/branches/$branchId/daily-summary?date=$date") },
            transform = {
                val day = it.body<DailySalesSummaryResponse>()
                if (generation == reliefGeneration) {
                    _reliefDay.value = UiState.Success(day)
                    selectDay(day)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == reliefGeneration) {
                    _reliefDay.value = UiState.Error("loadReliefDay failed: ${response.status.value}")
                }
            },
            onError = { e ->
                if (generation == reliefGeneration) {
                    _reliefDay.value = UiState.Error(e.message ?: "Unknown error")
                }
            },
        )
    }

    /**
     * #158 pass-2 — the hybrid exit path: leaving the relief section clears its state so a
     * re-entry via the chip starts clean (no stale day under a fresh date input).
     */
    fun clearReliefState() {
        reliefGeneration++
        _reliefDay.value = UiState.Idle
        _editMode.value = false
        _selectedDay.value = null
        clearEditData()
    }

    /**
     * #105 D1 — Edit-toggle visibility, #156 branch-scoped: the checks resolve against the
     * VIEWED branch ([_selectedBranchId] — the branch the day data belongs to, switchable via
     * the picker; defaults to the clocked-in branch). The backend resolves the same branch
     * from the day row (`CapabilityFilter.requireBranchCapability`), so the toggle, the
     * section loads and the backend 403s all agree. A null viewed branch fails closed.
     */
    fun hasAssignCapability(): Boolean =
        SessionState.capabilities.value.hasCapability(
            CapabilityCodes.ASSIGN_COMPENSATION,
            CapabilityContext.BRANCH,
            _selectedBranchId.value,
        )

    /**
     * #105 D1/#156 — the EDIT_BRANCH_DATA leg of the edit-toggle check, now with the
     * #158 day leg: the BRANCH triple at the viewed branch OR a BRANCH_DAY relief grant
     * for the VIEWED day ([_selectedDay] — the day row the backend gates via; a relief
     * delegate edits their granted day without any BRANCH grant). A null day fails the
     * day leg closed.
     */
    fun hasEditBranchDataCapability(): Boolean =
        SessionState.capabilities.value.hasBranchOrDayCapability(
            CapabilityCodes.EDIT_BRANCH_DATA,
            _selectedBranchId.value,
            _selectedDay.value?.branchDayId,
        )

    fun hasEditCapabilities(): Boolean {
        val caps = SessionState.capabilities.value
        val branchId = _selectedBranchId.value
        // #158 — the EDIT_BRANCH_DATA leg includes the day-scoped relief grant; the
        // ASSIGN_COMPENSATION + EDIT_PAST_DAY legs stay BRANCH-only (not relief-eligible,
        // per the #157 surface).
        return hasEditBranchDataCapability() ||
            caps.hasCapability(CapabilityCodes.ASSIGN_COMPENSATION, CapabilityContext.BRANCH, branchId) ||
            caps.hasCapability(CapabilityCodes.EDIT_PAST_DAY, CapabilityContext.BRANCH, branchId)
    }

    fun setEditMode(on: Boolean) {
        if (on == _editMode.value) return
        _editMode.value = on
        if (on) {
            loadEditData()
        } else {
            clearEditData()
        }
    }

    // ─────────────────────────── edit data ───────────────────────────

    private val _editExpenses = MutableStateFlow<UiState<List<ExpenseResponse>>>(UiState.Idle)
    val editExpenses: StateFlow<UiState<List<ExpenseResponse>>> = _editExpenses.asStateFlow()

    private val _editCompensations = MutableStateFlow<UiState<List<CompensationResponse>>>(UiState.Idle)
    val editCompensations: StateFlow<UiState<List<CompensationResponse>>> = _editCompensations.asStateFlow()

    private val _editAllowances = MutableStateFlow<UiState<List<AllowanceResponse>>>(UiState.Idle)
    val editAllowances: StateFlow<UiState<List<AllowanceResponse>>> = _editAllowances.asStateFlow()

    private val _editUsers = MutableStateFlow<UiState<List<BranchDayUserResponse>>>(UiState.Idle)
    val editUsers: StateFlow<UiState<List<BranchDayUserResponse>>> = _editUsers.asStateFlow()

    // Per-action inline errors (ADR-0022 pessimistic axis) keyed by action key + per-action
    // in-flight guard (double-tap closure).
    private val actionTracker = ActionTracker<String>()
    val editErrors: StateFlow<Map<String, String>> = actionTracker.errors

    // One-shot conflict signal (pass-1 HARD, pass-2 reworked): a 409 adds the key to a NEW Set
    // instance so the screen's LaunchedEffect(conflicts) re-fires — the open edit dialog holds a
    // stale expectedVersion and must close (re-saving it would loop 409s; the reloaded row is
    // the retry source). The screen consumes the key (consumeConflict) after reacting, so a
    // repeat 409 on the same row re-emits, and a persisted key can never slam a LATER fresh
    // dialog shut. (Create-conflict keys — comp:create etc. — are never consumed; they surface
    // only as inline errors on dialogs that close on dismiss, and clearEditData clears them.)
    private val _conflicts = MutableStateFlow<Set<String>>(emptySet())
    val conflicts: StateFlow<Set<String>> = _conflicts.asStateFlow()

    fun consumeConflict(key: String) {
        _conflicts.value = _conflicts.value - key
    }

    val inFlightActions: StateFlow<Set<String>> = actionTracker.inFlight

    private var editDataGeneration = 0

    fun loadEditData() {
        val day = _selectedDay.value ?: return
        val branchDayId = day.branchDayId
        editDataGeneration++
        val generation = editDataGeneration
        if (hasEditBranchDataCapability()) {
            loadSection(
                generation,
                _editExpenses,
                "expenses",
                "/api/expenses",
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("expense:"),
            )
        }
        if (hasAssignCapability()) {
            loadSection(
                generation,
                _editCompensations,
                "compensations",
                "/api/compensations",
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("comp:"),
            )
            loadSection(
                generation,
                _editAllowances,
                "allowances",
                "/api/allowances",
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("allow:"),
            )
            loadSection(
                generation,
                _editUsers,
                "branch-day users",
                "/api/branch-days/$branchDayId/users",
                params = emptyList(),
            )
        }
    }

    fun reloadSection(section: EditSection) {
        val day = _selectedDay.value ?: return
        val generation = editDataGeneration
        when (section) {
            EditSection.EXPENSES -> {
                loadSection(
                    generation,
                    _editExpenses,
                    "expenses",
                    "/api/expenses",
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("expense:"),
                )
            }

            EditSection.COMPENSATIONS -> {
                loadSection(
                    generation,
                    _editCompensations,
                    "compensations",
                    "/api/compensations",
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("comp:"),
                )
            }

            EditSection.ALLOWANCES -> {
                loadSection(
                    generation,
                    _editAllowances,
                    "allowances",
                    "/api/allowances",
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("allow:"),
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private inline fun <reified T> loadSection(
        generation: Int,
        state: MutableStateFlow<UiState<List<T>>>,
        operation: String,
        endpoint: String,
        params: List<Pair<String, String>>,
        errorKeyPrefixes: List<String> = emptyList(),
    ) {
        handler.launchStateless(
            operation = operation,
            endpoint = "GET $endpoint",
            block = {
                apiClient.httpClient.get(endpoint) {
                    params.forEach { (k, v) -> parameter(k, v) }
                }
            },
            transform = {
                val list = it.body<List<T>>()
                if (generation == editDataGeneration) {
                    state.value = UiState.Success(list)
                    // #143 class — a fresh list supersedes the section's stale action
                    // errors (e.g. a 409-reload landing beside its own error line).
                    if (errorKeyPrefixes.isNotEmpty()) {
                        actionTracker.clearWhere { key -> errorKeyPrefixes.any { key.startsWith(it) } }
                    }
                }
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    state.value = UiState.Error("$operation failed: ${response.status.value}")
                }
            },
            onError = { e ->
                // Transport or deserialization failure: route to the section's error state —
                // gated on the current edit-data generation so a superseded day/branch load
                // can't error the cleared sections. onError is that single surface (#169).
                if (generation == editDataGeneration) {
                    state.value = UiState.Error("$operation failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    private fun clearEditData() {
        // P4 pass-1: bumping the generation makes in-flight section loads from a superseded
        // day/branch inert — they must not repopulate the cleared sections.
        editDataGeneration++
        _editExpenses.value = UiState.Idle
        _editCompensations.value = UiState.Idle
        _editAllowances.value = UiState.Idle
        _editUsers.value = UiState.Idle
        actionTracker.clear()
        _conflicts.value = emptySet()
    }

    // ─────────────────────────── expense actions ───────────────────────────

    fun createExpense(
        amount: String,
        categoryCode: String,
        notes: String?,
        reason: String?,
    ) {
        val day = _selectedDay.value ?: return
        val key = "expense:create"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "createExpense",
            endpoint = "POST /api/expenses",
            block = {
                apiClient.httpClient.post("/api/expenses") {
                    setBody(
                        CreateExpenseRequest(
                            id = newId(),
                            branchDayId = day.branchDayId,
                            amount = amount,
                            category = categoryCode,
                            notes = notes,
                            reason = reason,
                        ),
                    )
                }
            },
            transform = {
                val created = it.body<ExpenseResponse>()
                if (generation == editDataGeneration) {
                    val current = _editExpenses.value
                    if (current is UiState.Success) {
                        _editExpenses.value = UiState.Success(current.data + created)
                    } else {
                        // Pass-8 SOFT — appending onto an Error section would truncate the
                        // list to the new row; reload the section instead.
                        reloadSection(EditSection.EXPENSES)
                    }
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(key, "expense:create", response)
                }
            },
            onError = { e ->
                // Pass-9 HARD — a transport/timeout failure must keep the dialog open with an
                // inline error (the close-on-success effect keys on the ABSENCE of an error).
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "expense:create failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    fun updateExpense(
        expense: ExpenseResponse,
        amount: String,
        categoryCode: String,
        notes: String?,
        reason: String?,
    ) {
        val key = "expense:update:${expense.id}"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "updateExpense",
            endpoint = "PATCH /api/expenses/${expense.id}",
            block = {
                apiClient.httpClient.patch("/api/expenses/${expense.id}") {
                    setBody(
                        UpdateExpenseRequest(
                            amount = amount,
                            category = categoryCode,
                            notes = notes,
                            expectedVersion = expense.version,
                            reason = reason,
                        ),
                    )
                }
            },
            transform = {
                val updated = it.body<ExpenseResponse>()
                if (generation == editDataGeneration) {
                    replaceExpenseRow(updated)
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(
                        key,
                        "expense:update",
                        response,
                        conflictMessage = "Expense changed elsewhere — reloaded",
                    ) {
                        reloadSection(EditSection.EXPENSES)
                    }
                }
            },
            onError = { e ->
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "expense:update failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    fun deleteExpense(
        expense: ExpenseResponse,
        reason: String,
    ) {
        val key = "expense:delete:${expense.id}"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "deleteExpense",
            endpoint = "DELETE /api/expenses/${expense.id}",
            block = {
                apiClient.httpClient.delete("/api/expenses/${expense.id}") {
                    setBody(DeleteExpenseRequest(reason = reason))
                }
            },
            transform = {
                val deleted = it.body<ExpenseResponse>()
                if (generation == editDataGeneration) {
                    replaceExpenseRow(deleted)
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(key, "expense:delete", response)
                }
            },
            onError = { e ->
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "expense:delete failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    fun restoreExpense(
        expense: ExpenseResponse,
        reason: String?,
    ) {
        val key = "expense:restore:${expense.id}"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "restoreExpense",
            endpoint = "POST /api/expenses/${expense.id}/restore",
            block = {
                apiClient.httpClient.post("/api/expenses/${expense.id}/restore") {
                    setBody(RestoreExpenseRequest(reason = reason))
                }
            },
            transform = {
                val restored = it.body<ExpenseResponse>()
                if (generation == editDataGeneration) {
                    replaceExpenseRow(restored)
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(key, "expense:restore", response)
                }
            },
            onError = { e ->
                // Pass-10 HARD — the one onError the pass-9 batch missed: a transport failure
                // must keep the restore dialog open with an inline error (the close-on-success
                // effect keys on the ABSENCE of an error).
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "expense:restore failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    private fun replaceExpenseRow(updated: ExpenseResponse) {
        val current =
            (_editExpenses.value as? UiState.Success<List<ExpenseResponse>>)
                ?.data
                .orEmpty()
        _editExpenses.value =
            UiState.Success(current.map { if (it.id == updated.id) updated else it })
    }

    // ─────────────────────────── compensation actions ───────────────────────────

    fun createCompensation(
        userId: String,
        amount: String,
        note: String?,
        reason: String?,
    ) {
        val day = _selectedDay.value ?: return
        val key = "comp:create"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "createCompensation",
            endpoint = "POST /api/compensation",
            block = {
                apiClient.httpClient.post("/api/compensation") {
                    setBody(
                        CreateCompensationRequest(
                            id = newId(),
                            workBranchDayId = day.branchDayId,
                            payingBranchDayId = day.branchDayId,
                            userId = userId,
                            amount = amount,
                            note = note,
                            reason = reason,
                        ),
                    )
                }
            },
            transform = {
                val created = it.body<CompensationResponse>()
                if (generation == editDataGeneration) {
                    val current = _editCompensations.value
                    if (current is UiState.Success) {
                        _editCompensations.value = UiState.Success(current.data + created)
                    } else {
                        reloadSection(EditSection.COMPENSATIONS)
                    }
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    // #101 D4 — 409 duplicate (one per user per paying day) → inline error on the
                    // picker; the row is already compensated (the list shows it).
                    failActionOrSilent403(
                        key,
                        "comp:create",
                        response,
                        conflictMessage = "Already compensated on this day",
                    )
                }
            },
            onError = { e ->
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "comp:create failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    fun updateCompensation(
        compensation: CompensationResponse,
        amount: String,
        note: String?,
        reason: String?,
    ) {
        val key = "comp:update:${compensation.id}"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "updateCompensation",
            endpoint = "PATCH /api/compensation/${compensation.id}",
            block = {
                apiClient.httpClient.patch("/api/compensation/${compensation.id}") {
                    setBody(
                        UpdateCompensationRequest(
                            amount = amount,
                            expectedVersion = compensation.version,
                            note = note,
                            reason = reason,
                        ),
                    )
                }
            },
            transform = {
                val updated = it.body<CompensationResponse>()
                if (generation == editDataGeneration) {
                    _editCompensations.value =
                        UiState.Success(
                            (_editCompensations.value as? UiState.Success<List<CompensationResponse>>)
                                ?.data
                                .orEmpty()
                                .map { row -> if (row.id == updated.id) updated else row },
                        )
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(
                        key,
                        "comp:update",
                        response,
                        conflictMessage = "Compensation changed elsewhere — reloaded",
                    ) {
                        reloadSection(EditSection.COMPENSATIONS)
                    }
                }
            },
            onError = { e ->
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "comp:update failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    // ─────────────────────────── allowance actions ───────────────────────────

    fun createAllowance(
        userId: String,
        amount: String,
        reason: String?,
    ) {
        val day = _selectedDay.value ?: return
        val key = "allow:create"
        if (!actionTracker.begin(key)) return
        val generation = editDataGeneration
        handler.launchStateless(
            operation = "createAllowance",
            endpoint = "POST /api/allowances",
            block = {
                apiClient.httpClient.post("/api/allowances") {
                    setBody(
                        CreateAllowanceRequest(
                            id = newId(),
                            branchDayId = day.branchDayId,
                            userId = userId,
                            amount = amount,
                            reason = reason,
                        ),
                    )
                }
            },
            transform = {
                val created = it.body<AllowanceResponse>()
                if (generation == editDataGeneration) {
                    val current = _editAllowances.value
                    if (current is UiState.Success) {
                        _editAllowances.value = UiState.Success(current.data + created)
                    } else {
                        reloadSection(EditSection.ALLOWANCES)
                    }
                    actionTracker.finish(key)
                }
                Unit
            },
            onNonSuccess = { response ->
                if (generation == editDataGeneration) {
                    failActionOrSilent403(key, "allow:create", response)
                }
            },
            onError = { e ->
                if (generation == editDataGeneration) {
                    actionTracker.fail(key, "allow:create failed: ${e.message ?: "network error"}")
                }
            },
        )
    }

    // ─────────────────────────── exports (D6) ───────────────────────────

    data class DownloadPayload(
        val fileName: String,
        val bytes: ByteArray,
    )

    /** Export keys: `mode:<branchId>:<mode>:<format>`, `day:<branchDayId>:<format>`, `public:<kind>:<format>`. */
    private val _downloads = MutableStateFlow<Map<String, UiState<DownloadPayload>>>(emptyMap())
    val downloads: StateFlow<Map<String, UiState<DownloadPayload>>> = _downloads.asStateFlow()

    private val _exportErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val exportErrors: StateFlow<Map<String, String>> = _exportErrors.asStateFlow()

    fun exportMode(
        key: String,
        url: String,
    ) {
        if (_downloads.value.containsKey(key) && _downloads.value[key] is UiState.Loading) return
        _exportErrors.value = _exportErrors.value - key
        _downloads.value = _downloads.value + (key to UiState.Loading)
        handler.launchStateless(
            operation = "export:$key",
            endpoint = "GET export $key",
            block = { apiClient.httpClient.get(url) },
            transform = {
                _downloads.value =
                    _downloads.value +
                    (key to UiState.Success(DownloadPayload(fileName = fileNameOf(it), bytes = it.readRawBytes())))
                Unit
            },
            onNonSuccess = { response ->
                _downloads.value = _downloads.value - key
                _exportErrors.value = _exportErrors.value + (key to "Export failed: ${response.status.value}")
            },
            onError = { e ->
                _downloads.value = _downloads.value - key
                _exportErrors.value = _exportErrors.value + (key to "Export failed: ${e.message ?: "network error"}")
            },
        )
    }

    fun modeExportUrl(
        mode: ReportMode,
        branchId: String,
        format: String,
    ): String =
        when (mode) {
            // DAILY deliberately absent: no toolbar export (D4 — per-day only, in the detail).
            ReportMode.DAILY -> {
                ""
            }

            ReportMode.MONTHLY -> {
                val month =
                    com.companyb.companyapp.ui.screen
                        .parseYearMonthInput(_monthInput.value) ?: defaultMonth
                "/api/branches/$branchId/export/monthly?year=${month.year}&month=${month.month.ordinal + 1}&format=$format"
            }

            ReportMode.ALL_TIME -> {
                "/api/branches/$branchId/export/all-time?format=$format"
            }

            ReportMode.DATE_RANGE -> {
                val range = _appliedRange.value
                if (range != null) {
                    "/api/branches/$branchId/export/range?from=${range.first}&to=${range.second}&format=$format"
                } else {
                    ""
                }
            }
        }

    fun exportDay(
        day: DailySalesSummaryResponse,
        branchId: String,
        format: String,
    ) {
        // Keyed on branchDayId (pass-4/5 HARD): the date alone collides across branches — an
        // in-flight branch-A export would block + mislabel branch B's same-date row. The
        // screen's ExportButtons look up the SAME key (both sides must agree).
        exportMode(
            key = "day:${day.branchDayId}:$format",
            url = "/api/branches/$branchId/export/daily?date=${day.date}&format=$format",
        )
    }

    fun exportPublic(
        kind: String,
        format: String,
    ) {
        exportMode(
            key = "public:$kind:$format",
            url = "/api/branches/export/$kind?format=$format",
        )
    }

    /** #105 D4 — the toolbar's mode export (Daily has none — per-day only, in the detail). */
    fun exportModeCurrent(format: String) {
        val branchId = _selectedBranchId.value ?: return
        val url = modeExportUrl(_mode.value, branchId, format)
        if (url.isEmpty()) return
        // Keyed on branchId (pass-9 SOFT): a late landing from a superseded branch must not
        // block/mislabel the current branch's export.
        exportMode(
            key = "mode:$branchId:${_mode.value.name}:$format",
            url = url,
        )
    }

    fun consumeDownload(key: String) {
        _downloads.value = _downloads.value - key
    }

    private fun fileNameOf(response: HttpResponse): String {
        val disposition = response.headers[HttpHeaders.ContentDisposition]
        val quoted =
            disposition
                ?.substringAfter("filename=\"", missingDelimiterValue = "")
                ?.substringBefore('"')
                ?.takeIf { it.isNotBlank() }
        return quoted ?: "companyapp-export.${extensionOf(response)}"
    }

    private fun extensionOf(response: HttpResponse): String {
        val type = response.contentType()
        return when (type?.withoutParameters()?.toString()) {
            "application/pdf" -> "pdf"
            "text/csv" -> "csv"
            else -> "bin"
        }
    }

    // ─────────────────────────── helpers ───────────────────────────

    /**
     * ADR-0022 / #113 D4 — 403s exit silently (the capability surface is code-only; the backend
     * is authoritative and a silent exit must not flash an error the user can't act on).
     * 409 = conflict → message + section reload; other statuses → generic inline error.
     */
    private fun failActionOrSilent403(
        key: String,
        operation: String,
        response: HttpResponse,
        conflictMessage: String? = null,
        reload: (() -> Unit)? = null,
    ) {
        if (response.status == HttpStatusCode.Forbidden) {
            actionTracker.finish(key)
            return
        }
        val message =
            when {
                response.status == HttpStatusCode.Conflict && conflictMessage != null -> conflictMessage
                else -> "$operation failed: ${response.status.value}"
            }
        actionTracker.fail(key, message)
        if (response.status == HttpStatusCode.Conflict) {
            _conflicts.value = _conflicts.value + key
            reload?.invoke()
        }
    }

    private fun newId(): String =
        kotlin.uuid.Uuid
            .random()
            .toString()

    private enum class FetchMode(
        val operationName: String,
    ) {
        Cold("loadFeed"),
        Refresh("refreshFeed"),
        LoadMore("loadMore"),
    }

    companion object {
        internal const val FEED_PAGE_SIZE = 20
    }
}

enum class EditSection {
    EXPENSES,
    COMPENSATIONS,
    ALLOWANCES,
}
