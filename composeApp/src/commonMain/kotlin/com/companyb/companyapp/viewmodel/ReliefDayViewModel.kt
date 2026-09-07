package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #358 — the relief deep-link destination: a branch day's request rows, addressed by
 * (branchId, date) exactly as a notification carries them. Bearer + row scoping on the
 * backend (members see every request, outsiders their own) — no capability gate client-side.
 *
 * #388 — the tapped branch's display name resolves from the existing branch-options read;
 * until (or unless) it lands the screen shows its generic "Branch" fallback. A failed
 * resolution is silent — it never blocks or duplicates the request list's own error state,
 * and [load] re-attempts it so Retry also retries the name.
 *
 * #401 — [load] also fetches the day's relief invites (separate leg): invite-sourced
 * notifications (accepted/declined/revoked/reminder) previously landed on this panel and
 * died as an empty day; now the same surface renders both entities' true states.
 */
class ReliefDayViewModel(
    private val apiClient: ApiClient,
    private val branchId: String,
    private val date: String,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReliefDayVM")

    private val _requests = MutableStateFlow<UiState<List<ReliefAccessResponse>>>(UiState.Idle)
    val requests: StateFlow<UiState<List<ReliefAccessResponse>>> = _requests.asStateFlow()

    // #401 — the day's relief invites, a separate leg: its failure degrades to an inline
    // retry strip and never touches the request list's own states (the #396 auxiliary-read
    // shape). Invite-sourced notifications previously tapped into a request-only panel and
    // died as "No relief activity on this day".
    private val _invites = MutableStateFlow<UiState<List<ReliefInviteResponse>>>(UiState.Idle)
    val invites: StateFlow<UiState<List<ReliefInviteResponse>>> = _invites.asStateFlow()

    private val _branchName = MutableStateFlow<String?>(null)
    val branchName: StateFlow<String?> = _branchName.asStateFlow()

    fun load() {
        handler.launch(
            state = _requests,
            operation = "loadReliefDay",
            endpoint = "GET /api/relief-access?branchId&date",
            block = { apiClient.httpClient.get(ApiRoutes.reliefAccessByBranchAndDate(branchId, date)) },
            transform = { it.body() },
        )
        handler.launch(
            state = _invites,
            operation = "loadReliefDayInvites",
            endpoint = "GET /api/branches/{branchId}/relief-invites/by-date",
            block = { apiClient.httpClient.get(ApiRoutes.branchReliefInvitesByDate(branchId, date)) },
            transform = { it.body() },
        )
        resolveBranchName()
    }

    private fun resolveBranchName() {
        if (_branchName.value != null) return
        handler.launchStateless(
            operation = "resolveBranchName",
            endpoint = "GET ${ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS}",
            block = { apiClient.httpClient.get(ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS) },
            transform = { response ->
                _branchName.value =
                    response
                        .body<List<ReliefBranchOptionResponse>>()
                        .firstOrNull { it.branchId == branchId }
                        ?.branchName
            },
        )
    }
}
