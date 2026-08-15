package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateReliefInviteRequest
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
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

    // keep-last (the #143 shape, VM-side; the #162 KeepLast unifier): the freshest received
    // list survives Loading/Error so the section keeps rendering across reloads and composition
    // re-entries.
    private val keptReceived = KeepLast<List<ReliefInviteResponse>>(viewModelScope)
    val received: StateFlow<UiState<List<ReliefInviteResponse>>> = keptReceived.state
    val freshestReceived: StateFlow<List<ReliefInviteResponse>?> = keptReceived.freshest

    private val _acceptResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val acceptResult: StateFlow<UiState<Unit>> = _acceptResult.asStateFlow()

    private val _declineResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val declineResult: StateFlow<UiState<Unit>> = _declineResult.asStateFlow()

    private val _candidates = MutableStateFlow<UiState<List<ReliefCandidateResponse>>>(UiState.Idle)
    val candidates: StateFlow<UiState<List<ReliefCandidateResponse>>> = _candidates.asStateFlow()

    // Throwaway handler target for the sent list (the #162 keyed-mirror shape): the screen
    // renders ONLY the branch-keyed mirror (sentByKey) — the live Loading/Error transitions
    // land here and are consumed by no one (the mirror renders through them).
    private val sentListFlow = MutableStateFlow<UiState<List<ReliefInviteResponse>>>(UiState.Idle)

    // keep-last for the inviter's sent list, BRANCH-KEYED (the #162 KeepLastByKey shape — the
    // panel re-opens per branch card and a reload must keep the previous list rendered). Keying
    // the mirror by branchId makes the #160 pass-1/pass-2 cross-branch bleed structurally
    // unrenderable: the screen gates on the CURRENT panel's key, so another branch's rows (with
    // live Retract) can never pass the gate — the commit-stamp machinery (`_sentBranch` +
    // `sentStamp` + the stale-response substitution) that guarded the old single-slot mirror is
    // deleted with it. A stale in-flight response still commits to the live state, but the
    // screen never renders the live state — only this keyed mirror.
    private val keptSent = KeepLastByKey<String, List<ReliefInviteResponse>>()
    val sentByKey: StateFlow<Map<String, List<ReliefInviteResponse>>> = keptSent.lastByKey

    private val _createResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val createResult: StateFlow<UiState<Unit>> = _createResult.asStateFlow()

    private val _retractResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val retractResult: StateFlow<UiState<Unit>> = _retractResult.asStateFlow()

    // Bumped on every successful action: a load that lands with a mismatched stamp predates
    // the action and must not resurrect the resolved row (the #141 stamp pattern).
    private var actionStamp = 0L

    // #113 shape: the newest search cancels the in-flight one — a stale response dies at the
    // cancellation instead of committing.
    private var searchJob: Job? = null

    fun loadReceived(): Job {
        if (keptReceived.state.value is UiState.Loading) return Job()
        val stamp = actionStamp
        return handler.launch(
            state = keptReceived.stateFlow,
            operation = "loadReceived",
            endpoint = "GET /api/relief-invites",
            block = { apiClient.httpClient.get("/api/relief-invites") },
            transform = { response ->
                val body = response.body<List<ReliefInviteResponse>>()
                if (stamp != actionStamp) {
                    // An accept/decline landed while the load was in flight — committing the
                    // snapshot would resurrect the resolved row (the #141 resurrect class).
                    // Two recovery paths with distinct jobs: the SUBSTITUTION (currentReceivedList
                    // — removeReceived assigns Success synchronously, FIFO-Main converges the
                    // mirror) keeps the post-action list on screen; the RE-ISSUE converges
                    // server truth — rows the action couldn't know (cross-device accepts,
                    // new invites) land from the fresh GET. Both are pinned by tests.
                    loadReceived()
                    currentReceivedList() ?: emptyList()
                } else {
                    body
                }
            },
        )
    }

    fun acceptInvite(inviteId: String): Job =
        handler.launch(
            state = _acceptResult,
            operation = "acceptInvite",
            endpoint = "POST /api/relief-invites/$inviteId/accept",
            block = { apiClient.httpClient.post("/api/relief-invites/$inviteId/accept") },
            // #113 shape: a 409 means the invite is already resolved (double-tap race or a
            // cross-device accept) — the row must leave the section, so reload instead of
            // surfacing an error on a stale row (the markRead absent-row defense precedent).
            onNonSuccess = onConflictReload(_acceptResult, inviteId),
            transform = {
                actionStamp++
                removeReceived(inviteId)
                Unit
            },
        )

    fun declineInvite(inviteId: String): Job =
        handler.launch(
            state = _declineResult,
            operation = "declineInvite",
            endpoint = "POST /api/relief-invites/$inviteId/decline",
            block = { apiClient.httpClient.post("/api/relief-invites/$inviteId/decline") },
            onNonSuccess = onConflictReload(_declineResult, inviteId),
            transform = {
                actionStamp++
                removeReceived(inviteId)
                Unit
            },
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
                    apiClient.httpClient.get("/api/branches/$branchId/relief-candidates?q=$encodedQuery&date=$date")
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
                apiClient.httpClient.post("/api/branches/$branchId/relief-invites") {
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

    fun loadSent(branchId: String): Job {
        // No in-flight guard and no synchronous branch pre-set: a newer load must always
        // launch (a same-key guard would let an in-flight load swallow a panel re-open's
        // refetch). Stale in-flight responses are inert by construction — the mirror is
        // keyed by branch, so a response for another branch commits under its own key and
        // the screen's per-key gate never renders it.
        return handler.launch(
            state = sentListFlow,
            operation = "loadSent",
            endpoint = "GET /api/branches/$branchId/relief-invites",
            block = { apiClient.httpClient.get("/api/branches/$branchId/relief-invites") },
            transform = { response ->
                val body = response.body<List<ReliefInviteResponse>>()
                // Keyed commit (the #162 KeepLastByKey shape): the mirror entry for this
                // branch flips together with the committed body — the screen gate
                // `lastByKey[panelBranch]` can then trust that a passing gate means the
                // rendered list IS this panel's. A stale response for an older panel commits
                // under the OLD branch's key and can never render here.
                keptSent.commit(branchId, body)
                body
            },
            onNonSuccess = {
                keptSent.finish(branchId)
                false
            },
            onError = {
                keptSent.finish(branchId)
            },
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
            block = { apiClient.httpClient.post("/api/relief-invites/$inviteId/retract") },
            transform = {
                loadSent(branchId)
                Unit
            },
        )

    private fun currentReceivedList(): List<ReliefInviteResponse>? = keptReceived.freshestValue()

    private fun removeReceived(inviteId: String) {
        // Decrement only when the row actually left a KNOWN list: with no list loaded the badge
        // baseline is the poller's count, and decrementing against an unknown list would corrupt
        // it — the 60s poll overwrite self-corrects (the unread markRead precedent).
        val current = keptReceived.freshestValue() ?: return
        val remaining = current.filterNot { it.id == inviteId }
        if (remaining.size == current.size) return
        keptReceived.stateFlow.value = UiState.Success(remaining)
        NotificationState.decrementInvites()
    }
}
