package com.companyb.companyapp.finance
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.async.ActionTracker
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.StatelessHooks
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branchday.BranchDayUserResponse
import com.companyb.companyapp.contracts.finance.AllowanceResponse
import com.companyb.companyapp.contracts.finance.CompensationResponse
import com.companyb.companyapp.contracts.finance.CreateAllowanceRequest
import com.companyb.companyapp.contracts.finance.CreateCompensationRequest
import com.companyb.companyapp.contracts.finance.CreateExpenseRequest
import com.companyb.companyapp.contracts.finance.DeleteExpenseRequest
import com.companyb.companyapp.contracts.finance.ExpenseResponse
import com.companyb.companyapp.contracts.finance.RestoreExpenseRequest
import com.companyb.companyapp.contracts.finance.UpdateCompensationRequest
import com.companyb.companyapp.contracts.finance.UpdateExpenseRequest
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import com.companyb.companyapp.contracts.reporting.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.network.ApiClient
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
 *
 * #560 — finance owns report-query/feed, day-editor and export transitions here as
 * private members — the #479 extension splits are folded back per #535 (a cohesive
 * owner, not a function-count bundle). Screens observe the read-only [StateFlow]s
 * and invoke the public commands; no composable touches mutable internals.
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

    private val selectedBranchIdState = MutableStateFlow<String?>(null)
    val selectedBranchId: StateFlow<String?> = selectedBranchIdState.asStateFlow()

    fun loadBranches() {
        if (_branches.value is UiState.Loading) return
        handler.launch(
            state = _branches,
            operation = "loadBranches",
            endpoint = "GET /api/branches/accessible",
            block = { apiClient.httpClient.get(ApiRoutes.BRANCHES_ACCESSIBLE) },
            transform = {
                val list = it.body<List<BranchResponse>>()
                _branches.value = UiState.Success(list)
                if (selectedBranchIdState.value == null) {
                    // Default: the clocked-in branch when the user can see it, else the first
                    // accessible branch (Accountant: GLOBAL view, no selected branch — D3).
                    val default =
                        list.firstOrNull { b ->
                            b.id ==
                                AppSessionState.snapshot.value.clock
                                    ?.branchId
                        }
                            ?: list.firstOrNull()
                    if (default != null) {
                        selectedBranchIdState.value = default.id
                        refreshWindowAndFeed()
                    }
                }
                list
            },
        )
    }

    fun selectBranch(branchId: String) {
        if (branchId == selectedBranchIdState.value) return
        selectedBranchIdState.value = branchId
        selectedDayState.value = null
        editModeState.value = false
        paramErrorState.value = null
        clearEditData()
        refreshWindowAndFeed()
        // #105 D4 — the MONTHLY rollup card is pinned; a branch switch must refetch it (and a
        // superseded in-flight rollup must stay inert — rollupGeneration bump in loadMonthlyRollup).
        monthlyRollupState.value = UiState.Idle
        if (modeState.value == ReportMode.MONTHLY) {
            loadMonthlyRollup()
        }
    }

    // ─────────────────────────── mode + params ───────────────────────────

    private val modeState = MutableStateFlow(ReportMode.DAILY)
    val mode: StateFlow<ReportMode> = modeState.asStateFlow()

    /** Editable `yyyy-MM` text for the MONTHLY mode and the ALL_TIME calendar jump. */
    private val monthInputState = MutableStateFlow(defaultMonth.toString())
    val monthInput: StateFlow<String> = monthInputState.asStateFlow()

    /** Applied MONTHLY month (validated at apply time — #485: feed/rollup/export read this, never the draft). */
    private val appliedMonthState = MutableStateFlow(defaultMonth)
    val appliedMonth: StateFlow<YearMonth> = appliedMonthState.asStateFlow()

    private val rangeFromInputState = MutableStateFlow("")
    val rangeFromInput: StateFlow<String> = rangeFromInputState.asStateFlow()

    private val rangeToInputState = MutableStateFlow("")
    val rangeToInput: StateFlow<String> = rangeToInputState.asStateFlow()

    /** Applied date-range window (validated at apply time). */
    private val appliedRangeState = MutableStateFlow<Pair<String, String>?>(null)
    val appliedRange: StateFlow<Pair<String, String>?> = appliedRangeState.asStateFlow()

    /** Applied ALL_TIME jump month (null = unbounded all-time). */
    private val jumpMonthState = MutableStateFlow<YearMonth?>(null)

    /** Mode-parameter validation errors (rendered inline next to the field). */
    private val paramErrorState = MutableStateFlow<String?>(null)
    val paramError: StateFlow<String?> = paramErrorState.asStateFlow()

    private val monthlyRollupState = MutableStateFlow<UiState<MonthlyRemittanceSummaryResponse?>>(UiState.Idle)
    val monthlyRollup: StateFlow<UiState<MonthlyRemittanceSummaryResponse?>> = monthlyRollupState.asStateFlow()

    /** Rollup responses from a superseded branch/mode are inert (P4 pass-1 HARD). */
    private var rollupGeneration = 0

    private val feedEntriesState = MutableStateFlow<UiState<List<DailySalesSummaryResponse>>>(UiState.Idle)
    val feedEntries: StateFlow<UiState<List<DailySalesSummaryResponse>>> = feedEntriesState.asStateFlow()

    private val nextCursorState = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = nextCursorState.asStateFlow()

    private val isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = isLoadingMoreState.asStateFlow()

    private val isRefreshingState = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = isRefreshingState.asStateFlow()

    private val loadMoreErrorState = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = loadMoreErrorState.asStateFlow()

    private val refreshErrorState = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = refreshErrorState.asStateFlow()

    // Every window/mode/branch change bumps the generation; an in-flight page from a superseded
    // generation is inert (no list/cursor/error writes) — the AuditLog #144 shape.
    private var feedGeneration = 0

    private val selectedDayState = MutableStateFlow<DailySalesSummaryResponse?>(null)
    val selectedDay: StateFlow<DailySalesSummaryResponse?> = selectedDayState.asStateFlow()

    private val editModeState = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = editModeState.asStateFlow()

    private val reliefDayState = MutableStateFlow<UiState<DailySalesSummaryResponse>>(UiState.Idle)
    val reliefDay: StateFlow<UiState<DailySalesSummaryResponse>> = reliefDayState.asStateFlow()

    /** Generation guard for [loadReliefDay] — a superseded relief fetch stays inert (the #143 class). */
    private var reliefGeneration = 0

    /**
     * #158 — the relief day-scoped read entry: a BRANCH_DAY grant holder (no VIEW_BRANCH_DATA)
     * fetches a single day by date via the day-scoped summary read. The branch is the
     * clocked-in branch (the relief branch — the picker lists only BRANCH-granted branches,
     * which a relief delegate has none of). Success seeds [selectedDay] so the day detail +
     * editor flows work off the same state.
     */
    private val editExpensesState = MutableStateFlow<UiState<List<ExpenseResponse>>>(UiState.Idle)
    val editExpenses: StateFlow<UiState<List<ExpenseResponse>>> = editExpensesState.asStateFlow()

    private val editCompensationsState = MutableStateFlow<UiState<List<CompensationResponse>>>(UiState.Idle)
    val editCompensations: StateFlow<UiState<List<CompensationResponse>>> = editCompensationsState.asStateFlow()

    private val editAllowancesState = MutableStateFlow<UiState<List<AllowanceResponse>>>(UiState.Idle)
    val editAllowances: StateFlow<UiState<List<AllowanceResponse>>> = editAllowancesState.asStateFlow()

    private val editUsersState = MutableStateFlow<UiState<List<BranchDayUserResponse>>>(UiState.Idle)
    val editUsers: StateFlow<UiState<List<BranchDayUserResponse>>> = editUsersState.asStateFlow()

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
    private val conflictsState = MutableStateFlow<Set<String>>(emptySet())
    val conflicts: StateFlow<Set<String>> = conflictsState.asStateFlow()

    fun consumeConflict(key: String) {
        conflictsState.value = conflictsState.value - key
    }

    val inFlightActions: StateFlow<Set<String>> = actionTracker.inFlight

    private var editDataGeneration = 0

    data class DownloadPayload(
        val fileName: String,
        val bytes: ByteArray,
    )

    /** Export keys: `mode:<branchId>:<mode>:<format>`, `day:<branchDayId>:<format>`, `public:<kind>:<format>`. */
    private val downloadsState = MutableStateFlow<Map<String, UiState<DownloadPayload>>>(emptyMap())
    val downloads: StateFlow<Map<String, UiState<DownloadPayload>>> = downloadsState.asStateFlow()

    private val exportErrorsState = MutableStateFlow<Map<String, String>>(emptyMap())
    val exportErrors: StateFlow<Map<String, String>> = exportErrorsState.asStateFlow()

    // ─────────── report params (#560, from FinanceReportParamsOps) ───────────
    fun setMode(mode: ReportMode) {
        if (mode == modeState.value) return
        modeState.value = mode
        paramErrorState.value = null
        refreshWindowAndFeed()
        if (mode == ReportMode.MONTHLY) {
            loadMonthlyRollup()
        } else {
            monthlyRollupState.value = UiState.Idle
        }
    }

    fun setMonthInput(raw: String) {
        monthInputState.value = raw
    }

    fun applyMonth() {
        val month =
            parseYearMonthInput(monthInputState.value)
        if (month == null) {
            paramErrorState.value = "Month must be yyyy-MM"
            return
        }
        paramErrorState.value = null
        appliedMonthState.value = month
        monthlyRollupState.value = UiState.Idle
        refreshWindowAndFeed()
        loadMonthlyRollup()
    }

    fun clearRange() {
        appliedRangeState.value = null
        paramErrorState.value = null
        refreshWindowAndFeed()
    }

    fun setRangeInputs(
        from: String,
        to: String,
    ) {
        rangeFromInputState.value = from
        rangeToInputState.value = to
    }

    fun applyRange() {
        val from =
            parseDateInput(rangeFromInputState.value)
        val to =
            parseDateInput(rangeToInputState.value)
        when {
            from == null -> {
                paramErrorState.value = "From must be yyyy-MM-dd"
            }

            to == null -> {
                paramErrorState.value = "To must be yyyy-MM-dd"
            }

            from > to -> {
                paramErrorState.value = "From must be on or before To"
            }

            else -> {
                paramErrorState.value = null
                appliedRangeState.value = from.toString() to to.toString()
                refreshWindowAndFeed()
            }
        }
    }

    fun setJumpInput(raw: String) {
        monthInputState.value = raw
    }

    fun applyJump() {
        val month =
            parseYearMonthInput(monthInputState.value)
        if (month == null) {
            paramErrorState.value = "Month must be yyyy-MM"
            return
        }
        paramErrorState.value = null
        jumpMonthState.value = month
        refreshWindowAndFeed()
    }

    fun clearJump() {
        jumpMonthState.value = null
        monthInputState.value = defaultMonth.toString()
        paramErrorState.value = null
        refreshWindowAndFeed()
    }

    // ─────────────────────────── feed ───────────────────────────

    // ─────────── feed + rollup (#560, from FinanceReportFeedOps) ───────────
    fun refreshFeed() {
        if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) return
        val branchId = selectedBranchIdState.value ?: return
        if (isRefreshingState.value || isLoadingMoreState.value) return
        // A refresh replaces the day rows — a stale selection would keep showing the
        // pre-refresh figures (pass-9 SOFT).
        selectedDayState.value = null
        editModeState.value = false
        refreshErrorState.value = null
        loadMoreErrorState.value = null
        fetchPage(FeedFetchMode.Refresh, cursor = null, branchId = branchId)
    }

    fun loadMore() {
        val branchId = selectedBranchIdState.value ?: return
        val cursor = nextCursorState.value ?: return
        if (isLoadingMoreState.value || isRefreshingState.value) return
        loadMoreErrorState.value = null
        fetchPage(FeedFetchMode.LoadMore, cursor = cursor, branchId = branchId)
    }

    fun retryFeed() {
        if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) return
        val branchId = selectedBranchIdState.value ?: return
        feedEntriesState.value = UiState.Loading
        fetchPage(FeedFetchMode.Cold, cursor = null, branchId = branchId)
    }

    private fun refreshWindowAndFeed() {
        feedGeneration++
        isRefreshingState.value = false
        isLoadingMoreState.value = false
        nextCursorState.value = null
        refreshErrorState.value = null
        loadMoreErrorState.value = null
        selectedDayState.value = null
        editModeState.value = false
        // Pass-3 HARD — a mode/window switch while editing must not leave the OLD day's
        // section data armed: re-entering edit on another day would render the old rows
        // during the load window and Edit/Delete would mutate the wrong day's records.
        clearEditData()
        val branchId = selectedBranchIdState.value ?: return
        // Pass-5 SOFT — DATE_RANGE has no window before Apply: no unbounded all-time fetch
        // fires behind the hint (the hint branch renders instead of the feed).
        if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) {
            feedEntriesState.value = UiState.Idle
            return
        }
        feedEntriesState.value = UiState.Loading
        fetchPage(FeedFetchMode.Cold, cursor = null, branchId = branchId)
    }

    private fun currentWindow(): FeedWindow =
        feedWindowFor(
            FeedWindowRequest(
                mode = modeState.value,
                today = today,
                month = appliedMonthState.value,
                rangeFrom = appliedRangeState.value?.first,
                rangeTo = appliedRangeState.value?.second,
                jumpMonth = jumpMonthState.value,
            ),
        )

    private fun fetchPage(
        mode: FeedFetchMode,
        cursor: String?,
        branchId: String,
    ) {
        val generation = feedGeneration
        if (mode == FeedFetchMode.Refresh) isRefreshingState.value = true
        if (mode == FeedFetchMode.LoadMore) isLoadingMoreState.value = true
        handler.launchStatelessGuarded(
            operation = mode.operationName,
            endpoint = "GET /api/branches/$branchId/daily-summaries",
            block = {
                apiClient.httpClient.get(ApiRoutes.branchDailySummaries(branchId)) {
                    cursor?.let { parameter("cursor", it) }
                    parameter("limit", FEED_PAGE_SIZE)
                    currentWindow().from?.let { parameter("from", it) }
                    currentWindow().to?.let { parameter("to", it) }
                }
            },
            guarded =
                GuardedStateless(
                    // #528 — suspend decode owns the parse; the non-suspending commit below owns
                    // every list/cursor/flag write. A generation bump mid-decode drops the body.
                    decode = { it.body<DailySalesSummaryBrowseResponse>() },
                    commit = { page ->
                        val listAlreadyCommitted =
                            mode == FeedFetchMode.Cold && feedEntriesState.value is UiState.Success
                        if (listAlreadyCommitted) {
                            logWarn("FinanceVM", "cold feed success suppressed — feed superseded")
                        } else {
                            refreshErrorState.value = null
                            nextCursorState.value = page.nextCursor
                            applyPage(mode, page.entries)
                        }
                        finish(mode)
                    },
                    // #173 — the hand-rolled generation guard folds into the guarded stale gate: a
                    // superseded landing (window/branch/mode switched while this page was in flight)
                    // is inert — no list/cursor/error/flags writes, no finish.
                    stale = { generation != feedGeneration },
                    onNonSuccess = { response ->
                        handlePageFailure(mode, "feed failed: ${response.status.value}")
                        finish(mode)
                    },
                    onError = { e ->
                        // Network or deserialization failure — same error surface + flag cleanup so the
                        // list state and buttons never freeze (keep-last-list); gated by stale — a
                        // superseded fetch's failure must not surface on the new list. onError is that
                        // single surface (#169).
                        handlePageFailure(mode, "feed failed: ${e.message ?: "network error"}")
                        finish(mode)
                    },
                ),
        )
    }

    private fun applyPage(
        mode: FeedFetchMode,
        entries: List<DailySalesSummaryResponse>,
    ) {
        when (mode) {
            FeedFetchMode.Cold, FeedFetchMode.Refresh -> {
                feedEntriesState.value = UiState.Success(entries)
            }

            FeedFetchMode.LoadMore -> {
                val current =
                    (feedEntriesState.value as? UiState.Success<List<DailySalesSummaryResponse>>)
                        ?.data
                        .orEmpty()
                feedEntriesState.value = UiState.Success(current + entries)
            }
        }
    }

    private fun handlePageFailure(
        mode: FeedFetchMode,
        message: String,
    ) {
        when (mode) {
            FeedFetchMode.Cold -> {
                if (feedEntriesState.value !is UiState.Success) {
                    feedEntriesState.value = UiState.Error(message)
                } else {
                    logWarn("FinanceVM", "cold feed failure suppressed — feed superseded: $message")
                }
            }

            FeedFetchMode.Refresh -> {
                refreshErrorState.value = message
            }

            FeedFetchMode.LoadMore -> {
                loadMoreErrorState.value = message
            }
        }
    }

    private fun finish(mode: FeedFetchMode) {
        if (mode == FeedFetchMode.Refresh) isRefreshingState.value = false
        if (mode == FeedFetchMode.LoadMore) isLoadingMoreState.value = false
    }

    // ─────────────────────────── monthly rollup ───────────────────────────

    fun loadMonthlyRollup() {
        val branchId = selectedBranchIdState.value ?: return
        val month = appliedMonthState.value
        rollupGeneration++
        val generation = rollupGeneration
        monthlyRollupState.value = UiState.Loading
        handler.launchStatelessGuarded(
            operation = "loadMonthlyRollup",
            endpoint = "GET /api/branches/$branchId/monthly-summary",
            block = {
                apiClient.httpClient.get(ApiRoutes.branchMonthlySummary(branchId)) {
                    parameter("year", month.year)
                    parameter("month", month.month.ordinal + 1)
                }
            },
            guarded =
                GuardedStateless(
                    // #528 — decode/commit split: a branch/month switch mid-decode drops the body.
                    decode = { it.body<MonthlyRemittanceSummaryResponse>() },
                    commit = {
                        // 404 (no remittance submitted that month — the #105 F5 shape) is NOT an
                        // error: the rollup card simply doesn't render. Transform only sees 2xx —
                        // the 404 branch lives in onNonSuccess below.
                        monthlyRollupState.value = UiState.Success(it)
                    },
                    // #173 — the generation guard folds into the stale gate (a superseded rollup —
                    // branch/month switched — must not write any state; a DAILY-mode switch doesn't
                    // bump the generation — the pre-existing #170 accepted SOFT, benign while DAILY).
                    stale = { generation != rollupGeneration },
                    onNonSuccess = { response ->
                        if (response.status == HttpStatusCode.NotFound) {
                            monthlyRollupState.value = UiState.Success(null)
                        } else {
                            monthlyRollupState.value = UiState.Error("monthly rollup failed: ${response.status.value}")
                        }
                    },
                    // #170 — the loadReliefDay shape: a transport failure moves Loading → Error
                    // (terminal) instead of parking on Loading forever (the #168/#169 P5 sibling —
                    // pre-fix the sole onError-less stateless site parking a UiState on Loading;
                    // loadSent is onError-less by keep-last design — no state to park).
                    onError = { e ->
                        monthlyRollupState.value = UiState.Error(e.message ?: "Unknown error")
                    },
                ),
        )
    }

    // ─────────────────────────── selection + edit mode ───────────────────────────

    // ─────────── day selection + relief (#560, from FinanceReportDayOps) ───────────
    fun selectDay(day: DailySalesSummaryResponse?) {
        selectedDayState.value = day
    }

    // ─────────────────────────── relief day entry (#158) ───────────────────────────

    fun loadReliefDay(date: String) {
        val branchId =
            AppSessionState.snapshot.value.clock
                ?.branchId
                ?: run {
                    reliefDayState.value = UiState.Error("No clocked-in branch")
                    return
                }
        // Pass-1 HARD (P3/P4) — the #144-ack/browse stale-state class: a relief-day switch
        // while editing must not leave the OLD day's edit sections armed (editExpenses etc.
        // hold the previous day's rows; a later Edit toggle would mutate the wrong day).
        // Mirrors refreshWindowAndFeed's reset + loadSection's generation guard. The state
        // write is manual (the state-less #168 launch writes no Loading/Success) so a
        // superseded response never lands Success on the UI. The VM's [selectedBranchIdState] is
        // deliberately NOT touched:
        // the relief surface reads the clocked-in branch from SessionState (pass-2 HARD —
        // writing it would pin the hybrid's reports surface to the relief branch).
        reliefGeneration++
        val generation = reliefGeneration
        editModeState.value = false
        selectedDayState.value = null
        clearEditData()
        reliefDayState.value = UiState.Loading
        handler.launchStatelessGuarded(
            operation = "loadReliefDay",
            endpoint = "GET /api/branches/$branchId/daily-summary?date=$date",
            block = { apiClient.httpClient.get(ApiRoutes.branchDailySummaryWithDate(branchId, date)) },
            guarded =
                GuardedStateless(
                    // #528 — decode/commit split: a relief-date switch mid-decode drops the body.
                    decode = { it.body<DailySalesSummaryResponse>() },
                    commit = { day ->
                        reliefDayState.value = UiState.Success(day)
                        selectDay(day)
                    },
                    // #173 — the generation guard folds into the stale gate (a superseded relief
                    // fetch — clearReliefState or a new date — must not write [reliefDay]/[selectedDay]).
                    stale = { generation != reliefGeneration },
                    onNonSuccess = { response ->
                        reliefDayState.value = UiState.Error("loadReliefDay failed: ${response.status.value}")
                    },
                    onError = { e ->
                        reliefDayState.value = UiState.Error(e.message ?: "Unknown error")
                    },
                ),
        )
    }

    /**
     * #158 pass-2 — the hybrid exit path: leaving the relief section clears its state so a
     * re-entry via the chip starts clean (no stale day under a fresh date input).
     */
    fun clearReliefState() {
        reliefGeneration++
        reliefDayState.value = UiState.Idle
        editModeState.value = false
        selectedDayState.value = null
        clearEditData()
    }

    /**
     * #105 D1 — Edit-toggle visibility, #156 branch-scoped: the checks resolve against the
     * VIEWED branch ([selectedBranchIdState] — the branch the day data belongs to, switchable via
     * the picker; defaults to the clocked-in branch). The backend resolves the same branch
     * from the day row (`CapabilityFilter.requireBranchCapability`), so the toggle, the
     * section loads and the backend 403s all agree. A null viewed branch fails closed.
     */
    private fun hasAssignCapability(): Boolean =
        AppSessionState.snapshot.value.capabilities.hasCapability(
            CapabilityCodes.ASSIGN_COMPENSATION,
            CapabilityContextType.BRANCH,
            selectedBranchIdState.value,
        )

    /**
     * #105 D1/#156 — the EDIT_BRANCH_DATA leg of the edit-toggle check, now with the
     * #158 day leg: the BRANCH triple at the viewed branch OR a BRANCH_DAY relief grant
     * for the VIEWED day ([selectedDayState] — the day row the backend gates via; a relief
     * delegate edits their granted day without any BRANCH grant). A null day fails the
     * day leg closed.
     */
    fun hasEditBranchDataCapability(): Boolean =
        AppSessionState.snapshot.value.capabilities.hasBranchOrDayCapability(
            CapabilityCodes.EDIT_BRANCH_DATA,
            selectedBranchIdState.value,
            selectedDayState.value?.branchDayId,
        )

    fun hasEditCapabilities(): Boolean {
        val caps = AppSessionState.snapshot.value.capabilities
        val branchId = selectedBranchIdState.value
        // #158 — the EDIT_BRANCH_DATA leg includes the day-scoped relief grant; the
        // ASSIGN_COMPENSATION + EDIT_PAST_DAY legs stay BRANCH-only (not relief-eligible,
        // per the #157 surface).
        return hasEditBranchDataCapability() ||
            caps.hasCapability(CapabilityCodes.ASSIGN_COMPENSATION, CapabilityContextType.BRANCH, branchId) ||
            caps.hasCapability(CapabilityCodes.EDIT_PAST_DAY, CapabilityContextType.BRANCH, branchId)
    }

    fun setEditMode(on: Boolean) {
        if (on == editModeState.value) return
        editModeState.value = on
        if (on) {
            loadEditData()
        } else {
            clearEditData()
        }
    }

    // ─────────────────────────── edit data ───────────────────────────

    // ─────────── day-editor sections (#560, from FinanceReportDayEditOps) ───────────
    private fun loadEditData() {
        val day = selectedDayState.value ?: return
        val branchDayId = day.branchDayId
        editDataGeneration++
        val generation = editDataGeneration
        if (hasEditBranchDataCapability()) {
            loadSection(
                generation,
                editExpensesState,
                "expenses",
                ApiRoutes.EXPENSES,
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("expense:"),
            )
        }
        if (hasAssignCapability()) {
            loadSection(
                generation,
                editCompensationsState,
                "compensations",
                ApiRoutes.COMPENSATIONS,
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("comp:"),
            )
            loadSection(
                generation,
                editAllowancesState,
                "allowances",
                ApiRoutes.ALLOWANCES,
                params = listOf("branchDayId" to branchDayId),
                errorKeyPrefixes = listOf("allow:"),
            )
            loadSection(
                generation,
                editUsersState,
                "branch-day users",
                ApiRoutes.branchDayUsers(branchDayId),
                params = emptyList(),
            )
        }
    }

    fun reloadSection(section: EditSection) {
        val day = selectedDayState.value ?: return
        val generation = editDataGeneration
        when (section) {
            EditSection.EXPENSES -> {
                loadSection(
                    generation,
                    editExpensesState,
                    "expenses",
                    ApiRoutes.EXPENSES,
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("expense:"),
                )
            }

            EditSection.COMPENSATIONS -> {
                loadSection(
                    generation,
                    editCompensationsState,
                    "compensations",
                    ApiRoutes.COMPENSATIONS,
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("comp:"),
                )
            }

            EditSection.ALLOWANCES -> {
                loadSection(
                    generation,
                    editAllowancesState,
                    "allowances",
                    ApiRoutes.ALLOWANCES,
                    params = listOf("branchDayId" to day.branchDayId),
                    errorKeyPrefixes = listOf("allow:"),
                )
            }
        }
    }

    // #596: 6-param section-load helper stays whole per #535; the endpoint/operation/params/error-prefix
    // bundle is load-specific plumbing, not a real ownership decision.
    @Suppress("LongParameterList") // #596
    private inline fun <reified T> loadSection(
        generation: Int,
        state: MutableStateFlow<UiState<List<T>>>,
        operation: String,
        endpoint: String,
        params: List<Pair<String, String>>,
        errorKeyPrefixes: List<String> = emptyList(),
    ) {
        handler.launchStatelessGuarded(
            operation = operation,
            endpoint = "GET $endpoint",
            block = {
                apiClient.httpClient.get(endpoint) {
                    params.forEach { (k, v) -> parameter(k, v) }
                }
            },
            guarded =
                GuardedStateless(
                    // #528 — decode/commit split: a day/branch switch mid-decode must not
                    // repopulate the cleared sections.
                    decode = { it.body<List<T>>() },
                    commit = { list ->
                        state.value = UiState.Success(list)
                        // #143 class — a fresh list supersedes the section's stale action
                        // errors (e.g. a 409-reload landing beside its own error line).
                        if (errorKeyPrefixes.isNotEmpty()) {
                            actionTracker.clearWhere { key -> errorKeyPrefixes.any { key.startsWith(it) } }
                        }
                    },
                    // #173 — the generation guard folds into the stale gate (a superseded day/branch
                    // section load — clearEditData during flight — must not repopulate cleared state).
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        state.value = UiState.Error("$operation failed: ${response.status.value}")
                    },
                    onError = { e ->
                        // Transport or deserialization failure: route to the section's error state —
                        // gated by the stale flag so a superseded day/branch load can't error the
                        // cleared sections. onError is that single surface (#169).
                        state.value = UiState.Error("$operation failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    private fun clearEditData() {
        // P4 pass-1: bumping the generation makes in-flight section loads from a superseded
        // day/branch inert — they must not repopulate the cleared sections.
        editDataGeneration++
        editExpensesState.value = UiState.Idle
        editCompensationsState.value = UiState.Idle
        editAllowancesState.value = UiState.Idle
        editUsersState.value = UiState.Idle
        actionTracker.clear()
        conflictsState.value = emptySet()
    }

    // ─────────────────────────── expense actions ───────────────────────────

    fun createCompensation(
        userId: String,
        amount: String,
        note: String?,
        reason: String?,
    ) {
        val day = selectedDayState.value ?: return
        val key = "comp:create"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "createCompensation",
            endpoint = "POST /api/compensation",
            block = {
                apiClient.httpClient.post(ApiRoutes.COMPENSATION) {
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
            guarded =
                GuardedStateless(
                    decode = { it.body<CompensationResponse>() },
                    commit = { created ->
                        val current = editCompensationsState.value
                        if (current is UiState.Success) {
                            editCompensationsState.value = UiState.Success(current.data + created)
                        } else {
                            reloadSection(EditSection.COMPENSATIONS)
                        }
                        actionTracker.finish(key)
                    },
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        // #101 D4 — 409 duplicate (one per user per paying day) → inline error on the
                        // picker; the row is already compensated (the list shows it).
                        failActionOrSilent403(
                            key,
                            "comp:create",
                            response,
                            conflictMessage = "Already compensated on this day",
                        )
                    },
                    onError = { e ->
                        actionTracker.fail(key, "comp:create failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    fun updateCompensation(
        compensation: CompensationResponse,
        amount: String,
        note: String?,
        reason: String?,
    ) {
        val key = "comp:update:${compensation.id}"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "updateCompensation",
            endpoint = "PATCH /api/compensation/${compensation.id}",
            block = {
                apiClient.httpClient.patch(ApiRoutes.compensation(compensation.id)) {
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
            guarded =
                GuardedStateless(
                    decode = { it.body<CompensationResponse>() },
                    commit = { updated ->
                        editCompensationsState.value =
                            UiState.Success(
                                (editCompensationsState.value as? UiState.Success<List<CompensationResponse>>)
                                    ?.data
                                    .orEmpty()
                                    .map { row -> if (row.id == updated.id) updated else row },
                            )
                        actionTracker.finish(key)
                    },
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(
                            key,
                            "comp:update",
                            response,
                            conflictMessage = "Compensation changed elsewhere — reloaded",
                        ) {
                            reloadSection(EditSection.COMPENSATIONS)
                        }
                    },
                    onError = { e ->
                        actionTracker.fail(key, "comp:update failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    // ─────────────────────────── allowance actions ───────────────────────────

    fun createAllowance(
        userId: String,
        amount: String,
        reason: String?,
    ) {
        val day = selectedDayState.value ?: return
        val key = "allow:create"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "createAllowance",
            endpoint = "POST /api/allowances",
            block = {
                apiClient.httpClient.post(ApiRoutes.ALLOWANCES) {
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
            guarded =
                GuardedStateless(
                    decode = { it.body<AllowanceResponse>() },
                    commit = { created ->
                        val current = editAllowancesState.value
                        if (current is UiState.Success) {
                            editAllowancesState.value = UiState.Success(current.data + created)
                        } else {
                            reloadSection(EditSection.ALLOWANCES)
                        }
                        actionTracker.finish(key)
                    },
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(key, "allow:create", response)
                    },
                    onError = { e ->
                        actionTracker.fail(key, "allow:create failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    // ─────────── expense actions (#560, from FinanceReportExpenseOps) ───────────
    fun createExpense(
        amount: String,
        categoryCode: String,
        notes: String?,
        reason: String?,
    ) {
        val day = selectedDayState.value ?: return
        val key = "expense:create"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "createExpense",
            endpoint = "POST /api/expenses",
            block = {
                apiClient.httpClient.post(ApiRoutes.EXPENSES) {
                    setBody(
                        CreateExpenseRequest(
                            id = newId(),
                            branchDayId = day.branchDayId,
                            amount = amount,
                            category =
                                com.companyb.companyapp.contracts.finance.ExpenseCategory
                                    .valueOf(categoryCode),
                            notes = notes,
                            reason = reason,
                        ),
                    )
                }
            },
            guarded =
                GuardedStateless(
                    decode = { it.body<ExpenseResponse>() },
                    commit = { created ->
                        val current = editExpensesState.value
                        if (current is UiState.Success) {
                            editExpensesState.value = UiState.Success(current.data + created)
                        } else {
                            // Pass-8 SOFT — appending onto an Error section would truncate the
                            // list to the new row; reload the section instead.
                            reloadSection(EditSection.EXPENSES)
                        }
                        actionTracker.finish(key)
                    },
                    // #173 — a superseded action (branch/day switched mid-flight) is inert: no row
                    // write onto the new day's sections, no tracker terminal (clearEditData cleared it).
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(key, "expense:create", response)
                    },
                    onError = { e ->
                        // Pass-9 HARD — a transport/timeout failure must keep the dialog open with an
                        // inline error (the close-on-success effect keys on the ABSENCE of an error).
                        actionTracker.fail(key, "expense:create failed: ${e.message ?: "network error"}")
                    },
                ),
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
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "updateExpense",
            endpoint = "PATCH /api/expenses/${expense.id}",
            block = {
                apiClient.httpClient.patch(ApiRoutes.expense(expense.id)) {
                    setBody(
                        UpdateExpenseRequest(
                            amount = amount,
                            category =
                                com.companyb.companyapp.contracts.finance.ExpenseCategory
                                    .valueOf(categoryCode),
                            notes = notes,
                            expectedVersion = expense.version,
                            reason = reason,
                        ),
                    )
                }
            },
            guarded =
                GuardedStateless(
                    decode = { it.body<ExpenseResponse>() },
                    commit = { updated ->
                        replaceExpenseRow(updated)
                        actionTracker.finish(key)
                    },
                    // #173 — the superseded-PATCH gate folds into the stale flag (a branch/day switch
                    // mid-flight must leave the action + its tracker terminal inert).
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(
                            key,
                            "expense:update",
                            response,
                            conflictMessage = "Expense changed elsewhere — reloaded",
                        ) {
                            reloadSection(EditSection.EXPENSES)
                        }
                    },
                    onError = { e ->
                        actionTracker.fail(key, "expense:update failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    fun deleteExpense(
        expense: ExpenseResponse,
        reason: String,
    ) {
        val key = "expense:delete:${expense.id}"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "deleteExpense",
            endpoint = "DELETE /api/expenses/${expense.id}",
            block = {
                apiClient.httpClient.delete(ApiRoutes.expense(expense.id)) {
                    setBody(DeleteExpenseRequest(reason = reason))
                }
            },
            guarded =
                GuardedStateless(
                    decode = { it.body<ExpenseResponse>() },
                    commit = { deleted ->
                        replaceExpenseRow(deleted)
                        actionTracker.finish(key)
                    },
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(key, "expense:delete", response)
                    },
                    onError = { e ->
                        actionTracker.fail(key, "expense:delete failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    fun restoreExpense(
        expense: ExpenseResponse,
        reason: String?,
    ) {
        val key = "expense:restore:${expense.id}"
        if (!actionTracker.tryBegin(key)) return
        val generation = editDataGeneration
        handler.launchStatelessGuarded(
            operation = "restoreExpense",
            endpoint = "POST /api/expenses/${expense.id}/restore",
            block = {
                apiClient.httpClient.post(ApiRoutes.expenseRestore(expense.id)) {
                    setBody(RestoreExpenseRequest(reason = reason))
                }
            },
            guarded =
                GuardedStateless(
                    decode = { it.body<ExpenseResponse>() },
                    commit = { restored ->
                        replaceExpenseRow(restored)
                        actionTracker.finish(key)
                    },
                    stale = { generation != editDataGeneration },
                    onNonSuccess = { response ->
                        failActionOrSilent403(key, "expense:restore", response)
                    },
                    onError = { e ->
                        // Pass-10 HARD — the one onError the pass-9 batch missed: a transport failure
                        // must keep the restore dialog open with an inline error (the close-on-success
                        // effect keys on the ABSENCE of an error).
                        actionTracker.fail(key, "expense:restore failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    private fun replaceExpenseRow(updated: ExpenseResponse) {
        val current =
            (editExpensesState.value as? UiState.Success<List<ExpenseResponse>>)
                ?.data
                .orEmpty()
        editExpensesState.value =
            UiState.Success(current.map { if (it.id == updated.id) updated else it })
    }

    // ─────────────────────────── compensation actions ───────────────────────────

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
            conflictsState.value = conflictsState.value + key
            reload?.invoke()
        }
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newId(): String =
        kotlin.uuid.Uuid
            .random()
            .toString()

    // ─────────── exports (#560, from FinanceReportExportOps) ───────────
    // ─────────────────────────── exports (D6) ───────────────────────────

    private fun exportMode(
        key: String,
        url: String,
    ) {
        if (downloadsState.value.containsKey(key) && downloadsState.value[key] is UiState.Loading) return
        exportErrorsState.value = exportErrorsState.value - key
        downloadsState.value = downloadsState.value + (key to UiState.Loading)
        handler.launchStateless(
            operation = "export:$key",
            endpoint = "GET export $key",
            block = { apiClient.httpClient.get(url) },
            transform = {
                downloadsState.value =
                    downloadsState.value +
                    (
                        key to
                            UiState.Success(
                                DownloadPayload(
                                    fileName = fileNameOf(it),
                                    bytes = it.readRawBytes(),
                                ),
                            )
                    )
            },
            hooks =
                StatelessHooks(
                    onNonSuccess = { response ->
                        downloadsState.value = downloadsState.value - key
                        exportErrorsState.value =
                            exportErrorsState.value + (key to "Export failed: ${response.status.value}")
                    },
                    onError = { e ->
                        downloadsState.value = downloadsState.value - key
                        exportErrorsState.value =
                            exportErrorsState.value + (key to "Export failed: ${e.message ?: "network error"}")
                    },
                ),
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
                val month = appliedMonthState.value
                ApiRoutes.branchExportWithQuery(
                    ApiRoutes.branchExportMonthly(branchId),
                    "year=${month.year}&month=${month.month.ordinal + 1}&format=$format",
                )
            }

            ReportMode.ALL_TIME -> {
                ApiRoutes.branchExportWithQuery(ApiRoutes.branchExportAllTime(branchId), "format=$format")
            }

            ReportMode.DATE_RANGE -> {
                val range = appliedRangeState.value
                if (range != null) {
                    ApiRoutes.branchExportWithQuery(
                        ApiRoutes.branchExportRange(branchId),
                        "from=${range.first}&to=${range.second}&format=$format",
                    )
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
            url =
                ApiRoutes.branchExportWithQuery(
                    ApiRoutes.branchExportDaily(branchId),
                    "date=${day.date}&format=$format",
                ),
        )
    }

    fun exportPublic(
        kind: String,
        format: String,
    ) {
        exportMode(
            key = "public:$kind:$format",
            url = ApiRoutes.branchExportWithQuery("${ApiRoutes.BRANCHES_EXPORT}/$kind", "format=$format"),
        )
    }

    /** #105 D4 — the toolbar's mode export (Daily has none — per-day only, in the detail). */
    fun exportModeCurrent(format: String) {
        val branchId = selectedBranchIdState.value ?: return
        val url = modeExportUrl(modeState.value, branchId, format)
        if (url.isEmpty()) return
        // Keyed on branchId (pass-9 SOFT): a late landing from a superseded branch must not
        // block/mislabel the current branch's export.
        exportMode(
            key = "mode:$branchId:${modeState.value.name}:$format",
            url = url,
        )
    }

    fun consumeDownload(key: String) {
        downloadsState.value = downloadsState.value - key
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

    companion object {
        private const val FEED_PAGE_SIZE = 20
    }
}

private enum class FeedFetchMode(
    val operationName: String,
) {
    Cold("loadFeed"),
    Refresh("refreshFeed"),
    LoadMore("loadMore"),
}

enum class EditSection {
    EXPENSES,
    COMPENSATIONS,
    ALLOWANCES,
}
