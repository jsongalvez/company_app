package com.companyb.companyapp.session.create

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionPreviewResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #674 — the intake workspace state rules: the client-side price gate, the
 * user-edit vs automatic-price dirty distinction, the discard-draft reset, the
 * Additional-details summary, and practitioner-selection retention across an
 * optional members-load failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCreateIntakeStateTest {
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

    @Test
    fun `price gate accepts zero and positives only`() {
        assertEquals(250.0, parseSessionPrice("250.00"))
        assertEquals(0.0, parseSessionPrice("0"))
        assertEquals(250.0, parseSessionPrice("  250 "))
        assertNull(parseSessionPrice(""))
        assertNull(parseSessionPrice("   "))
        assertNull(parseSessionPrice("-1"))
        assertNull(parseSessionPrice("abc"))
        assertNull(parseSessionPrice("Infinity"))
        assertNull(parseSessionPrice("1,000"))
    }

    @Test
    fun `fresh draft has no user edits`() {
        assertFalse(SessionCreateDraft().hasUserEdits())
    }

    @Test
    fun `automatic preview price never marks the draft dirty`() {
        val vm = SessionCreateViewModel(mockApiClient(previewHandler()), BRANCH_ID)
        vm.applyPreviewPrice(SessionPreviewResponse(SessionType.REGULAR, "250.00"))
        assertEquals("250.00", vm.draft.value.finalPrice)
        assertFalse(vm.draft.value.hasUserEdits())

        vm.applyPreviewPrice(SessionPreviewResponse(SessionType.MEDICAL_MISSION, "0.00"))
        assertFalse(vm.draft.value.hasUserEdits())
    }

    @Test
    fun `explicit edits mark the draft dirty`() {
        assertTrue(SessionCreateDraft(finalPrice = "900", finalPriceEdited = true).hasUserEdits())
        assertTrue(SessionCreateDraft(otherConcerns = "history").hasUserEdits())
        assertTrue(SessionCreateDraft(remarks = "note").hasUserEdits())
        assertTrue(SessionCreateDraft(isBooked = true).hasUserEdits())
        assertTrue(SessionCreateDraft(nextAppointmentDate = "2026-09-01").hasUserEdits())
    }

    @Test
    fun `untouched entry is not dirty`() {
        assertFalse(isSessionCreateDirty(null, SessionCreateDraft(), emptySet(), null))
    }

    @Test
    fun `picked client concerns practitioner or edits are dirty`() {
        val dirtyDraft = SessionCreateDraft(remarks = "note")
        assertTrue(isSessionCreateDirty(client("c1"), SessionCreateDraft(), emptySet(), null))
        assertTrue(isSessionCreateDirty(null, dirtyDraft, emptySet(), null))
        assertTrue(isSessionCreateDirty(null, SessionCreateDraft(), setOf("con1"), null))
        assertTrue(
            isSessionCreateDirty(
                null,
                SessionCreateDraft(),
                emptySet(),
                BranchMemberResponse("p1", "A"),
            ),
        )
    }

    @Test
    fun `additional details summary stays empty until populated`() {
        assertNull(additionalDetailsSummary(null, ""))
        assertNull(additionalDetailsSummary("  ", "   "))
        assertEquals("A Santos", additionalDetailsSummary("A Santos", ""))
        assertEquals("Remarks added", additionalDetailsSummary(null, "note"))
        assertEquals("A Santos · Remarks added", additionalDetailsSummary("A Santos", "note"))
    }

    @Test
    fun `discard draft resets selection edits and submit state`() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(previewHandler()), BRANCH_ID)

            vm.selectClient(client("c1"))
            runCurrent()
            vm.setRemarks("note")
            vm.setBooked(true)
            vm.selectPractitioner(BranchMemberResponse("p1", "A"))
            vm.toggleConcern("con1")
            assertTrue(
                isSessionCreateDirty(
                    vm.selectedClient.value,
                    vm.draft.value,
                    vm.selectedConcernIds.value,
                    vm.selectedPractitioner.value,
                ),
            )

            vm.discardDraft()

            assertEquals(null, vm.selectedClient.value)
            assertEquals(SessionCreateDraft(), vm.draft.value)
            assertEquals(emptySet(), vm.selectedConcernIds.value)
            assertEquals(null, vm.selectedPractitioner.value)
            assertIs<UiState.Idle>(vm.preview.value)
            assertIs<UiState.Idle>(vm.createResult.value)
            assertFalse(
                isSessionCreateDirty(
                    vm.selectedClient.value,
                    vm.draft.value,
                    vm.selectedConcernIds.value,
                    vm.selectedPractitioner.value,
                ),
            )
        }

    @Test
    fun `practitioner choice survives a failed members reload`() =
        runTest(testScheduler) {
            val vm = SessionCreateViewModel(mockApiClient(failingMembersHandler()), BRANCH_ID)

            vm.selectPractitioner(BranchMemberResponse("p1", "A"))
            vm.loadMembers()
            testScheduler.advanceUntilIdle()

            assertIs<UiState.Error>(vm.members.value)
            assertEquals("p1", vm.selectedPractitioner.value?.id)
        }

    private fun previewHandler(): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/branches/$BRANCH_ID/session-preview" -> {
                    jsonRespond(status = HttpStatusCode.OK, body = PREVIEW_JSON)
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
        }

    private fun failingMembersHandler(): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/branches/$BRANCH_ID/members" -> {
                    jsonRespond(status = HttpStatusCode.InternalServerError, body = "{}")
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
        const val PREVIEW_JSON = """{"sessionType":"REGULAR","basePrice":"250.00"}"""
    }
}
