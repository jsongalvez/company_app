package com.companyb.companyapp.session.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.StatelessHooks
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientMutation
import com.companyb.companyapp.client.ClientPickerApi
import com.companyb.companyapp.client.ClientSearcher
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.session.AddSessionConcernRequest
import com.companyb.companyapp.contracts.session.ConcernResponse
import com.companyb.companyapp.contracts.session.CreateSessionRequest
import com.companyb.companyapp.contracts.session.SessionPreviewResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.extractApiErrorMessage
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #457 — the narrow picker boundary: the client directory needs search + select only,
 * never the form draft/preview/submit surface. #558 — extends the client-owned
 * [ClientPickerApi] so the dependency runs session → client, never the reverse.
 */
interface SessionClientPickerApi : ClientPickerApi

/**
 * #457 — the narrow form boundary: preview/type-price, concerns, practitioner, submit.
 * Screen layouts depend on this, not the concrete VM, so the VM's search internals,
 * draft holder, and entry-load helpers stay behind the seam. Compatibility preserved:
 * [SessionCreateViewModel] implements this and existing call sites pass it unchanged.
 *
 * #460 — 12 functions over the 11 budget, kept whole deliberately: the seam is one
 * narrow boundary (picker + form + submit); splitting it would scatter the contract
 * the screens depend on. Any further growth must split, not suppress again.
 */
@Suppress("TooManyFunctions")
interface SessionCreateFormApi : SessionClientPickerApi {
    val preview: StateFlow<UiState<SessionPreviewResponse>>
    val concerns: StateFlow<UiState<List<ConcernResponse>>>
    val selectedConcernIds: StateFlow<Set<String>>
    val members: StateFlow<UiState<List<BranchMemberResponse>>>
    val selectedPractitioner: StateFlow<BranchMemberResponse?>
    val createResult: StateFlow<UiState<SessionResponse>>
    val concernAddFailures: StateFlow<Int>
    val concernRetryState: StateFlow<UiState<Unit>>
    val toggleConcern: (String) -> Unit

    fun clearSelectedClient()

    fun setFinalPrice(value: String)

    fun setOtherConcerns(value: String)

    fun setRemarks(value: String)

    fun setBooked(value: Boolean)

    fun setNextAppointmentDate(value: String)

    fun selectPractitioner(member: BranchMemberResponse?)

    fun retryPreview()

    fun retryConcerns()

    fun retryMembers()

    fun createSession(
        finalPrice: String,
        remarks: String?,
        otherConcerns: String?,
        // Default: today's shipped walk-in shape — existing callers keep the same semantics.
        booking: BookingFields = BookingFields(isWalkIn = true, nextAppointmentDate = null),
    )

    fun retryConcernAdds()
}

