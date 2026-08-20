package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * One entry in the slot-order list for a branch (#106 D4). Derived from the users list's
 * assignments: every user with an active assignment at the branch, slot ASC then display name
 * tiebreak. `isDeactivated` powers the dimmed + controls-disabled treatment (D2).
 */
data class UserSlotRow(
    val userId: String,
    val displayName: String,
    val isDeactivated: Boolean,
    val slot: Short,
)

/** Client-side slot-order derivation (#106 D4): slot ASC, display name tiebreak. */
fun slotOrderForBranch(
    users: List<UserSummaryResponse>,
    branchId: String,
): List<UserSlotRow> =
    users
        .mapNotNull { user ->
            user.assignments.firstOrNull { it.branchId == branchId }?.let { assignment ->
                UserSlotRow(
                    userId = user.id,
                    displayName = user.displayName,
                    isDeactivated = user.status == UserStatus.INACTIVE,
                    slot = assignment.slot,
                )
            }
        }.sortedWith(compareBy<UserSlotRow> { it.slot }.thenBy { it.displayName })

/** Client-side slot-number validation mirroring the backend's "Slot must be 1 or greater" (400). */
fun parseSlotInput(input: String): Short? = input.trim().toShortOrNull()?.takeIf { it >= 1 }

/**
 * The EditSlotDialog's rejection message, pure so the decision is testable (#143 pass-3 — the
 * #142 lesson: message-truth decisions that regress inline must be extracted). Distinguishes the
 * two rejection classes parseSlotInput conflates (both return null):
 * - a number ≥ 1 but beyond SMALLINT (32767) — "too large". Includes inputs that overflow even
 *   Long ("99999999999999999999"): all-digit strings that fail to parse as Long are certainly
 *   > 32767 (pass-2's toLongOrNull-only classification regressed exactly here). Note Kotlin's
 *   Short/Long parsing is digit-aware (non-ASCII numerals like "٥" parse as 5 — pass-4's
 *   "Unicode mislabel" SOFT was a false premise; isDigit() aligns with the parser).
 * - anything else (≤ 0, non-numeric, empty) — "Slot must be 1 or greater" (the backend's 400).
 */
fun slotInputError(input: String): String? {
    val trimmed = input.trim()
    if (parseSlotInput(trimmed) != null) return null
    val numeric = trimmed.toLongOrNull()
    val tooLarge =
        (numeric != null && numeric >= 1) ||
            (numeric == null && trimmed.isNotEmpty() && trimmed.all { it.isDigit() })
    return if (tooLarge) "Slot number too large (max 32767)" else "Slot must be 1 or greater"
}

/** D2 — client-side search on display name/username, instant (no extra round-trips, YAGNI). */
fun filterUsers(
    users: List<UserSummaryResponse>,
    query: String,
): List<UserSummaryResponse> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return users
    return users.filter {
        it.displayName.contains(trimmed, ignoreCase = true) ||
            it.username.contains(trimmed, ignoreCase = true)
    }
}

/**
 * State model for the User Management screen (#106 D2-D5, built in #135).
 *
 * - [users]: full flat user list (`GET /api/users`, GLOBAL MANAGE_USERS).
 * - [branches]: picker source for the D4 slot manager (`GET /api/branches`, same gate).
 * - Mutations (deactivate/reactivate/swap/update-slot) are pessimistic (ADR-0022): a 2xx
 *   mutates the in-memory list in place (rows update without a reload); a failure keeps the row
 *   and surfaces an inline per-action error ([actionErrors]) keyed by the same key as [inFlight].
 */
class UserViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "UserVM")

    // Keep-last-results, VM-side (the #143/#161 port shape, unified by #162 — KeepLast):
    // the freshest list (Success data, or the last successful list) survives Loading/Error so
    // the screen keeps rendering it across reloads and composition re-entries (a screen-side
    // remember would die on re-entry; the VM is entry-scoped). It is also the fallback
    // mutations mutate in place when a reload failed but the list is still rendered — a row
    // action must not dead-tap against a rendered list.
    private val keptUsers = KeepLast<List<UserSummaryResponse>>(viewModelScope)
    val users: StateFlow<UiState<List<UserSummaryResponse>>> = keptUsers.state
    val freshestUsers: StateFlow<List<UserSummaryResponse>?> = keptUsers.freshest

    private val _branches = MutableStateFlow<UiState<List<BranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<BranchResponse>>> = _branches.asStateFlow()

    // Per-action in-flight guard + inline errors (ADR-0022 pessimistic axis). Keys:
    // "deactivate:$userId", "reactivate:$userId", "swap:$branchId:$userIdA:$userIdB",
    // "slot:$branchId:$userId".
    private val actionTracker = ActionTracker<String>()
    val inFlight: StateFlow<Set<String>> = actionTracker.inFlight
    val actionErrors: StateFlow<Map<String, String>> = actionTracker.errors

    // Every mutation lands here with a state-less handler call (the #168 state-less launch):
    // the real effects route through actionTracker (tryBegin/fail/finish) + the in-place list
    // transforms, so the throwaway UiState flow the stateful launch would have written is
    // unneeded (the #122/#120 keep-last-list shape).
    fun loadUsers() {
        // Guard 1 (mutations): a reload mid-mutation would let the mutation's in-place transform
        // re-apply to the fresh list (swap double-applies — pass-1 P2/P4 HARD class; the
        // Refresh-button gate alone couldn't cover non-click triggers like LaunchedEffect
        // refires on rotation/re-entry, pass-2 P2/P4). While any mutation is in flight the
        // locally-mutated list IS the authority (mutations only exist once the list loaded
        // Success), so skipping is safe: the only way inFlight is non-empty is a Success list
        // that in-place updates keep current. The guard sits BEFORE the error clear: a skipped
        // load leaves the list untouched, so its errors still describe current state (pass-3 P2
        // — clearing them would hide a real failure the next composition's failed-action display
        // relies on).
        // Guard 2 (loads): skip while a load is in flight — a double-fire (the screen's entry
        // effect re-running on rotation, a refresh tap during a load) must not stack two GETs
        // (#161 keep-last port no-refire axis). The Loading check works because the pre-set
        // below makes it SYNCHRONOUS: the handler's own Loading assignment is not guaranteed to
        // land before launch returns (Main.immediate may execute the body inline, but the guard
        // must not depend on dispatch timing) — a plain post-launch check would race a
        // same-frame double-tap (the #143 in-flight shape).
        if (keptUsers.state.value is UiState.Loading || inFlight.value.isNotEmpty()) return
        // Synchronous guard pre-set (see Guard 2). Self-clearing by construction: the handler
        // owns the state flow and assigns Error/Success on every non-cancellation exit path, so
        // no separate flag can wedge (the #140 stuck-Loading class). Cancellation only happens
        // at VM teardown (the load holds no cancelable handle), where the guard dies with the
        // VM.
        keptUsers.stateFlow.value = UiState.Loading
        // A reload replaces the list; the errors describe actions against the pre-reload list
        // (pass-1 P4: "Deactivate failed: 500" persisting beside fresh data is stale). Errors
        // only — markers are untouched (empty here anyway: the guard above skips while any
        // mutation is in flight).
        actionTracker.clearErrors()
        handler.launch(
            state = keptUsers.stateFlow,
            operation = "loadUsers",
            endpoint = "GET /api/users",
            block = { apiClient.httpClient.get(ApiRoutes.USERS) },
            transform = { it.body() },
        )
    }

    fun loadBranches() {
        handler.launch(
            state = _branches,
            operation = "loadBranches",
            endpoint = "GET /api/branches",
            block = { apiClient.httpClient.get(ApiRoutes.BRANCHES) },
            transform = { it.body() },
        )
    }

    // D3 — deactivate (confirmation dialog shown by the screen) → existing PATCH; the row flips
    // to INACTIVE in place. Self-deactivate is hidden on the own row; a backend 400 would surface
    // the inline error.
    fun deactivateUser(userId: String) {
        runMutation(
            key = "deactivate:$userId",
            operation = "deactivateUser",
            endpoint = "PATCH /api/users/$userId/deactivate",
            block = { apiClient.httpClient.patch(ApiRoutes.userDeactivate(userId)) },
            onSuccess = { mutateUser(userId) { it.withStatus(UserStatus.INACTIVE) } },
            statusMessage = { "Deactivate failed: ${it.value}" },
        )
    }

    // D3 — reactivate (direct row action) → symmetric PATCH; row flips to ACTIVE, deactivated_at
    // cleared (mirrors #133's idempotent pair semantics).
    fun reactivateUser(userId: String) {
        runMutation(
            key = "reactivate:$userId",
            operation = "reactivateUser",
            endpoint = "PATCH /api/users/$userId/reactivate",
            block = { apiClient.httpClient.patch(ApiRoutes.userReactivate(userId)) },
            onSuccess = { mutateUser(userId) { it.withStatus(UserStatus.ACTIVE) } },
            statusMessage = { "Reactivate failed: ${it.value}" },
        )
    }

    // D4 — pairwise swap (desktop up/down arrows; one move = one swap with the neighbor).
    // The in-flight key normalizes the pair order so a same-frame reversed double-tap
    // (row-B ▼ then row-C ▲) hits the same guard key instead of dispatching twice (pass-1
    // P3 finding — benign when both land, but concurrent duplicates are still waste).
    fun swapSlots(
        branchId: String,
        userIdA: String,
        userIdB: String,
    ) {
        val (first, second) = listOf(userIdA, userIdB).sorted()
        runMutation(
            key = "swap:$branchId:$first:$second",
            operation = "swapSlots",
            endpoint = "POST /api/branches/$branchId/slots/swap",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchSlotsSwap(branchId)) {
                    setBody(SwapSlotsRequest(userIdA, userIdB))
                }
            },
            onSuccess = { swapSlotsInPlace(branchId, userIdA, userIdB) },
            statusMessage = { "Swap failed: ${it.value}" },
        )
    }

    // D4 — tap-to-edit slot number (mobile primary; manual number fallback on both platforms).
    fun updateSlot(
        branchId: String,
        userId: String,
        slot: Short,
    ) {
        runMutation(
            key = "slot:$branchId:$userId",
            operation = "updateSlot",
            endpoint = "PATCH /api/branches/$branchId/assignments/$userId/slot",
            block = {
                apiClient.httpClient.patch(ApiRoutes.branchAssignmentSlot(branchId, userId)) {
                    setBody(UpdateSlotRequest(slot))
                }
            },
            onSuccess = { mutateAssignmentSlot(branchId, userId, slot) },
            statusMessage = { "Slot update failed: ${it.value}" },
        )
    }

    private fun runMutation(
        key: String,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        onSuccess: () -> Unit,
        statusMessage: (HttpStatusCode) -> String,
    ) {
        // A mutation landing while a reload is in flight would be clobbered by the load's
        // pre-mutation snapshot (the pass-1 HARD interleave the keep-last gate opened: rows
        // render live during Loading now, and the load's last-writer Success would silently
        // revert the PATCH — the #141 stale-mask class). The reload response is the authority;
        // skipping restores the pre-port invariant (rows were untappable during Loading). Belt:
        // the screen disables the row actions + dialog confirms while Loading; this guard covers
        // the same-frame tap that slips past the composition gate.
        if (keptUsers.state.value is UiState.Loading) return
        if (!actionTracker.tryBegin(key)) return
        handler.launchStateless(
            operation = operation,
            endpoint = endpoint,
            block = { block() },
            transform = {
                actionTracker.finish(key)
                onSuccess()
            },
            onNonSuccess = { response ->
                actionTracker.fail(key, statusMessage(response.status))
            },
            onError = { e ->
                // Network failure — clear the in-flight guard so buttons re-enable AND surface
                // an inline error (ADR-0022 pessimistic contract: the row is kept and the
                // failure is visible). The state-less launch has no state flow to write; the
                // inline error is what the screen renders. (transform never deserializes
                // for these 204 ops, so only block() can throw here — onError covers it.)
                actionTracker.fail(key, e.message ?: "$operation failed")
            },
        )
    }

    private fun mutateUser(
        userId: String,
        transform: (UserSummaryResponse) -> UserSummaryResponse,
    ) {
        // mutate (#163): the exact-sync-read + Success-write discipline in one call. A failed
        // reload leaves the state Error while the freshest flow still renders the rows — the
        // action must not dead-tap against a rendered list (#161 port; the NotificationViewModel
        // currentUnreadList precedent). The success writes Success over Error, which is the
        // freshest truth for the mutated row.
        keptUsers.mutate { users ->
            users.map { if (it.id == userId) transform(it) else it }
        }
    }

    private fun mutateAssignmentSlot(
        branchId: String,
        userId: String,
        slot: Short,
    ) {
        mutateUser(userId) { user -> user.withSlot(branchId, slot) }
    }

    private fun swapSlotsInPlace(
        branchId: String,
        userIdA: String,
        userIdB: String,
    ) {
        // One mutate instead of the old two sequential writes: the old intermediate frame (A
        // with B's slot) never reached a screen — both writes ran synchronously in the
        // caller's frame, and deferred composition conflated them into the final swapped
        // list. The single write is the final swapped list; the null returns (either user
        // missing, or no assignment at the branch) map to no-write, the old `?: return`
        // paths.
        keptUsers.mutate { users ->
            val slotA =
                users
                    .firstOrNull { it.id == userIdA }
                    ?.assignments
                    ?.firstOrNull { it.branchId == branchId }
                    ?.slot
                    ?: return@mutate null
            val slotB =
                users
                    .firstOrNull { it.id == userIdB }
                    ?.assignments
                    ?.firstOrNull { it.branchId == branchId }
                    ?.slot
                    ?: return@mutate null
            users.map { user ->
                when (user.id) {
                    userIdA -> user.withSlot(branchId, slotB)
                    userIdB -> user.withSlot(branchId, slotA)
                    else -> user
                }
            }
        }
    }
}

/** The slot-copy shape shared by the D4 slot edit and the swap's two rows. */
private fun UserSummaryResponse.withSlot(
    branchId: String,
    slot: Short,
): UserSummaryResponse =
    copy(
        assignments =
            assignments.map {
                if (it.branchId == branchId) it.copy(slot = slot) else it
            },
    )

private fun UserSummaryResponse.withStatus(status: UserStatus): UserSummaryResponse =
    when (status) {
        UserStatus.INACTIVE -> {
            copy(
                status = UserStatus.INACTIVE,
                deactivatedAt =
                    deactivatedAt ?: Clock.System.now().toString(),
            )
        }

        UserStatus.ACTIVE -> {
            copy(status = UserStatus.ACTIVE, deactivatedAt = null)
        }
    }
