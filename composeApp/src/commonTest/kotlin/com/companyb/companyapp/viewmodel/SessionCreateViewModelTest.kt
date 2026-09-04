package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.ClientMutation
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * #348 — the SessionCreate VM: the debounced client search (the ClientViewModel D2 port),
 * the selectClient→preview read against the NEW branch session-preview endpoint, the walk-in
 * create POST reaching Success, the Loading double-submit guard, and the inline 409 surfacing.
 *
 * MockEngine handler order: specific paths BEFORE generic endsWith branches; every arm asserts
 * method + exact path else error() — a mis-routed call must fail loudly, not silently 404.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCreateViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.advanceTimeByAndRun(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun onQueryChange_debounce_fires_search_with_query_parameter() =
        runTest(testScheduler) {
            val queries = mutableListOf<String>()
            val vm = SessionCreateViewModel(mockApiClient(searchHandler(queries)), BRANCH_ID)

            vm.onQueryChange("jo")
            advanceTimeByAndRun(299)
            assertEquals(expected = emptyList(), actual = queries)
            advanceTimeByAndRun(1)

            assertEquals(expected = listOf("jo"), actual = queries)
            assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
        }

    @Test
    fun selectClient_loads_branch_session_preview_for_the_selected_client() =
        runTest(testScheduler) {
            val previewQueries = mutableListOf<String>()
            val vm =
                SessionCreateViewModel(
                    mockApiClient(
                        handler(previewClientIds = previewQueries),
                    ),
                    BRANCH_ID,
                )

            vm.selectClient(client("c1"))
            runCurrent()

            assertEquals(expected = listOf("c1"), actual = previewQueries)
            val state = assertIs<UiState.Success<SessionPreviewResponse>>(vm.preview.value)
            assertEquals(expected = "REGULAR", actual = state.data.sessionType.name)
            assertEquals(expected = "250.00", actual = state.data.basePrice)
        }

    @Test
    fun selectClient_superseded_preview_landing_never_commits_stale_body() =
        runTest(testScheduler) {
            // #405 review fix pin — A's preview is still in flight when B is selected: the
            // newer select cancels the held load (the ClientSearcher structured-cancellation
            // guard), so A's mission body can never land and paint onto B.
            var previewCount = 0
            val sequencedPreview: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview"
                ) {
                    previewCount++
                    if (previewCount == 1) {
                        // Virtualized hold: A's body would land only after B has committed.
                        withContext(StandardTestDispatcher(testScheduler)) { delay(PREVIEW_HOLD_MS) }
                        jsonRespond(status = HttpStatusCode.OK, body = MISSION_PREVIEW_JSON)
                    } else {
                        jsonRespond(status = HttpStatusCode.OK, body = PREVIEW_JSON)
                    }
                } else {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
            val vm = SessionCreateViewModel(mockApiClient(sequencedPreview), BRANCH_ID)

            vm.selectClient(client("c1"))
            runCurrent()
            assertIs<UiState.Loading>(vm.preview.value)

            vm.selectClient(client("c2"))
            runCurrent()
            val fresh = assertIs<UiState.Success<SessionPreviewResponse>>(vm.preview.value)
            assertEquals(expected = "REGULAR", actual = fresh.data.sessionType.name)

            // The held pre-B snapshot never lands — cancellation killed it at its suspension,
            // and no re-issue GET exists: B's truth simply stands.
            advanceTimeByAndRun(PREVIEW_HOLD_MS)
            advanceTimeByAndRun(PREVIEW_HOLD_MS)
            val converged = assertIs<UiState.Success<SessionPreviewResponse>>(vm.preview.value)
            assertEquals(expected = "REGULAR", actual = converged.data.sessionType.name)
        }

    @Test
    fun clearSelectedClient_resets_selection_and_preview_to_idle() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(handler()), BRANCH_ID)

            vm.selectClient(client("c1"))
            runCurrent()
            vm.clearSelectedClient()
            runCurrent()

            assertEquals(expected = null, actual = vm.selectedClient.value)
            assertIs<UiState.Idle>(vm.preview.value)
        }

    @Test
    fun clearSelectedClient_preserves_draft_except_client_specific_price() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(handler()), BRANCH_ID)

            vm.setFinalPrice("900")
            vm.setOtherConcerns("Shoulder history")
            vm.setRemarks("Keep this note")
            vm.setBooked(true)
            vm.setNextAppointmentDate("2026-09-01")
            vm.clearSelectedClient()

            assertEquals(
                expected =
                    SessionCreateDraft(
                        finalPrice = "",
                        otherConcerns = "Shoulder history",
                        remarks = "Keep this note",
                        isBooked = true,
                        nextAppointmentDate = "2026-09-01",
                    ),
                actual = vm.draft.value,
            )
        }

    @Test
    fun profile_mutation_refreshes_selected_client_and_anonymization_clears_it() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(handler()), BRANCH_ID)

            vm.selectClient(client("c1"))
            runCurrent()
            val updated = client("c1").copy(firstName = "Jane", sessionCount = 4)
            vm.applyClientMutation(ClientMutation(clientId = "c1", client = updated))

            assertEquals(expected = updated, actual = vm.selectedClient.value)

            vm.applyClientMutation(ClientMutation(clientId = "c1", client = null))

            assertEquals(expected = null, actual = vm.selectedClient.value)
            assertIs<UiState.Idle>(vm.preview.value)
        }

    @Test
    fun selecting_another_directory_client_resets_client_specific_price_draft() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(handler()), BRANCH_ID)

            vm.selectClient(client("c1"))
            runCurrent()
            vm.setFinalPrice("900")
            vm.selectClient(client("c2"))

            assertEquals(expected = "", actual = vm.draft.value.finalPrice)
        }

    @Test
    fun preview_replaces_automatic_price_but_keeps_manual_price() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(handler()), BRANCH_ID)
            val firstPreview = SessionPreviewResponse(SessionType.REGULAR, "250.00")
            val secondPreview = SessionPreviewResponse(SessionType.SECOND_SESSION, "400.00")

            vm.applyPreviewPrice(firstPreview)
            vm.applyPreviewPrice(secondPreview)
            assertEquals(expected = "400.00", actual = vm.draft.value.finalPrice)

            vm.setFinalPrice("900")
            vm.applyPreviewPrice(firstPreview)

            assertEquals(expected = "900", actual = vm.draft.value.finalPrice)

            vm.setFinalPrice("")
            vm.applyPreviewPrice(secondPreview)

            assertEquals(expected = "", actual = vm.draft.value.finalPrice)
        }

    @Test
    fun includeCreatedClient_updates_existing_picker_results() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(searchHandler(mutableListOf())), BRANCH_ID)

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.includeClientInSearchResults(client("c9"))

            val results = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value).data
            assertEquals(expected = listOf("c9", "c1"), actual = results.map { it.id })
        }

    @Test
    fun client_search_instances_keep_query_and_results_independent() =
        runTest(testScheduler) {
            val firstQueries = mutableListOf<String>()
            val secondQueries = mutableListOf<String>()
            val first = SessionCreateViewModel(mockApiClient(searchHandler(firstQueries)), BRANCH_ID)
            val second = SessionCreateViewModel(mockApiClient(searchHandler(secondQueries)), BRANCH_ID)

            first.onQueryChange("jo")
            advanceTimeByAndRun(300)

            assertEquals(expected = "jo", actual = first.query.value)
            assertEquals(expected = "", actual = second.query.value)
            assertEquals(expected = listOf("jo"), actual = firstQueries)
            assertEquals(expected = emptyList(), actual = secondQueries)
            assertIs<UiState.Idle>(second.searchResults.value)
        }

    @Test
    fun createSession_posts_walkin_create_and_reaches_success() =
        runTest(testScheduler) {
            val bodies = mutableListOf<kotlin.Pair<String, Boolean>>()
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                val text = (request.body as io.ktor.http.content.TextContent).text
                                // The idempotency key is a fresh UUID (BR §390–392); walk-ins
                                // start PENDING (BR §129).
                                bodies.add(text to text.contains("\"isWalkIn\":true"))
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )
            vm.selectClient(client("c1"))
            runCurrent()

            vm.createSession(
                finalPrice = "300.00",
                remarks = " ok ",
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            assertEquals(expected = 1, actual = bodies.size)
            assertTrue(bodies.single().second)
            assertTrue(bodies.single().first.contains("\"finalPrice\":\"300.00\""))
            assertTrue(bodies.single().first.contains("\"remarks\":\"ok\""))
            val state = assertIs<UiState.Success<SessionResponse>>(vm.createResult.value)
            assertEquals(expected = "s1", actual = state.data.id)

            vm.createSession(
                finalPrice = "300.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()
            assertEquals(expected = 1, actual = bodies.size)
        }

    @Test
    fun createSession_adds_selected_concerns_after_success() =
        runTest(testScheduler) {
            val concernPosts = mutableListOf<String>()
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            request.method == HttpMethod.Post &&
                                request.url.encodedPath == "/api/sessions/s1/concerns" -> {
                                concernPosts.add((request.body as io.ktor.http.content.TextContent).text)
                                jsonRespond(status = HttpStatusCode.OK, body = "{}")
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )
            vm.selectClient(client("c1"))
            runCurrent()
            vm.toggleConcern("con1")

            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            assertEquals(expected = 1, actual = concernPosts.size)
            assertTrue(concernPosts.single().contains("\"concernId\":\"con1\""))
            assertEquals(expected = 0, actual = vm.concernAddFailures.value)
        }

    @Test
    fun failed_concern_adds_are_visible_and_retryable_without_duplicate_session_create() =
        runTest(testScheduler) {
            var sessionPosts = 0
            var concernPosts = 0
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                sessionPosts++
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            request.method == HttpMethod.Post &&
                                request.url.encodedPath == "/api/sessions/s1/concerns" -> {
                                concernPosts++
                                val status = if (concernPosts == 1) HttpStatusCode.BadRequest else HttpStatusCode.OK
                                jsonRespond(status = status, body = "{}")
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )

            vm.selectClient(client("c1"))
            runCurrent()
            vm.toggleConcern("con1")
            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            assertEquals(expected = 1, actual = sessionPosts)
            assertEquals(expected = 1, actual = vm.concernAddFailures.value)
            assertIs<UiState.Success<SessionResponse>>(vm.createResult.value)

            vm.retryConcernAdds()
            runCurrent()

            assertEquals(expected = 1, actual = sessionPosts)
            assertEquals(expected = 2, actual = concernPosts)
            assertEquals(expected = 0, actual = vm.concernAddFailures.value)
            assertIs<UiState.Success<Unit>>(vm.concernRetryState.value)
        }

    @Test
    fun createSession_double_submit_while_loading_fires_one_post() =
        runTest(testScheduler) {
            var posts = 0
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                posts++
                                delay(10_000)
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )
            vm.selectClient(client("c1"))
            runCurrent()

            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            advanceTimeByAndRun(20_000)

            assertEquals(expected = 1, actual = posts)
        }

    @Test
    fun selecting_client_while_create_is_loading_keeps_original_client() =
        runTest(testScheduler) {
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = PREVIEW_JSON)
                            }

                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                delay(10_000)
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )

            vm.selectClient(client("c1"))
            runCurrent()
            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            vm.selectClient(client("c2"))
            assertEquals(expected = "c1", actual = vm.selectedClient.value?.id)
        }

    @Test
    fun createSession_snapshots_practitioner_and_concerns_before_post_lands() =
        runTest(testScheduler) {
            val releaseSessionPost = CompletableDeferred<Unit>()
            var sessionBody = ""
            var concernBody = ""
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = PREVIEW_JSON)
                            }

                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                sessionBody = (request.body as io.ktor.http.content.TextContent).text
                                releaseSessionPost.await()
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            request.method == HttpMethod.Post &&
                                request.url.encodedPath == "/api/sessions/s1/concerns" -> {
                                concernBody = (request.body as io.ktor.http.content.TextContent).text
                                jsonRespond(status = HttpStatusCode.OK, body = "{}")
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )

            vm.selectClient(client("c1"))
            runCurrent()
            vm.selectPractitioner(BranchMemberResponse("p1", "First"))
            vm.toggleConcern("con1")
            assertEquals(expected = setOf("con1"), actual = vm.selectedConcernIds.value)
            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            vm.selectPractitioner(BranchMemberResponse("p2", "Second"))
            vm.toggleConcern("con2")
            releaseSessionPost.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertTrue(sessionBody.contains("\"requestedPractitionerId\":\"p1\""))
            assertTrue(!sessionBody.contains("\"requestedPractitionerId\":\"p2\""))
            assertTrue(concernBody.contains("\"concernId\":\"con1\""))
            assertTrue(!concernBody.contains("\"concernId\":\"con2\""))
            assertIs<UiState.Success<SessionResponse>>(vm.createResult.value)
        }

    @Test
    fun createSession_409_one_active_message_surfaces_inline() =
        runTest(testScheduler) {
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                jsonRespond(
                                    status = HttpStatusCode.Conflict,
                                    body = """{"error":"This client already has an active session"}""",
                                )
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )
            vm.selectClient(client("c1"))
            runCurrent()

            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()

            val state = assertIs<UiState.Error>(vm.createResult.value)
            assertEquals(
                expected = "This client already has an active session",
                actual = state.message,
            )
        }

    @Test
    fun loadMembers_fetches_directory_and_selection_flows_into_create_body() =
        runTest(testScheduler) {
            val bodies = mutableListOf<String>()
            var membersRequests = 0
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/branches/$BRANCH_ID/members" -> {
                                membersRequests++
                                jsonRespond(
                                    status = HttpStatusCode.OK,
                                    body = """[{"id":"p1","displayName":"Test usera"}]""",
                                )
                            }

                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                bodies.add((request.body as io.ktor.http.content.TextContent).text)
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )

            vm.loadMembers()
            runCurrent()
            val state = assertIs<UiState.Success<List<BranchMemberResponse>>>(vm.members.value)
            assertEquals(expected = "Test usera", actual = state.data.single().displayName)

            // Selection flows into the POST; a fresh session defaults to no practitioner.
            vm.selectClient(client("c1"))
            runCurrent()
            vm.selectPractitioner(state.data.single())
            vm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()
            assertTrue(bodies.single().contains("\"requestedPractitionerId\":\"p1\""))

            var secondBody = ""
            val secondVm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = PREVIEW_JSON)
                            }

                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                                secondBody = (request.body as io.ktor.http.content.TextContent).text
                                jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )
            secondVm.selectClient(client("c1"))
            runCurrent()
            secondVm.createSession(
                finalPrice = "250.00",
                remarks = null,
                otherConcerns = null,
                booking = BookingFields(isWalkIn = true, nextAppointmentDate = null),
            )
            runCurrent()
            assertTrue(secondBody.contains("\"requestedPractitionerId\":null"))
            assertEquals(expected = 1, actual = membersRequests)
        }

    @Test
    fun retryMembers_refetches_after_error() =
        runTest(testScheduler) {
            var fail = true
            val vm =
                SessionCreateViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/branches/$BRANCH_ID/members" -> {
                                if (fail) {
                                    jsonRespond(status = HttpStatusCode.InternalServerError, body = "{}")
                                } else {
                                    jsonRespond(status = HttpStatusCode.OK, body = "[]")
                                }
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                    BRANCH_ID,
                )

            vm.loadMembers()
            // 5xx trips the ApiClient's HttpRequestRetry; its backoff runs on virtual time,
            // so the terminal Error lands only once the retry delays are advanced through.
            testScheduler.advanceUntilIdle()
            assertIs<UiState.Error>(vm.members.value)

            fail = false
            vm.retryMembers()
            runCurrent()
            assertIs<UiState.Success<List<BranchMemberResponse>>>(vm.members.value)
        }

    // --- handlers ---

    private fun searchHandler(queries: MutableList<String>): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/clients" -> {
                    queries.add(request.url.parameters["q"].orEmpty())
                    jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
        }

    private fun handler(
        previewStatus: HttpStatusCode = HttpStatusCode.OK,
        previewClientIds: MutableList<String>? = null,
    ): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview" -> {
                    previewClientIds?.add(request.url.parameters["clientId"].orEmpty())
                    jsonRespond(status = previewStatus, body = PREVIEW_JSON)
                }

                request.method == HttpMethod.Get && request.url.encodedPath == "/api/clients" -> {
                    jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                }

                request.method == HttpMethod.Get && request.url.encodedPath == "/api/concerns" -> {
                    jsonRespond(status = HttpStatusCode.OK, body = CONCERNS_JSON)
                }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/sessions" -> {
                    jsonRespond(status = HttpStatusCode.OK, body = SESSION_JSON)
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.endsWith("/concerns") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = "{}")
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
        }

    private fun MockRequestHandleScope.jsonRespond(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun client(id: String): ClientResponse =
        ClientResponse(
            id = id,
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = Gender.M,
            age = 30,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
            sessionCount = 0,
        )

    private companion object {
        const val BRANCH_ID = "11111111-1111-1111-1111-111111111111"

        const val PREVIEW_HOLD_MS = 5_000L

        const val SEARCH_JSON =
            """[
                {"id":"c1","firstName":"John","lastName":"Doe","middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"M","age":30,"systolicBp":null,"diastolicBp":null,"medicalConditions":null,"sessionCount":0}
            ]"""

        const val PREVIEW_JSON = """{"sessionType":"REGULAR","basePrice":"250.00"}"""

        const val MISSION_PREVIEW_JSON = """{"sessionType":"MEDICAL_MISSION","basePrice":"0.00"}"""

        const val CONCERNS_JSON =
            """[{"id":"con1","label":"Headache","createdBy":null,"createdAt":null}]"""

        const val SESSION_JSON =
            """{"id":"s1","clientId":"c1","branchDayId":"bd1","requestedPractitionerId":null,"sessionType":"REGULAR","isWalkIn":true,"sessionStatus":"PENDING","basePrice":"250.00","finalPrice":"250.00","remarks":null,"otherConcerns":null,"bookedAt":null,"nextAppointmentDate":null,"version":0,"concerns":[]}"""
    }
}