/** #457 — the single submission-lock predicate: Loading or Success locks the form. */
fun isSessionCreateLocked(result: UiState<*>): Boolean = result is UiState.Loading || result is UiState.Success

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
@Suppress("TooManyFunctions")
class SessionCreateViewModel(
    private val apiClient: ApiClient,
    private val branchId: String,
) : ViewModel(),
    SessionCreateFormApi {
    private val handler = ApiCallHandler(viewModelScope, "SessionCreateVM")

    // --- Client picker: keep-last debounced search, entry-scoped. ---
    private val clientSearcher = ClientSearcher(apiClient, viewModelScope, "SessionCreateVM")
    override val searchResults: StateFlow<UiState<List<ClientResponse>>> = clientSearcher.state
    val freshestResults: StateFlow<List<ClientResponse>?> = clientSearcher.freshest
    override val query: StateFlow<String> = clientSearcher.query

    override val onQueryChange: (String) -> Unit = clientSearcher::onQueryChange
    override val retrySearch: () -> Unit = clientSearcher::retrySearch

    // --- Selected client + preview ---

    private val _selectedClient = MutableStateFlow<ClientResponse?>(null)
    val selectedClient: StateFlow<ClientResponse?> = _selectedClient.asStateFlow()

    private val _draft = MutableStateFlow(SessionCreateDraft())
    val draft: StateFlow<SessionCreateDraft> = _draft.asStateFlow()

    private val _preview = MutableStateFlow<UiState<SessionPreviewResponse>>(UiState.Idle)
    override val preview: StateFlow<UiState<SessionPreviewResponse>> = _preview.asStateFlow()

    /**
     * #405 review fix — the preview load is keep-last via structured cancellation: a
     * superseded body can never land and paint a previous client's type/price onto the newly
     * selected one.
     */
    private var previewJob: Job? = null
    private var lastAppliedMutation: ClientMutation? = null

    /** Selects a picker hit or a just-created client; the preview drives type + price display. */
    override fun selectClient(client: ClientResponse) {
        if (isSubmissionLocked()) return
        if (_createResult.value is UiState.Error) _createResult.value = UiState.Idle
        if (_selectedClient.value?.id != client.id) {
            _draft.update { it.copy(finalPrice = "", finalPriceEdited = false) }
        }
        _selectedClient.value = client
        loadPreview(client.id)
    }

    fun includeClientInSearchResults(client: ClientResponse) {
        clientSearcher.include(client)
    }

    override fun applyClientMutation(mutation: ClientMutation) {
        if (_selectedClient.value?.id != mutation.clientId || isSubmissionLocked()) return
        if (lastAppliedMutation == mutation) return
        val client = mutation.client
        if (client == null) {
            clientSearcher.remove(mutation.clientId)
            clearSelectedClient()
        } else {
            _selectedClient.value = client
            clientSearcher.replace(client)
        }
        lastAppliedMutation = mutation
    }

    override fun retryPreview() {
        if (isSubmissionLocked()) return
        _selectedClient.value?.let { loadPreview(it.id) }
    }

    /** #348 — the picker's "Change" action: selection dropped, preview back to Idle. */
    override fun clearSelectedClient() {
        if (isSubmissionLocked()) return
        _createResult.value = UiState.Idle
        _selectedClient.value = null
        _draft.update { it.copy(finalPrice = "", finalPriceEdited = false) }
        previewJob?.cancel()
        previewJob = null
        _preview.value = UiState.Idle
    }

    override fun setFinalPrice(value: String) {
        if (isSubmissionLocked()) return
        _draft.update { it.copy(finalPrice = value, finalPriceEdited = true) }
    }

    override fun setOtherConcerns(value: String) {
        if (isSubmissionLocked()) return
        _draft.update { it.copy(otherConcerns = value) }
    }

    override fun setRemarks(value: String) {
        if (isSubmissionLocked()) return
        _draft.update { it.copy(remarks = value) }
    }

    override fun setBooked(value: Boolean) {
        if (isSubmissionLocked()) return
        _draft.update {
            it.copy(
                isBooked = value,
                nextAppointmentDate = if (value) it.nextAppointmentDate else "",
            )
        }
    }

    override fun setNextAppointmentDate(value: String) {
        if (isSubmissionLocked()) return
        _draft.update { it.copy(nextAppointmentDate = value) }
    }

    /** Applies server preview defaults without overwriting an explicit price draft. */
    fun applyPreviewPrice(preview: SessionPreviewResponse?) {
        if (preview == null || isSubmissionLocked()) return
        _draft.update { draft ->
            when {
                preview.sessionType == SessionType.MEDICAL_MISSION -> {
                    draft.copy(finalPrice = "0", finalPriceEdited = false)
                }

                !draft.finalPriceEdited -> {
                    draft.copy(finalPrice = preview.basePrice)
                }

                else -> {
                    draft
                }
            }
        }
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
    override val concerns: StateFlow<UiState<List<ConcernResponse>>> = _concerns.asStateFlow()

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

    override fun retryConcerns() {
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
    private val concernPoster = ConcernPoster(apiClient, viewModelScope)
    override val selectedConcernIds: StateFlow<Set<String>> = concernPoster.selectedIds

    override val toggleConcern: (String) -> Unit = { concernId ->
        if (!isSubmissionLocked()) concernPoster.toggle(concernId)
    }

    // --- Requested practitioner (#366): own-branch member picker, optional end-to-end. ---

    private val _members = MutableStateFlow<UiState<List<BranchMemberResponse>>>(UiState.Idle)
    override val members: StateFlow<UiState<List<BranchMemberResponse>>> = _members.asStateFlow()

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

    override fun retryMembers() {
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
    override val selectedPractitioner: StateFlow<BranchMemberResponse?> = _selectedPractitioner.asStateFlow()

    override fun selectPractitioner(member: BranchMemberResponse?) {
        if (isSubmissionLocked()) return
        _selectedPractitioner.value = member
    }

    // --- Submit ---

    private val _createResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    override val createResult: StateFlow<UiState<SessionResponse>> = _createResult.asStateFlow()

    /**
     * Concern ids whose POST failed after the session itself was created. The session EXISTS at
     * that point — the entry keeps the user present and offers retry or explicit continuation
     * rather than pretending the whole submit failed.
     */
    override val concernAddFailures: StateFlow<Int> = concernPoster.failures.asStateFlow()

    private val _concernRetryState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    override val concernRetryState: StateFlow<UiState<Unit>> = _concernRetryState.asStateFlow()
    private var createdSessionId: String? = null
    private var concernRetryJob: Job? = null

    override fun createSession(
        finalPrice: String,
        remarks: String?,
        otherConcerns: String?,
        booking: BookingFields,
    ) {
        val client = _selectedClient.value ?: return
        if (isSubmissionLocked()) return
        val requestedPractitionerId = _selectedPractitioner.value?.id
        val concernIds = concernPoster.selectedIds.value.toSet()
        createdSessionId = null
        _concernRetryState.value = UiState.Idle
        _createResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
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
                                requestedPractitionerId = requestedPractitionerId,
                                finalPrice = finalPrice,
                                remarks = remarks?.trim()?.ifBlank { null },
                                otherConcerns = otherConcerns?.trim()?.ifBlank { null },
                                nextAppointmentDate = booking.nextAppointmentDate,
                            ),
                        )
                    }
                },
                transform = { response ->
                    val session = response.body<SessionResponse>()
                    createdSessionId = session.id
                    concernPoster.postSelected(session.id, concernIds)
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
            ),
        )
    }

    override fun retryConcernAdds() {
        val sessionId = createdSessionId ?: return
        if (_createResult.value !is UiState.Success ||
            concernPoster.failedIds.value.isEmpty() ||
            _concernRetryState.value is UiState.Loading
        ) {
            return
        }
        _concernRetryState.value = UiState.Loading
        concernRetryJob?.cancel()
        concernRetryJob =
            viewModelScope
                .launch {
                    concernPoster.retryFailed(sessionId)
                    _concernRetryState.value =
                        if (concernPoster.failedIds.value.isEmpty()) {
                            UiState.Success(Unit)
                        } else {
                            UiState.Error(
                                "Could not add ${concernPoster.failedIds.value.size} concern(s). " +
                                    "Try again or continue without them.",
                            )
                        }
                }.also { job ->
                    job.invokeOnCompletion { cause ->
                        if (cause is CancellationException && _concernRetryState.value is UiState.Loading) {
                            _concernRetryState.value = UiState.Idle
                        }
                    }
                }
    }

    private fun isSubmissionLocked(): Boolean = isSessionCreateLocked(_createResult.value)
}

