package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.ClientState
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
import kotlinx.coroutines.NonCancellable
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the #113 Clients build — the locked #99 decisions:
 *
 * - D2: 300ms debounce fires on ≥2 trimmed chars; typing before the debounce elapses cancels the
 *   pending search (only the latest fires); sub-2-char input returns to Idle with no request.
 * - D4: pessimistic per-field PATCH — success replaces the detail; failure → Error (inline error
 *   path); **403 silent exit** (state stays Idle, no Error); **409 reload + changed-elsewhere
 *   notice** (detail re-fetched, notice flag set).
 * - D1: anonymize 204 sets [ClientState.anonymizeNotice] for the search screen's snackbar.
 *
 * Uses the #93 handler-based MockEngine + `runTest(testScheduler)` virtual-time drain pattern
 * (see NotificationViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ClientState.clear()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        ClientState.clear()
    }

    @Test
    fun onQueryChange_below_two_chars_stays_idle_and_fires_no_request() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = ClientViewModel(mockApiClient(clientHandler(searchQueries = recorded)))

            vm.onQueryChange("j")
            runCurrent()
            advanceTimeByAndRun(100)
            advanceTimeByAndRun(300)

            assertIs<UiState.Idle>(vm.searchResults.value)
            assertEquals(expected = emptyList<String>(), actual = recorded)
        }

    @Test
    fun onQueryChange_blank_query_returns_to_idle() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)

            vm.onQueryChange("")
            runCurrent()

            assertIs<UiState.Idle>(vm.searchResults.value)
        }

    @Test
    fun onQueryChange_fires_search_after_300ms_debounce() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = ClientViewModel(mockApiClient(clientHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(299)
            assertEquals(expected = emptyList<String>(), actual = recorded)
            advanceTimeByAndRun(1)

            assertEquals(expected = listOf("jo"), actual = recorded)
            assertEquals(expected = "jo", actual = vm.lastFiredQuery.value)
            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
        }

    @Test
    fun lastFiredQuery_tracks_the_latest_fire_across_rapid_retyping() =
        runTest(testScheduler) {
            // The first request stays in flight (virtual-time delay): the second fire's Loading
            // emission is equal to the first's (data object — StateFlow suppresses it), so only
            // the fire point can record the second query — Loading observation would miss it.
            val recorded = mutableListOf<String>()
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                val q = request.url.parameters["q"].orEmpty()
                                recorded.add(q)
                                if (q == "jo") {
                                    delay(10_000)
                                }
                                jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            assertEquals(expected = "jo", actual = vm.lastFiredQuery.value)

            vm.onQueryChange("joh")
            advanceTimeByAndRun(300)
            assertEquals(expected = "joh", actual = vm.lastFiredQuery.value)
            assertEquals(expected = listOf("jo", "joh"), actual = recorded)
        }

    @Test
    fun onQueryChange_rapid_typing_cancels_pending_search_only_latest_fires() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = ClientViewModel(mockApiClient(clientHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(200)
            vm.onQueryChange("joh")
            advanceTimeByAndRun(200)
            vm.onQueryChange("john")
            advanceTimeByAndRun(300)

            assertEquals(expected = listOf("john"), actual = recorded)
            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
        }

    @Test
    fun onQueryChange_newer_input_cancels_inflight_request_stale_response_never_commits() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                val q = request.url.parameters["q"].orEmpty()
                                recorded.add(q)
                                // The FIRST query's request stays in flight (virtual-time delay) —
                                // exactly the out-of-order window D2's current-query guard must
                                // close: a newer keystroke cancels the in-flight request, so its
                                // stale response can never commit.
                                if (q == "jo") {
                                    delay(10_000)
                                }
                                jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            assertEquals(expected = listOf("jo"), actual = recorded)

            vm.onQueryChange("joh")
            advanceTimeByAndRun(300)
            advanceTimeByAndRun(20_000)

            assertEquals(expected = listOf("jo", "joh"), actual = recorded)
            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
        }

    @Test
    fun retrySearch_refires_latest_query() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = ClientViewModel(mockApiClient(clientHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.retrySearch()
            runCurrent()

            assertEquals(expected = listOf("jo", "jo"), actual = recorded)
        }

    @Test
    fun retrySearch_inflight_cancelled_by_clear_stale_response_never_commits() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                val q = request.url.parameters["q"].orEmpty()
                                recorded.add(q)
                                // The retried request stays in flight (virtual-time delay) — the
                                // D2 current-query guard must kill it on X-clear, or its stale
                                // response resurrects a list under an empty query.
                                if (q == "jo") {
                                    delay(10_000)
                                }
                                jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.retrySearch()
            advanceTimeByAndRun(1_000)
            assertIs<UiState.Loading>(vm.searchResults.value)

            vm.onQueryChange("")
            runCurrent()
            assertIs<UiState.Idle>(vm.searchResults.value)

            advanceTimeByAndRun(20_000)
            assertIs<UiState.Idle>(vm.searchResults.value)
        }

    @Test
    fun retrySearch_then_type_new_query_stale_retry_never_overwrites_newer_results() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                val q = request.url.parameters["q"].orEmpty()
                                recorded.add(q)
                                if (q == "jo") {
                                    // Stale retry held in flight beyond the newer query's response.
                                    delay(10_000)
                                    jsonRespond(status = HttpStatusCode.OK, body = STALE_JSON)
                                } else {
                                    jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                                }
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.retrySearch()
            advanceTimeByAndRun(1_000)

            vm.onQueryChange("joh")
            advanceTimeByAndRun(300)
            var state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })

            advanceTimeByAndRun(20_000)
            state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
            assertEquals(expected = listOf("jo", "jo", "joh"), actual = recorded)
        }

    @Test
    fun createClient_cache_mutation_survives_non_cancellable_stale_search() =
        runTest(testScheduler) {
            val releaseStaleSearch = CompletableDeferred<Unit>()
            var searchCount = 0
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                searchCount++
                                if (searchCount == 2) {
                                    withContext(NonCancellable) { releaseStaleSearch.await() }
                                    jsonRespond(status = HttpStatusCode.OK, body = STALE_JSON)
                                } else {
                                    jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                                }
                            }

                            request.method == HttpMethod.Post &&
                                request.url.encodedPath == "/api/clients" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = CREATED_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.onQueryChange("joh")
            advanceTimeByAndRun(300)
            assertIs<UiState.Loading>(vm.searchResults.value)

            vm.createClient(
                CreateClientRequest(
                    id = "c9",
                    firstName = "New",
                    lastName = "Client",
                    middleName = null,
                    gender = Gender.M,
                    age = 30,
                    systolicBp = null,
                    diastolicBp = null,
                ),
            )
            runCurrent()
            releaseStaleSearch.complete(Unit)
            testScheduler.advanceUntilIdle()

            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c9", "c1"), actual = state.data.map { it.id })
        }

    @Test
    fun search_success_empty_list_emits_success() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(clientHandler(searchBody = """[]""")),
                )

            vm.onQueryChange("xyz")
            advanceTimeByAndRun(300)

            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = emptyList(), actual = state.data)
        }

    @Test
    fun search_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(
                        // 4xx, not 5xx: the client's HttpRequestRetry (retryOnServerErrors) would
                        // suspend in backoff on 5xx, which never elapses under runCurrent-only.
                        clientHandler(searchStatus = HttpStatusCode.BadRequest),
                    ),
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)

            assertIs<UiState.Error>(vm.searchResults.value)
        }

    @Test
    fun loadClient_success_emits_detail() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.loadClient("c1")
            runCurrent()

            val state = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = "John", actual = state.data.firstName)
        }

    @Test
    fun loadClient_anonymized_detail_returns_null_names() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler(detailBody = ANONYMIZED_JSON)))

            vm.loadClient("c1")
            runCurrent()

            val state = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = null, actual = state.data.firstName)
            assertEquals(expected = null, actual = state.data.lastName)
            assertIs<Gender>(state.data.gender)
        }

    @Test
    fun loadClient_can_publish_authoritative_snapshot_for_other_entries() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.loadClient("c1", publishMutation = true)
            runCurrent()

            val mutation = assertNotNull(ClientState.clientMutation.value)
            assertEquals(expected = "c1", actual = mutation.clientId)
            assertEquals(expected = 0, actual = mutation.client?.sessionCount)
        }

    @Test
    fun profile_snapshot_does_not_refresh_clients_search() =
        runTest(testScheduler) {
            val searchQueries = mutableListOf<String>()
            val vm = ClientViewModel(mockApiClient(clientHandler(searchQueries = searchQueries)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.loadClient("c1", publishMutation = true)
            runCurrent()

            val mutation = assertNotNull(ClientState.clientMutation.value)
            assertFalse(mutation.refreshSearch)
            vm.applyClientMutation(mutation)

            assertEquals(expected = listOf("jo"), actual = searchQueries)
        }

    @Test
    fun older_profile_snapshot_cannot_overwrite_newer_snapshot() =
        runTest(testScheduler) {
            val older = ClientState.beginClientSnapshot("c1")
            val newer = ClientState.beginClientSnapshot("c1")

            assertFalse(ClientState.publishClientSnapshot(older, fixtureClient("c1")))
            assertTrue(
                ClientState.publishClientSnapshot(
                    newer,
                    fixtureClient("c1").copy(firstName = "Jane"),
                ),
            )
            assertEquals(
                expected = "Jane",
                actual =
                    ClientState.clientMutation.value
                        ?.client
                        ?.firstName,
            )
        }

    @Test
    fun clearing_client_state_rejects_late_mutation_completion() =
        runTest(testScheduler) {
            val lease = assertNotNull(ClientState.tryStartClientMutation())

            ClientState.clear()

            assertFalse(ClientState.publishClientMutation(lease, "c1", fixtureClient("c1")))
            assertFalse(ClientState.clientMutationInFlight.value)
        }

    @Test
    fun client_mutation_leases_serialize_cross_entry_writes() =
        runTest(testScheduler) {
            val first = assertNotNull(ClientState.tryStartClientMutation())

            assertTrue(ClientState.clientMutationInFlight.value)
            assertNull(ClientState.tryStartClientMutation())

            ClientState.finishClientMutation(first)

            assertFalse(ClientState.clientMutationInFlight.value)
            val second = assertNotNull(ClientState.tryStartClientMutation())
            ClientState.finishClientMutation(second)
        }

    @Test
    fun updateClient_success_emits_updated_detail() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            val state = assertIs<UiState.Success<ClientResponse>>(vm.updateClientState.value)
            assertEquals(expected = "0999", actual = state.data.phoneNumber)
        }

    @Test
    fun updateClient_success_commits_updated_record_into_detail_state() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            // D4 — the screen renders clientDetail, not updateClientState: a successful PATCH
            // must commit the response into the detail flow, or the display reverts to the
            // pre-edit value when edit mode exits.
            val detail = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = "0999", actual = detail.data.phoneNumber)
            assertEquals(expected = "John", actual = detail.data.firstName)
        }

    @Test
    fun updateClient_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(
                        clientHandler(updateStatus = HttpStatusCode.BadRequest),
                    ),
                )

            vm.updateClient("c1", UpdateClientRequest(firstName = ""))
            runCurrent()

            assertIs<UiState.Error>(vm.updateClientState.value)
        }

    @Test
    fun updateClient_403_silent_exit_leaves_state_idle() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(
                        clientHandler(updateStatus = HttpStatusCode.Forbidden),
                    ),
                )

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            // D4 403 axis: no Error surfaced, no Success — the state the screen maps to
            // "silently exit edit mode" (Loading → Idle transition without Success).
            assertIs<UiState.Idle>(vm.updateClientState.value)
        }

    @Test
    fun updateClient_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = clientHandler(updateStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/clients/c1") {
                    detailGets++
                }
                handler(request)
            }
            val vm = ClientViewModel(mockApiClient(wrapped))
            vm.loadClient("c1")
            runCurrent()

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            // D4 409 axis: the PATCH conflicted → the VM re-fetches the detail (2nd GET) and
            // raises the changed-elsewhere notice the screen renders as a banner.
            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.updateClientState.value)
        }

    @Test
    fun updateClient_404_reloads_husk_and_leaves_state_idle() =
        runTest(testScheduler) {
            var detailGets = 0
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients/c1" -> {
                                detailGets++
                                // The record was anonymized elsewhere: the first load returns the
                                // full record, the post-404 reload returns the husk (backend GET
                                // has no deletedAt filter — the row still 200s with null PII).
                                val body = if (detailGets == 1) DETAIL_JSON else ANONYMIZED_JSON
                                jsonRespond(status = HttpStatusCode.OK, body = body)
                            }

                            request.method == HttpMethod.Patch &&
                                request.url.encodedPath.startsWith("/api/clients/") -> {
                                jsonRespond(status = HttpStatusCode.NotFound, body = "")
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )
            vm.loadClient("c1")
            runCurrent()

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            // 404 axis: the record is gone (anonymized elsewhere) → reload renders the husk
            // (D10) instead of trapping the edit in Error forever; no changed-elsewhere notice.
            assertEquals(expected = 2, actual = detailGets)
            assertFalse(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.updateClientState.value)
            val detail = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = null, actual = detail.data.firstName)
            assertEquals(expected = null, actual = detail.data.lastName)
        }

    @Test
    fun updateClient_success_clears_changed_elsewhere_notice() =
        runTest(testScheduler) {
            var patches = 0
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Patch &&
                                request.url.encodedPath.startsWith("/api/clients/") -> {
                                patches++
                                if (patches == 1) {
                                    jsonRespond(status = HttpStatusCode.Conflict, body = "")
                                } else {
                                    jsonRespond(status = HttpStatusCode.OK, body = UPDATED_JSON)
                                }
                            }

                            request.method == HttpMethod.Get &&
                                request.url.encodedPath.startsWith("/api/clients/") -> {
                                jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()
            assertTrue(vm.detailChangedNotice.value)

            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()
            assertFalse(vm.detailChangedNotice.value)
        }

    @Test
    fun updateClient_success_cancels_inflight_reload_stale_get_never_reverts_detail() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients/c1" -> {
                                // The reload GET stays in flight past the PATCH's commit — the
                                // detail flow has two writers, and a stale GET landing after a
                                // fresher PATCH commit would revert the display to pre-edit data.
                                delay(10_000)
                                jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                            }

                            request.method == HttpMethod.Patch &&
                                request.url.encodedPath.startsWith("/api/clients/") -> {
                                jsonRespond(status = HttpStatusCode.OK, body = UPDATED_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.loadClient("c1")
            advanceTimeByAndRun(100)
            vm.updateClient("c1", UpdateClientRequest(phoneNumber = "0999"))
            runCurrent()

            var detail = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = "0999", actual = detail.data.phoneNumber)

            advanceTimeByAndRun(20_000)
            detail = assertIs<UiState.Success<ClientResponse>>(vm.clientDetail.value)
            assertEquals(expected = "0999", actual = detail.data.phoneNumber)
        }

    @Test
    fun anonymizeClient_success_sets_search_notice() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.anonymizeClient("c1")
            runCurrent()

            assertIs<UiState.Success<Unit>>(vm.anonymizeState.value)
            assertEquals(expected = "Client anonymized", actual = ClientState.anonymizeNotice.value)
        }

    @Test
    fun anonymizeClient_failure_emits_error_and_no_notice() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(
                        // 4xx, not 5xx — same retry-on-server-errors reason as search_failure test.
                        clientHandler(anonymizeStatus = HttpStatusCode.BadRequest),
                    ),
                )

            vm.anonymizeClient("c1")
            runCurrent()

            assertIs<UiState.Error>(vm.anonymizeState.value)
            assertEquals(expected = null, actual = ClientState.anonymizeNotice.value)
        }

    private fun TestScope.advanceTimeByAndRun(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private fun fixtureClient(id: String): ClientResponse =
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

    @Test
    fun onQueryChange_sets_query_state_synchronously() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.onQueryChange("al")
            vm.onQueryChange("ali")
            vm.onQueryChange("ali ")

            // VM-held query (#161 D9-deviation fix): the raw text updates synchronously per
            // keystroke — trimmed only for the search, never for the field's value.
            assertEquals(expected = "ali ", actual = vm.query.value)
        }

    @Test
    fun onQueryChange_clear_resets_query_state_to_empty() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(clientHandler()))

            vm.onQueryChange("alice")
            advanceTimeByAndRun(300)
            vm.onQueryChange("")
            runCurrent()

            assertEquals(expected = "", actual = vm.query.value)
            assertIs<UiState.Idle>(vm.searchResults.value)
        }

    private fun clientHandler(
        searchStatus: HttpStatusCode = HttpStatusCode.OK,
        searchBody: String = SEARCH_JSON,
        searchQueries: MutableList<String>? = null,
        updateStatus: HttpStatusCode = HttpStatusCode.OK,
        detailBody: String = DETAIL_JSON,
        anonymizeStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/clients" -> {
                    searchQueries?.add(request.url.parameters["q"].orEmpty())
                    jsonRespond(status = searchStatus, body = searchBody)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.startsWith("/api/clients/") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = detailBody)
                }

                request.method == HttpMethod.Patch &&
                    request.url.encodedPath.startsWith("/api/clients/") -> {
                    jsonRespond(status = updateStatus, body = UPDATED_JSON)
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.endsWith("/anonymize") -> {
                    jsonRespond(status = anonymizeStatus, body = "")
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

    private companion object {
        const val SEARCH_JSON =
            """[
                {"id":"c1","firstName":"John","lastName":"Doe","middleName":null,"suffix":null,"phoneNumber":"09171234567","address":null,"gender":"M","age":30,"systolicBp":120,"diastolicBp":80,"medicalConditions":null,"sessionCount":0}
            ]"""

        // Distinct payload for the stale-retry test: if the stale "jo" response ever commits
        // after "joh", the list would show c2 instead of c1.
        const val STALE_JSON =
            """[
                {"id":"c2","firstName":"Old","lastName":"Result","middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"M","age":50,"systolicBp":null,"diastolicBp":null,"medicalConditions":null,"sessionCount":0}
            ]"""

        const val CREATED_JSON =
            """{"id":"c9","firstName":"New","lastName":"Client","middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"M","age":30,"systolicBp":null,"diastolicBp":null,"medicalConditions":null,"sessionCount":0}"""

        const val DETAIL_JSON =
            """{"id":"c1","firstName":"John","lastName":"Doe","middleName":"A","suffix":null,"phoneNumber":"09171234567","address":"Manila","gender":"M","age":30,"systolicBp":120,"diastolicBp":80,"medicalConditions":null,"sessionCount":0}"""

        const val UPDATED_JSON =
            """{"id":"c1","firstName":"John","lastName":"Doe","middleName":"A","suffix":null,"phoneNumber":"0999","address":"Manila","gender":"M","age":30,"systolicBp":120,"diastolicBp":80,"medicalConditions":null,"sessionCount":0}"""

        // F3/D10 — anonymized: all PII null, gender + age retained.
        const val ANONYMIZED_JSON =
            """{"id":"c1","firstName":null,"lastName":null,"middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"F","age":44,"systolicBp":null,"diastolicBp":null,"medicalConditions":null,"sessionCount":0}"""
    }
}
