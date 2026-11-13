@file:Suppress("LargeClass") // #595

package com.companyb.companyapp.remittance

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemittanceAuthzTest : BasePostgresTest() {
    private val submitUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val globalViewer = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val branchDayDate = LocalDate.of(2026, 7, 10)

    private lateinit var draftRemittanceId: UUID
    private lateinit var otherBranchDraftId: UUID
    private lateinit var submittedRemittanceId: UUID
    private lateinit var breakdownId: UUID
    private lateinit var otherBranchBreakdownId: UUID
    private lateinit var submittedBreakdownId: UUID
    private lateinit var sessionId: UUID
    private lateinit var productSaleId: UUID

    override fun initTestData() {
        seedIdentityAndBranches()
        val dayId = seedSessionAndProductSale()
        seedDraftRemittances(dayId)
        seedSubmittedRemittance(dayId)
    }

    private fun seedIdentityAndBranches() {
        IdentityFixtures.insertTestUser(submitUser, "submit")
        IdentityFixtures.insertTestUser(noneUser, "no-caps")
        IdentityFixtures.insertTestUser(globalViewer, "global-viewer")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Remittance Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Remittance Branch $otherBranchId")

        IdentityFixtures.member(submitUser, branchId, Role.COORDINATOR)
        IdentityFixtures.hold(globalViewer, Role.ACCOUNTANT)

        SessionClientFixtures.insertTestClient(clientId)
    }

    private fun seedSessionAndProductSale(): UUID {
        sessionId = TestFixtures.uuid()
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = dayId,
            // #857 — only COMPLETED sessions are remittable.
            sessionStatus = com.companyb.companyapp.contracts.session.SessionStatus.COMPLETED,
        )

        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat $catId")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Product $prodId", catId)
        productSaleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = productSaleId,
            branchDayId = dayId,
            productId = prodId,
            handledBy = submitUser,
        )
        return dayId
    }

    private fun seedDraftRemittances(dayId: UUID) {
        draftRemittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = draftRemittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        breakdownId = TestFixtures.uuid()
        RemittanceService.addDayBreakdown(submitUser, draftRemittanceId, breakdownId, dayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("100.00"),
        )

        otherBranchDraftId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = otherBranchDraftId,
            type = RemittanceType.PRODUCT,
            branchId = otherBranchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        otherBranchBreakdownId = TestFixtures.uuid()
        val otherDayId = BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, branchDayDate)
        RemittanceService.addDayBreakdown(submitUser, otherBranchDraftId, otherBranchBreakdownId, otherDayId)
    }

    private fun seedSubmittedRemittance(dayId: UUID) {
        submittedRemittanceId = TestFixtures.uuid()
        val subLineId = TestFixtures.uuid()
        submittedBreakdownId = TestFixtures.uuid()
        val secondSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = secondSessionId,
            clientId = clientId,
            branchDayId = dayId,
            sessionStatus = com.companyb.companyapp.contracts.session.SessionStatus.COMPLETED,
        )
        RemittanceService.createDraft(
            callerId = submitUser,
            id = submittedRemittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        RemittanceService.addDayBreakdown(submitUser, submittedRemittanceId, submittedBreakdownId, dayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = submittedRemittanceId,
            id = subLineId,
            type = RemittanceLineType.SESSION,
            sessionId = secondSessionId,
            productSaleId = null,
            amount = BigDecimal("500.00"),
        )
        RemittanceService.submit(
            callerId = submitUser,
            remittanceId = submittedRemittanceId,
            expectedVersion = RemittanceService.getRemittance(submittedRemittanceId).remittance.version,
        )
    }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                RemittanceRoutes.register(cfg)
                RemittancePickerRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun assertGet(
        code: Int,
        path: String,
        user: UUID = submitUser,
    ) {
        val response = testServer.client.get(path, asUser(user))
        assertEquals(code, response.code, "GET $path as $user")
    }

    private fun assertPost(
        code: Int,
        path: String,
        body: Any,
        user: UUID = submitUser,
    ) {
        val response = testServer.client.post(path, body, asUser(user))
        assertEquals(code, response.code, "POST $path as $user")
    }

    private fun assertPatch(
        code: Int,
        path: String,
        body: Any,
        user: UUID = submitUser,
    ) {
        val response = testServer.client.patch(path, body, asUser(user))
        assertEquals(code, response.code, "PATCH $path as $user")
    }

    private fun assertDelete(
        code: Int,
        path: String,
        user: UUID = submitUser,
    ) {
        val response = testServer.client.delete(path, "", asUser(user))
        assertEquals(code, response.code, "DELETE $path as $user")
    }

    private fun lineBody(
        sessionId: UUID = this.sessionId,
        amount: String = "50.00",
    ) = mapOf(
        "id" to TestFixtures.uuid().toString(),
        "type" to "SESSION",
        "sessionId" to sessionId.toString(),
        "amount" to amount,
    )

    private fun headerBody(
        type: String = "SESSION",
        start: LocalDate = rangeStart,
        end: LocalDate = rangeEnd,
        expectedVersion: Int,
    ) = mapOf(
        "type" to type,
        "method" to "HANDED_TO_ACCOUNTANT",
        "dateRangeStart" to start.toString(),
        "dateRangeEnd" to end.toString(),
        "expectedVersion" to expectedVersion,
    )

    private fun undoBody(
        expectedVersion: Int,
        reason: String = "wrong",
    ) = mapOf("expectedVersion" to expectedVersion, "reason" to reason)

    // ──────────────────────────────────────────────
    // GET /api/remittances (G1 list)
    // ──────────────────────────────────────────────

    @Test
    fun `GET list allowed with net for granted user`() {
        val response = testServer.client.get("/api/remittances?branchId=$branchId", asUser(submitUser))
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .contains("\"netIncome\":\"500.00\""),
        )
    }

    @Test
    fun `GET list filtered by status`() {
        val response =
            testServer.client.get(
                "/api/remittances?branchId=$branchId&status=SUBMITTED",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"status\":\"SUBMITTED\""))
        assertTrue(!body.contains("\"id\":\"$draftRemittanceId\""))
    }

    @Test
    fun `GET list forbidden for no-capability user`() = assertGet(403, "/api/remittances?branchId=$branchId", noneUser)

    @Test
    fun `GET list forbidden on other branch`() = assertGet(403, "/api/remittances?branchId=$otherBranchId")

    @Test
    fun `GET list returns 404 for missing branch with grant`() {
        val missingBranchId = TestFixtures.uuid()
        assertGet(404, "/api/remittances?branchId=$missingBranchId")
    }

    @Test
    fun `GET list returns 400 without branchId`() = assertGet(400, "/api/remittances")

    @Test
    fun `GET list accepts ALL status`() {
        val response =
            testServer.client.get(
                "/api/remittances?branchId=$branchId&status=ALL",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"netIncome\":\"500.00\""))
        assertTrue(body.contains("\"id\":\"$draftRemittanceId\""))
    }

    @Test
    fun `GET list returns 400 on invalid status`() = assertGet(400, "/api/remittances?branchId=$branchId&status=NOPE")

    // ──────────────────────────────────────────────
    // G2/G3/G4 — pickers
    // ──────────────────────────────────────────────

    @Test
    fun `GET sessions picker allowed with client name`() {
        val response =
            testServer.client.get(
                "/api/branches/$branchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"clientName\":\"Test Client\""))
        assertTrue(body.contains("\"finalPrice\":\"2500.00\""))
    }

    @Test
    fun `GET sessions picker forbidden for no-capability user`() =
        assertGet(
            403,
            "/api/branches/$branchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
            noneUser,
        )

    @Test
    fun `GET product-sales picker allowed with product name`() {
        val response =
            testServer.client.get(
                "/api/branches/$branchId/remittance-product-sales?from=$rangeStart&to=$rangeEnd",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"productName\":"))
        assertTrue(body.contains("\"totalAmountAtTime\":\"100.00\""))
    }

    @Test
    fun `GET product-sales picker forbidden for no-capability user`() =
        assertGet(
            403,
            "/api/branches/$branchId/remittance-product-sales?from=$rangeStart&to=$rangeEnd",
            noneUser,
        )

    @Test
    fun `GET days picker allowed with effective statuses`() {
        val response =
            testServer.client.get(
                "/api/branches/$branchId/remittance-days?from=$rangeStart&to=$rangeEnd",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .contains("\"status\":\"REMITTED\""),
        )
    }

    @Test
    fun `GET days picker allowed for a global branch viewer without remittance rights`() =
        assertGet(
            200,
            "/api/branches/$branchId/remittance-days?from=$rangeStart&to=$rangeEnd",
            globalViewer,
        )

    @Test
    fun `GET days picker forbidden for no-capability user`() =
        assertGet(
            403,
            "/api/branches/$branchId/remittance-days?from=$rangeStart&to=$rangeEnd",
            noneUser,
        )

    @Test
    fun `GET days picker returns 400 on reversed range`() =
        assertGet(400, "/api/branches/$branchId/remittance-days?from=$rangeEnd&to=$rangeStart")

    @Test
    fun `GET days picker returns 400 on invalid date`() =
        assertGet(400, "/api/branches/$branchId/remittance-days?from=not-a-date&to=$rangeEnd")

    @Test
    fun `GET pickers forbidden on other branch`() =
        assertGet(
            403,
            "/api/branches/$otherBranchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
        )

    // ──────────────────────────────────────────────
    // G5 — DELETE day-breakdown
    // ──────────────────────────────────────────────

    @Test
    fun `DELETE day breakdown allowed for granted user`() {
        val response =
            testServer.client.delete(
                "/api/remittances/$draftRemittanceId/day-breakdowns/$breakdownId",
                "",
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .contains("\"id\":\"$breakdownId\""),
        )
    }

    @Test
    fun `DELETE day breakdown forbidden for no-capability user`() =
        assertDelete(403, "/api/remittances/$draftRemittanceId/day-breakdowns/$breakdownId", noneUser)

    @Test
    fun `DELETE day breakdown forbidden on other branch remittance`() =
        assertDelete(403, "/api/remittances/$otherBranchDraftId/day-breakdowns/$otherBranchBreakdownId")

    @Test
    fun `DELETE day breakdown on submitted remittance returns 400`() =
        assertDelete(400, "/api/remittances/$submittedRemittanceId/day-breakdowns/$submittedBreakdownId")

    // ──────────────────────────────────────────────
    // G6/G7 — detail snapshot + drift
    // ──────────────────────────────────────────────

    @Test
    fun `GET detail includes snapshot for submitted SESSION remittance`() {
        val response =
            testServer.client.get("/api/remittances/$submittedRemittanceId", asUser(submitUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"snapshot\":{\"remittanceId\":\"$submittedRemittanceId\""))
        assertTrue(body.contains("\"netIncome\":\"500.00\""))
    }

    @Test
    fun `GET drift allowed for granted user`() {
        val response =
            testServer.client.get("/api/remittances/$submittedRemittanceId/drift", asUser(submitUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"frozen\":"))
        assertTrue(body.contains("\"currentNet\":\"500.00\""))
    }

    @Test
    fun `GET drift forbidden for no-capability user`() =
        assertGet(403, "/api/remittances/$submittedRemittanceId/drift", noneUser)

    @Test
    fun `GET drift returns 404 for draft without snapshot`() =
        assertGet(404, "/api/remittances/$draftRemittanceId/drift")

    @Test
    fun `GET drift returns 403 on other branch remittance`() =
        assertGet(403, "/api/remittances/$otherBranchDraftId/drift")

    // ──────────────────────────────────────────────
    // F9 regression — pre-existing sub-routes were ungated
    // ──────────────────────────────────────────────

    @Test
    fun `POST line forbidden for no-capability user`() =
        assertPost(403, "/api/remittances/$draftRemittanceId/lines", lineBody(), noneUser)

    @Test
    fun `POST line duplicate session in same draft returns 409`() =
        assertPost(409, "/api/remittances/$draftRemittanceId/lines", lineBody())

    @Test
    fun `POST line still allowed for granted user`() {
        val freshSessionId = TestFixtures.uuid()
        val freshDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.contracts.session.SessionStatus.COMPLETED,
        )
        assertPost(201, "/api/remittances/$draftRemittanceId/lines", lineBody(sessionId = freshSessionId))
    }

    @Test
    fun `DELETE line forbidden for no-capability user`() {
        val lineId = TestFixtures.uuid()
        val freshSessionId = TestFixtures.uuid()
        val freshDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.contracts.session.SessionStatus.COMPLETED,
        )
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = freshSessionId,
            productSaleId = null,
            amount = BigDecimal("75.00"),
        )
        assertDelete(403, "/api/remittances/$draftRemittanceId/lines/$lineId", noneUser)
    }

    @Test
    fun `POST day-breakdown forbidden for no-capability user`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, LocalDate.of(2026, 7, 11))
        assertPost(
            403,
            "/api/remittances/$draftRemittanceId/day-breakdowns",
            mapOf("id" to TestFixtures.uuid().toString(), "branchDayId" to dayId.toString()),
            noneUser,
        )
    }

    @Test
    fun `POST submit forbidden for no-capability user`() =
        assertPost(
            403,
            "/api/remittances/$draftRemittanceId/submit",
            mapOf("expectedVersion" to 2),
            noneUser,
        )

    // ──────────────────────────────────────────────
    // G8 — undo + header PATCH
    // ──────────────────────────────────────────────

    @Test
    fun `POST undo allowed for granted user`() {
        val version = RemittanceService.getRemittance(submittedRemittanceId).remittance.version
        val response =
            testServer.client.post(
                "/api/remittances/$submittedRemittanceId/undo",
                undoBody(version, "wrong amounts"),
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .contains("\"status\":\"DRAFT\""),
        )
    }

    @Test
    fun `POST undo forbidden for no-capability user`() =
        assertPost(403, "/api/remittances/$submittedRemittanceId/undo", undoBody(3), noneUser)

    @Test
    fun `POST undo forbidden on other branch remittance`() =
        assertPost(403, "/api/remittances/$otherBranchDraftId/undo", undoBody(1))

    @Test
    fun `POST undo on non-submitted remittance returns 400`() =
        assertPost(400, "/api/remittances/$draftRemittanceId/undo", undoBody(2))

    @Test
    fun `POST undo without reason returns 400`() =
        assertPost(400, "/api/remittances/$submittedRemittanceId/undo", undoBody(3, "   "))

    @Test
    fun `POST undo with multi-line reason returns 400`() =
        assertPost(400, "/api/remittances/$submittedRemittanceId/undo", undoBody(3, "line one\nline two"))

    @Test
    fun `POST undo with version mismatch returns 409`() =
        assertPost(409, "/api/remittances/$submittedRemittanceId/undo", undoBody(99))

    @Test
    fun `PATCH header allowed for granted user`() {
        val response =
            testServer.client.patch(
                "/api/remittances/$draftRemittanceId",
                headerBody(expectedVersion = 3),
                asUser(submitUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"method\":\"HANDED_TO_ACCOUNTANT\""))
        assertTrue(body.contains("\"version\":4"))
    }

    @Test
    fun `PATCH header to a type already used for the submitted date is allowed for drafts`() =
        assertPatch(200, "/api/remittances/$draftRemittanceId", headerBody(expectedVersion = 3))

    @Test
    fun `PATCH header forbidden for no-capability user`() =
        assertPatch(403, "/api/remittances/$draftRemittanceId", headerBody(expectedVersion = 2), noneUser)

    @Test
    fun `PATCH header forbidden on other branch remittance`() =
        assertPatch(403, "/api/remittances/$otherBranchDraftId", headerBody(expectedVersion = 1))

    @Test
    fun `PATCH header invalid type returns 400`() =
        assertPatch(400, "/api/remittances/$draftRemittanceId", headerBody(type = "NOPE", expectedVersion = 2))

    @Test
    fun `PATCH header reversed range returns 400`() =
        assertPatch(
            400,
            "/api/remittances/$draftRemittanceId",
            headerBody(start = rangeEnd, end = rangeStart, expectedVersion = 2),
        )

    @Test
    fun `PATCH header on submitted remittance returns 400`() =
        assertPatch(400, "/api/remittances/$submittedRemittanceId", headerBody(expectedVersion = 3))

    @Test
    fun `PATCH header with version mismatch returns 409`() =
        assertPatch(409, "/api/remittances/$draftRemittanceId", headerBody(expectedVersion = 99))

    @Test
    fun `GET list with unknown branch returns 404`() {
        val unknownBranch = TestFixtures.uuid()
        assertGet(404, "/api/remittances?branchId=$unknownBranch")
    }

    @Test
    fun `GET list with unknown branch returns 404 without any capability`() {
        val unknownBranch = TestFixtures.uuid()
        assertGet(404, "/api/remittances?branchId=$unknownBranch", noneUser)
    }

    @Test
    fun `GET sessions picker with unknown branch returns 404`() {
        val unknownBranch = TestFixtures.uuid()
        assertGet(404, "/api/branches/$unknownBranch/remittance-sessions?from=$rangeStart&to=$rangeEnd")
    }

    @Test
    fun `GET product-sales picker with unknown branch returns 404`() {
        val unknownBranch = TestFixtures.uuid()
        assertGet(404, "/api/branches/$unknownBranch/remittance-product-sales?from=$rangeStart&to=$rangeEnd")
    }

    @Test
    fun `GET days picker with unknown branch returns 404`() {
        val unknownBranch = TestFixtures.uuid()
        assertGet(404, "/api/branches/$unknownBranch/remittance-days?from=$rangeStart&to=$rangeEnd")
    }

    @Test
    fun `GET list on branch with zero rows returns 200 empty list`() {
        val emptyBranch = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(emptyBranch, "Empty Remittance Branch $emptyBranch")
        IdentityFixtures.member(submitUser, emptyBranch, Role.COORDINATOR)
        val response = testServer.client.get("/api/remittances?branchId=$emptyBranch", asUser(submitUser))
        assertEquals(200, response.code)
        assertEquals("[]", response.body.string().orEmpty())
    }

    @Test
    fun `GET pickers on branch with zero rows return 200 empty lists`() {
        val emptyBranch = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(emptyBranch, "Empty Picker Branch $emptyBranch")
        IdentityFixtures.member(submitUser, emptyBranch, Role.COORDINATOR)
        for (picker in listOf("remittance-sessions", "remittance-product-sales", "remittance-days")) {
            val response =
                testServer.client.get(
                    "/api/branches/$emptyBranch/$picker?from=$rangeStart&to=$rangeEnd",
                    asUser(submitUser),
                )
            assertEquals(200, response.code, picker)
            assertEquals("[]", response.body.string().orEmpty(), picker)
        }
    }
}
