package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.CreateReliefInviteRequest
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Relief invite flow (#160) — both surfaces in one VM:
 *
 * - **Received** (the Notifications screen section): [loadReceived] with keep-last +
 *   no-refire gating (the #143 shape) and the accept/decline actions; resolved rows leave
 *   the list (the invite renders until resolved, not until read — #159 Q5).
 * - **Inviter** (the BranchSelect inviter side): candidate search (per-query Job
 *   cancellation — the #113 shape), create, sent list, retract.
 *
 * The VM is entry-scoped at both call sites (a fresh entry creates a fresh VM — the #112
 * self-cleaning pattern); the two surfaces never share one instance.
 */
class ReliefInviteViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReliefInviteVM")

    // The received surface (list + accept/decline) lives in [ReceivedInvites]; the flows and
    // actions surface as properties (the detekt function budget — the #348 ConcernPoster shape).
    private val receivedInvites = ReceivedInvites(apiClient, viewModelScope)
    val received: StateFlow<UiState<List<ReliefInviteResponse>>> = receivedInvites.received
    val freshestReceived: StateFlow<List<ReliefInviteResponse>?> = receivedInvites.freshestReceived
    val acceptResult: StateFlow<UiState<Unit>> = receivedInvites.acceptResult
    val declineResult: StateFlow<UiState<Unit>> = receivedInvites.declineResult

    val loadReceived: () -> Job = receivedInvites::loadReceived
    val acceptInvite: (String) -> Job = receivedInvites::acceptInvite
    val declineInvite: (String) -> Job = receivedInvites::declineInvite

    private val _candidates = MutableStateFlow<UiState<List<ReliefCandidateResponse>>>(UiState.Idle)
    val candidates: StateFlow<UiState<List<ReliefCandidateResponse>>> = _candidates.asStateFlow()

    // keep-last for the inviter's sent list, BRANCH-KEYED (the #162 KeepLastByKey shape,
    // mirror-only half — the #166 P5 mirror-only split: `keptSent` uses only the mirror; the
    // panel re-opens per branch card and a reload must keep the previous list rendered). Keying
    // the mirror by branchId makes the #160 pass-1/pass-2 cross-branch bleed structurally
    // unrenderable: the screen gates on the CURRENT panel's key, so another branch's rows (with
    // live Retract) can never pass the gate — the `_sentBranch` label + the stale-response
    // substitution that guarded the old single-slot mirror are deleted with it. Same-key
    // ordering (a stale snapshot landing after a mutation-triggered reload) is closed by the
    // loadSent stale gate (the #173 shape): only the newest launch's response commits.
    private val keptSent = KeyedMirror<String, List<ReliefInviteResponse>>()
    val sentByKey: StateFlow<Map<String, List<ReliefInviteResponse>>> = keptSent.lastByKey

    // #377 discovery read — branch-wide ACCEPTED duties (any active member may revoke),
    // same mirror-only keyed shape as keptSent: null until this branch's first commit hides
    // the section from non-members (the 403 never commits) and from cross-branch bleed.
    private val keptAccepted = KeyedMirror<String, List<ReliefInviteResponse>>()
    val acceptedByKey: StateFlow<Map<String, List<ReliefInviteResponse>>> = keptAccepted.lastByKey

    private val _createResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val createResult: StateFlow<UiState<Unit>> = _createResult.asStateFlow()

    private val _retractResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val retractResult: StateFlow<UiState<Unit>> = _retractResult.asStateFlow()

    private val _revokeResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val revokeResult: StateFlow<UiState<Unit>> = _revokeResult.asStateFlow()

    // #113 shape: the newest search cancels the in-flight one — a stale response dies at the
    // cancellation instead of committing.
    private var searchJob: Job? = null

    fun searchCandidates(
        branchId: String,
        query: String,
        date: String,
    ): Job {
        searchJob?.cancel()
        val encodedQuery = query.encodeURLParameter(spaceToPlus = false)
        searchJob =
            handler.launch(
                state = _candidates,
                operation = "searchCandidates",
                endpoint = "GET /api/branches/$branchId/relief-candidates?q=$encodedQuery&date=$date",
                block = {
                    apiClient.httpClient.get("${ApiRoutes.branchReliefCandidates(branchId)}?q=$encodedQuery&date=$date")
                },
                transform = { it.body() },
            )
        return searchJob!!
    }

    fun sendInvite(
        branchId: String,
        inviteeUserId: String,
        date: String,
    ): Job =
        handler.launch(
            state = _createResult,
            operation = "sendInvite",
            endpoint = "POST /api/branches/$branchId/relief-invites",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchReliefInvites(branchId)) {
                    setBody(CreateReliefInviteRequest(inviteeUserId = inviteeUserId, date = date))
                }
            },
            transform = {
                loadSent(branchId)
                // The invited user is no longer a candidate — drop them locally (the next
                // search would exclude them server-side anyway).
                val current = _candidates.value
                if (current is UiState.Success) {
                    _candidates.value = UiState.Success(current.data.filterNot { it.id == inviteeUserId })
                }
                Unit
            },
        )

    // Bumped per loadSent: a response that lands with a mismatched stamp belongs to an older
    // launch of ANY branch — committing it would let a stale snapshot be the last writer on a
    // key (the same-branch ordering class: a panel-open load in flight while a retract-triggered
    // reload fires; the pre-retract snapshot must not resurrect the retracted row — the #141
    // resurrect class). Cross-branch staleness is handled by the keyed mirror itself; this
    // stamp closes the same-key ordering the keyed mirror cannot see.
    private var sentStamp = 0L

    fun loadSent(branchId: String): Job {
        // No in-flight guard and no synchronous branch pre-set: a newer load must always
        // launch (a same-key guard would let an in-flight load swallow a panel re-open's
        // refetch or a mutation-triggered reload). Cross-branch staleness is inert by
        // construction — the mirror is keyed, so a response for another branch commits under
        // its own key and the screen's per-key gate never renders it. Same-branch ordering is
        // closed by the stale gate (the #173 shape): only the NEWEST launch's response
        // commits, whenever it lands (the gate runs BEFORE transform, so a stale body is never
        // deserialized — state/UI behavior identical to the old in-hook guard; only the wasted
        // parse and the stale landing's spurious error log are gone).
        val stamp = ++sentStamp
        return handler.launchStateless(
            operation = "loadSent",
            endpoint = "GET /api/branches/$branchId/relief-invites",
            block = { apiClient.httpClient.get(ApiRoutes.branchReliefInvites(branchId)) },
            transform = { response ->
                val body = response.body<List<ReliefInviteResponse>>()
                // Keyed commit (the #162 KeepLastByKey shape, mirror-only half), newest-launch-
                // wins: the mirror entry for this branch flips together with the committed body —
                // the screen gate `sentByKey[panelBranch]` can then trust that a passing gate
                // means the rendered list IS this panel's.
                keptSent.commit(branchId, body)
            },
            hooks =
                StatelessHooks(
                    stale = { stamp != sentStamp },
                ),
        )
    }

    fun retractInvite(
        inviteId: String,
        branchId: String,
    ): Job =
        handler.launch(
            state = _retractResult,
            operation = "retractInvite",
            endpoint = "POST /api/relief-invites/$inviteId/retract",
            block = { apiClient.httpClient.post(ApiRoutes.reliefInviteAction(inviteId, "retract")) },
            transform = {
                loadSent(branchId)
                Unit
            },
        )

    // Same per-load ordering stamp as sentStamp, for the accepted mirror.
    private var acceptedStamp = 0L

    fun loadAccepted(branchId: String): Job {
        // Mirror-only keyed commit (the loadSent shape): a newer load must always launch;
        // same-key ordering is closed by the stale gate — only the newest launch commits.
        val stamp = ++acceptedStamp
        return handler.launchStateless(
            operation = "loadAccepted",
            endpoint = "GET /api/branches/$branchId/relief-invites/accepted",
            block = { apiClient.httpClient.get(ApiRoutes.branchReliefInvitesAccepted(branchId)) },
            transform = { response ->
                keptAccepted.commit(branchId, response.body<List<ReliefInviteResponse>>())
            },
            hooks =
                StatelessHooks(
                    stale = { stamp != acceptedStamp },
                ),
        )
    }

    /**
     * #377 — revoke an ACCEPTED future duty (ruling 2: any active branch member). Success and
     * the authoritative 409 (already decided — another member revoked first, or a double-tap)
     * both converge via reload; other failures surface the server's `error` message
     * (400 duty-started reads as the business reason, not a bare status code).
     */
    fun revokeInvite(
        inviteId: String,
        branchId: String,
    ): Job =
        handler.launch(
            LaunchRequest(
                state = _revokeResult,
                operation = "revokeInvite",
                endpoint = "POST /api/relief-invites/$inviteId/revoke",
                block = { apiClient.httpClient.post(ApiRoutes.reliefInviteAction(inviteId, "revoke")) },
                onNonSuccess = { response ->
                    if (response.status == HttpStatusCode.Conflict) {
                        _revokeResult.value = UiState.Idle
                    } else {
                        _revokeResult.value = UiState.Error(revokeErrorMessage(response))
                    }
                    reloadRevocationSurfaces(branchId)
                    true
                },
                transform = {
                    reloadRevocationSurfaces(branchId)
                    Unit
                },
            ),
        )

    private fun reloadRevocationSurfaces(branchId: String) {
        loadAccepted(branchId)
        loadSent(branchId)
    }

    private suspend fun revokeErrorMessage(response: HttpResponse): String =
        runCatching { response.body<Map<String, String>>()["error"] }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Revoke failed (${response.status.value})"
}

/**
 * The received-invites surface (#160 Notifications section): the keep-last list (the #143
 * shape), accept/decline actions, and the #165 stamp/fallback resurrection guard. Extracted
 * from [ReliefInviteViewModel] for the detekt function budget (the ConcernPoster shape); the
 * `actionStamp` interplay is internal to this cluster.
 */
private class ReceivedInvites(
    private val apiClient: ApiClient,
    scope: CoroutineScope,
) {
    private val handler = ApiCallHandler(scope, "ReliefInviteVM")

    // keep-last (the #143 shape, VM-side; the #162 KeepLast unifier): the freshest received
    // list survives Loading/Error so the section keeps rendering across reloads and composition
    // re-entries.
    private val keptReceived = KeepLast<List<ReliefInviteResponse>>(scope)
    val received: StateFlow<UiState<List<ReliefInviteResponse>>> = keptReceived.state
    val freshestReceived: StateFlow<List<ReliefInviteResponse>?> = keptReceived.freshest

    private val _acceptResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val acceptResult: StateFlow<UiState<Unit>> = _acceptResult.asStateFlow()

    private val _declineResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val declineResult: StateFlow<UiState<Unit>> = _declineResult.asStateFlow()

    // Bumped on every successful action: a load that lands with a mismatched stamp predates
    // the action and must not resurrect the resolved row (the #141 stamp pattern).
    private var actionStamp = 0L

    fun loadReceived(): Job {
        if (keptReceived.state.value is UiState.Loading) return Job()
        return handler.launch(
            LaunchRequest(
                state = keptReceived.stateFlow,
                operation = "loadReceived",
                endpoint = "GET /api/relief-invites",
                block = { apiClient.httpClient.get(ApiRoutes.RELIEF_INVITES) },
                transform = { it.body<List<ReliefInviteResponse>>() },
                // #165 stale-substitution guard (concentrated from the former in-transform block): an
                // accept/decline landing while the load was in flight must not resurrect the resolved
                // row (the #141 resurrect class). The stamp read at landing disagrees with the
                // launch-captured read, so the handler substitutes the fallback: currentReceivedList
                // reads the exact post-action list (removeReceived assigns Success synchronously,
                // and at fallback time the stale landing's own Loading write is current — the read
                // resolves to the freshest mirror, exact on Main.immediate); the re-issue converges
                // server truth — rows the action couldn't know (cross-device accepts, new invites)
                // land from the fresh GET. Both paths are pinned by tests.
                stamp = { actionStamp },
                fallback = {
                    loadReceived()
                    currentReceivedList() ?: emptyList()
                },
            ),
        )
    }

    fun acceptInvite(inviteId: String): Job =
        handler.launch(
            LaunchRequest(
                state = _acceptResult,
                operation = "acceptInvite",
                endpoint = "POST /api/relief-invites/$inviteId/accept",
                block = { apiClient.httpClient.post(ApiRoutes.reliefInviteAction(inviteId, "accept")) },
                // #113 shape: a 409 means the invite is already resolved (double-tap race or a
                // cross-device accept) — the row must leave the section, so reload instead of
                // surfacing an error on a stale row (the markRead absent-row defense precedent).
                onNonSuccess = onConflictReload(_acceptResult, inviteId),
                transform = {
                    actionStamp++
                    removeReceived(inviteId)
                    Unit
                },
            ),
        )

    fun declineInvite(inviteId: String): Job =
        handler.launch(
            LaunchRequest(
                state = _declineResult,
                operation = "declineInvite",
                endpoint = "POST /api/relief-invites/$inviteId/decline",
                block = { apiClient.httpClient.post(ApiRoutes.reliefInviteAction(inviteId, "decline")) },
                onNonSuccess = onConflictReload(_declineResult, inviteId),
                transform = {
                    actionStamp++
                    removeReceived(inviteId)
                    Unit
                },
            ),
        )

    /**
     * Shared 409-handling for accept/decline: the 409 is authoritative server confirmation that
     * the invite is resolved, so the row leaves locally (Idle-reset — the #140 stuck-Loading
     * class — + removal + reload). The stamp bump BEFORE the reload is load-bearing: a pre-409
     * in-flight load would otherwise commit its pre-resolution snapshot with a matching stamp
     * and resurrect the row (the #141 resurrect class, 4-lens review finding). The reload may
     * be gated by the in-flight guard — the local removal already converged the list, and the
     * gated load's landing goes down the stamp-mismatch substitution path.
     */
    private fun onConflictReload(
        state: MutableStateFlow<UiState<Unit>>,
        inviteId: String,
    ): suspend (io.ktor.client.statement.HttpResponse) -> Boolean =
        { response ->
            if (response.status == HttpStatusCode.Conflict) {
                state.value = UiState.Idle
                actionStamp++
                removeReceived(inviteId)
                loadReceived()
                true
            } else {
                false
            }
        }

    private fun currentReceivedList(): List<ReliefInviteResponse>? = keptReceived.freshestValue()

    private fun removeReceived(inviteId: String) {
        // Decrement only when the row actually left a KNOWN list: with no list loaded the badge
        // baseline is the poller's count, and decrementing against an unknown list would corrupt
        // it — the 60s poll overwrite self-corrects (the unread markRead precedent). false from
        // mutateRemoved (#164) covers both no-list and not-in-list: no write, no decrement.
        val removed = keptReceived.mutateRemoved { it.id == inviteId }
        if (removed) {
            NotificationState.decrementInvites()
        }
    }
}
