package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
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
    internal val apiClient: ApiClient,
    private val now: Instant = Clock.System.now(),
) : ViewModel() {
    internal val handler = ApiCallHandler(viewModelScope, "FinanceVM")
    internal val today: LocalDate =
        now.toLocalDateTime(TimeZone.of("Asia/Manila")).date
    internal val defaultMonth: YearMonth = YearMonth(today.year, today.month.ordinal + 1)

    // ─────────────────────────── branches ───────────────────────────

    private val _branches = MutableStateFlow<UiState<List<BranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<BranchResponse>>> = _branches.asStateFlow()

    internal val selectedBranchIdState = MutableStateFlow<String?>(null)
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
                        list.firstOrNull { b -> b.id == SessionState.selectedBranchId.value }
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

    internal val modeState = MutableStateFlow(ReportMode.DAILY)
    val mode: StateFlow<ReportMode> = modeState.asStateFlow()

    /** Editable `yyyy-MM` text for the MONTHLY mode and the ALL_TIME calendar jump. */
    internal val monthInputState = MutableStateFlow(defaultMonth.toString())
    val monthInput: StateFlow<String> = monthInputState.asStateFlow()

    /** Applied MONTHLY month (validated at apply time — #485: feed/rollup/export read this, never the draft). */
    internal val appliedMonthState = MutableStateFlow(defaultMonth)
    val appliedMonth: StateFlow<YearMonth> = appliedMonthState.asStateFlow()

    internal val rangeFromInputState = MutableStateFlow("")
    val rangeFromInput: StateFlow<String> = rangeFromInputState.asStateFlow()

    internal val rangeToInputState = MutableStateFlow("")
    val rangeToInput: StateFlow<String> = rangeToInputState.asStateFlow()

    /** Applied date-range window (validated at apply time). */
    internal val appliedRangeState = MutableStateFlow<Pair<String, String>?>(null)
    val appliedRange: StateFlow<Pair<String, String>?> = appliedRangeState.asStateFlow()

    /** Applied ALL_TIME jump month (null = unbounded all-time). */
    internal val jumpMonthState = MutableStateFlow<YearMonth?>(null)
    val jumpMonth: StateFlow<YearMonth?> = jumpMonthState.asStateFlow()

    /** Mode-parameter validation errors (rendered inline next to the field). */
    internal val paramErrorState = MutableStateFlow<String?>(null)
    val paramError: StateFlow<String?> = paramErrorState.asStateFlow()

    internal val monthlyRollupState = MutableStateFlow<UiState<MonthlyRemittanceSummaryResponse?>>(UiState.Idle)
    val monthlyRollup: StateFlow<UiState<MonthlyRemittanceSummaryResponse?>> = monthlyRollupState.asStateFlow()

    /** Rollup responses from a superseded branch/mode are inert (P4 pass-1 HARD). */
    internal var rollupGeneration = 0

    internal val feedEntriesState = MutableStateFlow<UiState<List<DailySalesSummaryResponse>>>(UiState.Idle)
    val feedEntries: StateFlow<UiState<List<DailySalesSummaryResponse>>> = feedEntriesState.asStateFlow()

    internal val nextCursorState = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = nextCursorState.asStateFlow()

    internal val isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = isLoadingMoreState.asStateFlow()

    internal val isRefreshingState = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = isRefreshingState.asStateFlow()

    internal val loadMoreErrorState = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = loadMoreErrorState.asStateFlow()

    internal val refreshErrorState = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = refreshErrorState.asStateFlow()

    // Every window/mode/branch change bumps the generation; an in-flight page from a superseded
    // generation is inert (no list/cursor/error writes) — the AuditLog #144 shape.
    internal var feedGeneration = 0

    internal val selectedDayState = MutableStateFlow<DailySalesSummaryResponse?>(null)
    val selectedDay: StateFlow<DailySalesSummaryResponse?> = selectedDayState.asStateFlow()

    internal val editModeState = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = editModeState.asStateFlow()

    internal val reliefDayState = MutableStateFlow<UiState<DailySalesSummaryResponse>>(UiState.Idle)
    val reliefDay: StateFlow<UiState<DailySalesSummaryResponse>> = reliefDayState.asStateFlow()

    /** Generation guard for [loadReliefDay] — a superseded relief fetch stays inert (the #143 class). */
    internal var reliefGeneration = 0

    /**
     * #158 — the relief day-scoped read entry: a BRANCH_DAY grant holder (no VIEW_BRANCH_DATA)
     * fetches a single day by date via the day-scoped summary read. The branch is the
     * clocked-in branch (the relief branch — the picker lists only BRANCH-granted branches,
     * which a relief delegate has none of). Success seeds [selectedDay] so the day detail +
     * editor flows work off the same state.
     */
    internal val editExpensesState = MutableStateFlow<UiState<List<ExpenseResponse>>>(UiState.Idle)
    val editExpenses: StateFlow<UiState<List<ExpenseResponse>>> = editExpensesState.asStateFlow()

    internal val editCompensationsState = MutableStateFlow<UiState<List<CompensationResponse>>>(UiState.Idle)
    val editCompensations: StateFlow<UiState<List<CompensationResponse>>> = editCompensationsState.asStateFlow()

    internal val editAllowancesState = MutableStateFlow<UiState<List<AllowanceResponse>>>(UiState.Idle)
    val editAllowances: StateFlow<UiState<List<AllowanceResponse>>> = editAllowancesState.asStateFlow()

    internal val editUsersState = MutableStateFlow<UiState<List<BranchDayUserResponse>>>(UiState.Idle)
    val editUsers: StateFlow<UiState<List<BranchDayUserResponse>>> = editUsersState.asStateFlow()

    // Per-action inline errors (ADR-0022 pessimistic axis) keyed by action key + per-action
    // in-flight guard (double-tap closure).
    internal val actionTracker = ActionTracker<String>()
    val editErrors: StateFlow<Map<String, String>> = actionTracker.errors

    // One-shot conflict signal (pass-1 HARD, pass-2 reworked): a 409 adds the key to a NEW Set
    // instance so the screen's LaunchedEffect(conflicts) re-fires — the open edit dialog holds a
    // stale expectedVersion and must close (re-saving it would loop 409s; the reloaded row is
    // the retry source). The screen consumes the key (consumeConflict) after reacting, so a
    // repeat 409 on the same row re-emits, and a persisted key can never slam a LATER fresh
    // dialog shut. (Create-conflict keys — comp:create etc. — are never consumed; they surface
    // only as inline errors on dialogs that close on dismiss, and clearEditData clears them.)
    internal val conflictsState = MutableStateFlow<Set<String>>(emptySet())
    val conflicts: StateFlow<Set<String>> = conflictsState.asStateFlow()

    fun consumeConflict(key: String) {
        conflictsState.value = conflictsState.value - key
    }

    val inFlightActions: StateFlow<Set<String>> = actionTracker.inFlight

    internal var editDataGeneration = 0

    data class DownloadPayload(
        val fileName: String,
        val bytes: ByteArray,
    )

    /** Export keys: `mode:<branchId>:<mode>:<format>`, `day:<branchDayId>:<format>`, `public:<kind>:<format>`. */
    internal val downloadsState = MutableStateFlow<Map<String, UiState<DownloadPayload>>>(emptyMap())
    val downloads: StateFlow<Map<String, UiState<DownloadPayload>>> = downloadsState.asStateFlow()

    internal val exportErrorsState = MutableStateFlow<Map<String, String>>(emptyMap())
    val exportErrors: StateFlow<Map<String, String>> = exportErrorsState.asStateFlow()

    companion object {
        internal const val FEED_PAGE_SIZE = 20
    }
}

enum class EditSection {
    EXPENSES,
    COMPENSATIONS,
    ALLOWANCES,
}
