package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #348 — SessionCreate flow: find the client (debounced search, the ClientViewModel D2 port),
 * create one on the spot via [ClientCreateDialog] when search comes up empty, show the
 * server-computed session type + defaulted base price (the #348 preview read — never replicated
 * client-side), pick concerns, and submit the PENDING walk-in create.
 *
 * The branch is fixed at entry (constructor) from SessionState — the same branch context every
 * other entry-scoped screen bakes in.
 */
@OptIn(ExperimentalUuidApi::class)
class SessionCreateViewModel(
    private val apiClient: ApiClient,
    private val branchId: String,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionCreateVM")

    // --- Client picker: keep-last debounced search (the D2/#162 port, entry-scoped). ---
    // Search lives in [ClientSearcher]; the flows surface as properties (the detekt function
    // budget — same shape as ConcernPoster below).
    private val clientSearcher = ClientSearcher(apiClient, viewModelScope)
    val searchResults: StateFlow<UiState<List<ClientResponse>>> = clientSearcher.state
    val freshestResults: StateFlow<List<ClientResponse>?> = clientSearcher.freshest
    val query: StateFlow<String> = clientSearcher.query

    val onQueryChange: (String) -> Unit = clientSearcher::onQueryChange
    val retrySearch: () -> Unit = clientSearcher::retrySearch

    // --- Selected client + preview ---

    private val _selectedClient = MutableStateFlow<ClientResponse?>(null)
    val selectedClient: StateFlow<ClientResponse?> = _selectedClient.asStateFlow()

    private val _preview = MutableStateFlow<UiState<SessionPreviewResponse>>(UiState.Idle)
    val preview: StateFlow<UiState<SessionPreviewResponse>> = _preview.asStateFlow()

    /**
     * #405 review fix — the preview load is keep-last via structured cancellation (the
     * [ClientSearcher] D2 guard, same file): a newer select cancels the in-flight load, so a
     * superseded body can never land and paint a previous client's type/price onto the newly
     * selected one.
     */
    private var previewJob: Job? = null

    /** Selects a picker hit or a just-created client; the preview drives type + price display. */
    fun selectClient(client: ClientResponse) {
        _selectedClient.value = client
        loadPreview(client.id)
    }

    fun retryPreview() {
        _selectedClient.value?.let { loadPreview(it.id) }
    }

    /** #348 — the picker's "Change" action: selection dropped, preview back to Idle. */
    fun clearSelectedClient() {
        _selectedClient.value = null
        previewJob?.cancel()
        previewJob = null
        _preview.value = UiState.Idle
    }

    private fun loadPreview(clientId: String) {
        previewJob?.cancel()
        _preview.value = UiState.Loading
        previewJob =
            handler.launch(
                state = _preview,
                operation = "loadPreview",
                endpoint = "GET /api/branches/$branchId/session-preview",
                block = {
                    apiClient.httpClient.get(ApiRoutes.branchSessionPreview(branchId)) {
                        parameter("clientId", clientId)
                    }
                },
                transform = { it.body() },
            )
    }

    // --- Concern multi-select ---

    private val _concerns = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val concerns: StateFlow<UiState<List<ConcernResponse>>> = _concerns.asStateFlow()

    // Retry-loop lesson: effects keyed on this state fire loads on Idle only; manual retry on Error.
    fun loadConcerns() {
        if (_concerns.value !is UiState.Idle) return
        _concerns.value = UiState.Loading
        handler.launch(
            state = _concerns,
            operation = "loadConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    fun retryConcerns() {
        if (_concerns.value is UiState.Loading) return
        _concerns.value = UiState.Loading
        handler.launch(
            state = _concerns,
            operation = "retryConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    // Concern selection + add-posting live in [ConcernPoster]; the flows surface as properties
    // (the detekt function budget — #348's VM grew past the class threshold).
    private val concernPoster = ConcernPoster(apiClient)
    val selectedConcernIds: StateFlow<Set<String>> = concernPoster.selectedIds

    val toggleConcern: (String) -> Unit = concernPoster::toggle

    // --- Requested practitioner (#366): own-branch member picker, optional end-to-end. ---

    private val _members = MutableStateFlow<UiState<List<BranchMemberResponse>>>(UiState.Idle)
    val members: StateFlow<UiState<List<BranchMemberResponse>>> = _members.asStateFlow()

    /** Idle-only entry load (the concerns retry-loop lesson); Error gets a manual retry. */
    fun loadMembers() {
        if (_members.value !is UiState.Idle) return
        _members.value = UiState.Loading
        handler.launch(
            state = _members,
            operation = "loadMembers",
            endpoint = "GET /api/branches/$branchId/members",
            block = { apiClient.httpClient.get(ApiRoutes.branchMembers(branchId)) },
            transform = { it.body() },
        )
    }

    fun retryMembers() {
        if (_members.value is UiState.Loading) return
        _members.value = UiState.Loading
        handler.launch(
            state = _members,
            operation = "retryMembers",
            endpoint = "GET /api/branches/$branchId/members",
            block = { apiClient.httpClient.get(ApiRoutes.branchMembers(branchId)) },
            transform = { it.body() },
        )
    }

    private val _selectedPractitioner = MutableStateFlow<BranchMemberResponse?>(null)
    val selectedPractitioner: StateFlow<BranchMemberResponse?> = _selectedPractitioner.asStateFlow()

    fun selectPractitioner(member: BranchMemberResponse?) {
        _selectedPractitioner.value = member
    }

    // --- Submit ---

    private val _createResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<SessionResponse>> = _createResult.asStateFlow()

    /**
     * Concern ids whose POST failed after the session itself was created. The session EXISTS at
     * that point — navigation proceeds and the detail screen shows server truth; failures are
     * surfaced inline rather than pretending the whole submit failed.
     */
    val concernAddFailures: StateFlow<Int> = concernPoster.failures.asStateFlow()

    fun createSession(
        finalPrice: String,
        remarks: String?,
        otherConcerns: String?,
        // Default: today's shipped walk-in shape — pre-#423 callers keep byte-identical requests.
        booking: BookingFields = BookingFields(isWalkIn = true, bookedAt = null, nextAppointmentDate = null),
    ) {
        val client = _selectedClient.value ?: return
        if (_createResult.value is UiState.Loading) return
        _createResult.value = UiState.Loading
        handler.launch(
            state = _createResult,
            operation = "createSession",
            endpoint = "POST /api/sessions",
            block = {
                apiClient.httpClient.post(ApiRoutes.SESSIONS) {
                    setBody(
                        CreateSessionRequest(
                            id = Uuid.random().toString(),
                            clientId = client.id,
                            branchId = branchId,
                            isWalkIn = booking.isWalkIn,
                            requestedPractitionerId =
                                _selectedPractitioner.value?.id,
                            finalPrice = finalPrice,
                            remarks = remarks?.trim()?.ifBlank { null },
                            otherConcerns = otherConcerns?.trim()?.ifBlank { null },
                            bookedAt = booking.bookedAt,
                            nextAppointmentDate = booking.nextAppointmentDate,
                        ),
                    )
                }
            },
            transform = { response ->
                val session = response.body<SessionResponse>()
                concernPoster.postSelected(session.id)
                session
            },
            onNonSuccess = { response ->
                // 409 carries the one-active-session rule's message; 400/403 surface inline too.
                val detail =
                    extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                _createResult.value =
                    UiState.Error(detail ?: "Create session failed: ${response.status.value}")
                true
            },
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_SEARCH_CHARS = 2
    }
}

/**
 * #423 — the booking half of the create request, shaped once and shaped pure so desktopTest
 * can pin the gating: a walk-in sends no booking fields (BR §Clients "treated identically
 * once started"); a booked session stamps `bookedAt` = now (client clock; the server re-parses
 * it authoritatively) and carries the optional ISO `yyyy-MM-dd` next-appointment date.
 * `null` return = booked with an unparseable date draft — submit stays disabled and the
 * screen surfaces the inline error.
 */
internal fun bookingFields(
    isBooked: Boolean,
    nextAppointmentDraft: String,
    now: Instant,
): BookingFields? {
    if (!isBooked) return BookingFields(isWalkIn = true, bookedAt = null, nextAppointmentDate = null)
    val trimmed = nextAppointmentDraft.trim()
    val date =
        trimmed.takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()?.toString() ?: return null
        }
    return BookingFields(isWalkIn = false, bookedAt = now.toString(), nextAppointmentDate = date)
}

/** The booking half of a create-session request (#423); see [bookingFields]. */
data class BookingFields(
    val isWalkIn: Boolean,
    val bookedAt: String?,
    val nextAppointmentDate: String?,
)

/**
 * The client-picker search (the D2/#162 port, entry-scoped): keep-last debounced query over
 * `GET /api/clients`. Extracted from [SessionCreateViewModel] for the detekt function budget
 * (the ConcernPoster precedent).
 */
private class ClientSearcher(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val handler = ApiCallHandler(scope, "SessionCreateVM")

    private val keptResults = KeepLast<List<ClientResponse>>(scope)
    val state: StateFlow<UiState<List<ClientResponse>>> = keptResults.state
    val freshest: StateFlow<List<ClientResponse>?> = keptResults.freshest

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null
    private var latestQuery: String = ""

    fun onQueryChange(query: String) {
        _query.value = query
        latestQuery = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) {
            keptResults.stateFlow.value = UiState.Idle
            return
        }
        searchJob =
            scope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                launchSearch(trimmed, this, "search called: query=$trimmed")
            }
    }

    fun retrySearch() {
        val trimmed = latestQuery.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) return
        searchJob?.cancel()
        searchJob =
            scope.launch {
                launchSearch(trimmed, this, "search retried: query=$trimmed")
            }
    }

    // Structured cancellation (the ClientViewModel D2 guard): each request is a child of the
    // caller's job, so a newer keystroke cancels the in-flight one and only the latest commits.
    private fun launchSearch(
        query: String,
        scope: CoroutineScope,
        entryMessage: String,
    ) {
        handler.launch(
            scope = scope,
            state = keptResults.stateFlow,
            operation = "search",
            endpoint = "GET /api/clients",
            entryMessage = entryMessage,
            block = {
                apiClient.httpClient.get(ApiRoutes.CLIENTS) { parameter("q", query) }
            },
            transform = { it.body() },
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_SEARCH_CHARS = 2
    }
}

/**
 * Owns the concern multi-select (#348): the picked ids and the posts onto the created session.
 * Failures only count — the session itself exists by then, so navigation proceeds and the
 * screen surfaces [failures] inline.
 */
private class ConcernPoster(
    private val apiClient: ApiClient,
) {
    val failures = MutableStateFlow(0)

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggle(concernId: String) {
        _selectedIds.update { selected ->
            if (concernId in selected) selected - concernId else selected + concernId
        }
    }

    suspend fun postSelected(sessionId: String) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        var failed = 0
        ids.forEach { concernId ->
            runCatching {
                val response =
                    apiClient.httpClient.post(ApiRoutes.sessionConcerns(sessionId)) {
                        setBody(AddSessionConcernRequest(concernId = concernId))
                    }
                if (!response.status.isSuccess()) failed++
            }.onFailure {
                failed++
            }
        }
        failures.value = failed
    }
}
