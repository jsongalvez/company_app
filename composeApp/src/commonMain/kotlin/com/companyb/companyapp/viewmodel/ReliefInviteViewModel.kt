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
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

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

    private val _received = MutableStateFlow<UiState<List<ReliefInviteResponse>>>(UiState.Idle)
    val received: StateFlow<UiState<List<ReliefInviteResponse>>> = _received.asStateFlow()

    // keep-last (the #143 shape, VM-side): the last successful list survives Loading/Error so
    // the section keeps rendering across reloads and composition re-entries.
    private val _lastReceived = MutableStateFlow<List<ReliefInviteResponse>?>(null)
    val lastReceived: StateFlow<List<ReliefInviteResponse>?> = _lastReceived.asStateFlow()

    private val _acceptResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val acceptResult: StateFlow<UiState<Unit>> = _acceptResult.asStateFlow()

    private val _declineResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val declineResult: StateFlow<UiState<Unit>> = _declineResult.asStateFlow()

    private val _candidates = MutableStateFlow<UiState<List<ReliefCandidateResponse>>>(UiState.Idle)
    val candidates: StateFlow<UiState<List<ReliefCandidateResponse>>> = _candidates.asStateFlow()

    private val _sentInvites = MutableStateFlow<UiState<List<ReliefInviteResponse>>>(UiState.Idle)
    val sentInvites: StateFlow<UiState<List<ReliefInviteResponse>>> = _sentInvites.asStateFlow()

    // keep-last for the inviter's sent list — the panel re-opens per branch card and a reload
    // must keep the previous list rendered (the #143 shape, VM-side). The list is BRANCH-scoped:
    // _sentBranch marks whose list _lastSent holds, so switching panels never bleeds branch A's
    // rows (with live Retract) under branch B (pass-1 HARD).
    private val _lastSent = MutableStateFlow<List<ReliefInviteResponse>?>(null)
    val lastSent: StateFlow<List<ReliefInviteResponse>?> = _lastSent.asStateFlow()

    private val _sentBranch = MutableStateFlow<String?>(null)
    val sentBranch: StateFlow<String?> = _sentBranch.asStateFlow()

    // Bumped per loadSent: a load that lands with a mismatched stamp belongs to an older branch
    // panel — committing it would serve branch A's rows under branch B (out-of-order response).
    private var sentStamp = 0L

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

    init {
        viewModelScope.launch {
            _received
                .filterIsInstance<UiState.Success<List<ReliefInviteResponse>>>()
                .collect { state -> _lastReceived.value = state.data }
        }
        viewModelScope.launch {
            _sentInvites
                .filterIsInstance<UiState.Success<List<ReliefInviteResponse>>>()
                .collect { state -> _lastSent.value = state.data }
        }
    }

    fun loadReceived(): Job {
        if (_received.value is UiState.Loading) return Job()
        val stamp = actionStamp
        return handler.launch(
            state = _received,
            operation = "loadReceived",
            endpoint = "GET /api/relief-invites",
            block = { apiClient.httpClient.get("/api/relief-invites") },
            transform = { response ->
                val body = response.body<List<ReliefInviteResponse>>()
                if (stamp != actionStamp) {
                    // An accept/decline landed while the load was in flight — committing the
                    // snapshot would resurrect the resolved row (the #141 resurrect class).
                    // Read the CURRENT _received Success first (pass-2 HARD): the mirror
                    // (_lastReceived) updates async, so a stale load landing right after an
                    // action could otherwise resurrect the row from the pre-action mirror
                    // until the re-issued load lands.
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
            transform = {
                actionStamp++
                removeReceived(inviteId)
                Unit
            },
        )

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
        // launch (the guard would let branch A's in-flight load swallow branch B's), and
        // _sentBranch flips only at COMMIT (pass-2 HARD) — the gate
        // `sentBranch == panelBranch` must never hold while the keep-last slot still holds
        // another branch's rows. Stale in-flight responses are stamped out below.
        val stamp = ++sentStamp
        return handler.launch(
            state = _sentInvites,
            operation = "loadSent",
            endpoint = "GET /api/branches/$branchId/relief-invites",
            block = { apiClient.httpClient.get("/api/branches/$branchId/relief-invites") },
            transform = { response ->
                val body = response.body<List<ReliefInviteResponse>>()
                if (stamp != sentStamp) {
                    // A newer panel load launched while this one was in flight — substituting
                    // the current list keeps the stale branch's rows from committing under the
                    // new branch (the #141 substitution pattern). _sentBranch is NOT touched:
                    // the substituted list belongs to whichever branch committed last.
                    currentSentList() ?: emptyList()
                } else {
                    // Commit-stamp: the branch label flips together with the committed body —
                    // the screen gate can then trust that a passing gate means the rendered
                    // list IS this panel's.
                    _sentBranch.value = branchId
                    body
                }
            },
        )
    }

    private fun currentSentList(): List<ReliefInviteResponse>? =
        (_sentInvites.value as? UiState.Success)?.data ?: _lastSent.value

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

    private fun currentReceivedList(): List<ReliefInviteResponse>? =
        (_received.value as? UiState.Success)?.data ?: _lastReceived.value

    private fun removeReceived(inviteId: String) {
        // Decrement only when the row actually left a KNOWN list: with no list loaded the badge
        // baseline is the poller's count, and decrementing against an unknown list would corrupt
        // it — the 60s poll overwrite self-corrects (the unread markRead precedent).
        val current = _received.value
        val list = (current as? UiState.Success)?.data ?: _lastReceived.value ?: return
        val remaining = list.filterNot { it.id == inviteId }
        if (remaining.size == list.size) return
        _received.value = UiState.Success(remaining)
        NotificationState.decrementInvites()
    }
}