/**
 * #423 — the booking half of the create request, shaped once and shaped pure so desktopTest
 * can pin the gating: a walk-in sends no booking fields (BR §Clients "treated identically
 * once started"); a booked session leaves `bookedAt` server-owned and carries the optional ISO
 * `yyyy-MM-dd` next-appointment date.
 * `null` return = booked with an unparseable date draft — submit stays disabled and the
 * screen surfaces the inline error.
 */
internal fun bookingFields(
    isBooked: Boolean,
    nextAppointmentDraft: String,
): BookingFields? {
    if (!isBooked) return BookingFields(isWalkIn = true, nextAppointmentDate = null)
    val trimmed = nextAppointmentDraft.trim()
    val date =
        trimmed.takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()?.toString() ?: return null
        }
    return BookingFields(isWalkIn = false, nextAppointmentDate = date)
}

/** The booking half of a create-session request (#423); see [bookingFields]. */
data class BookingFields(
    val isWalkIn: Boolean,
    val nextAppointmentDate: String?,
)

/** Session-create fields held by the entry-scoped VM so profile navigation preserves the draft. */
data class SessionCreateDraft(
    val finalPrice: String = "",
    val finalPriceEdited: Boolean = false,
    val otherConcerns: String = "",
    val remarks: String = "",
    val isBooked: Boolean = false,
    val nextAppointmentDate: String = "",
)

/**
 * Owns the concern multi-select (#348): the picked ids and the posts onto the created session.
 * Failed ids are retained — the session exists by then, so the screen can retry or explicitly
 * continue without failed links.
 */
private class ConcernPoster(
    private val apiClient: ApiClient,
    scope: CoroutineScope,
) {
    private val handler = ApiCallHandler(scope, "SessionCreateVM")
    val failures = MutableStateFlow(0)
    val failedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggle(concernId: String) {
        _selectedIds.update { selected ->
            if (concernId in selected) selected - concernId else selected + concernId
        }
    }

    suspend fun postSelected(
        sessionId: String,
        ids: Set<String>,
    ) = coroutineScope { post(sessionId, ids, this) }

    suspend fun retryFailed(sessionId: String) = coroutineScope { post(sessionId, failedIds.value, this) }

    private suspend fun post(
        sessionId: String,
        ids: Set<String>,
        requestScope: CoroutineScope,
    ) {
        val failed = mutableSetOf<String>()
        ids.forEach { concernId ->
            var requestFailed = false
            val requestJob =
                handler
                    .launchStateless(
                        operation = "addConcern",
                        endpoint = "POST /api/sessions/$sessionId/concerns",
                        block = {
                            apiClient.httpClient.post(ApiRoutes.sessionConcerns(sessionId)) {
                                setBody(AddSessionConcernRequest(concernId = concernId))
                            }
                        },
                        transform = {},
                        hooks =
                            StatelessHooks(
                                scope = requestScope,
                                onNonSuccess = { requestFailed = true },
                                onError = { requestFailed = true },
                            ),
                    )
            requestJob.join()
            if (requestJob.isCancelled) requestFailed = true
            if (requestFailed) failed += concernId
        }
        failedIds.value = failed.toSet()
        failures.value = failed.size
    }
}
