@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuditLogAuthzTest : BasePostgresTest() {
    private val editorA = UUID.randomUUID()
    private val editorB = UUID.randomUUID()
    private val manageUsersUser = UUID.randomUUID()
    private val manageProductsUser = UUID.randomUUID()
    private val globalViewUser = UUID.randomUUID()
    private val noneUser = UUID.randomUUID()
    private val branchA = UUID.randomUUID()
    private val branchB = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private var branchARowId: UUID = UUID.randomUUID()
    private var branchBFlaggedId: UUID = UUID.randomUUID()
    private var selfAckId: UUID = UUID.randomUUID()
    private val recordA = UUID.randomUUID()
    private val recordB = UUID.randomUUID()

    @Suppress("LongMethod")
    override fun initTestData() {
        listOf(
            editorA to "editor-a",
            editorB to "editor-b",
            manageUsersUser to "manage-users",
            manageProductsUser to "manage-products",
            globalViewUser to "global-view",
            noneUser to "no-caps",
        ).forEach { (id, prefix) ->
            DatabaseTestHelper.insertTestUser(id, prefix)
            trackOwned(AppUserTable, AppUserTable.id, id)
        }

        trackOwned(BranchTable, BranchTable.id, branchA)
        DatabaseTestHelper.insertTestBranch(branchA, "Audit Branch A $branchA")
        trackOwned(BranchTable, BranchTable.id, branchB)
        DatabaseTestHelper.insertTestBranch(branchB, "Audit Branch B $branchB")

        DatabaseTestHelper.grantCapability(
            userId = editorA,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = editorB,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchB,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = manageUsersUser,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = com.companyb.companyapp.service.CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = manageProductsUser,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = com.companyb.companyapp.service.CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = globalViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = com.companyb.companyapp.service.CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        listOf(editorA, editorB, manageUsersUser, manageProductsUser, globalViewUser).forEach {
            trackOwned(UserCapabilityTable, UserCapabilityTable.userId, it)
        }

        // Direct inserts with explicit changedAt for deterministic cursor tests.
        transaction {
            fun insert(
                tableName: String,
                recordId: UUID,
                action: AuditAction,
                changedBy: UUID,
                changedAt: OffsetDateTime,
                branchId: UUID? = null,
                isFlagged: Boolean = false,
                acknowledgedBy: UUID? = null,
                acknowledgedAt: OffsetDateTime? = null,
            ): UUID {
                val id =
                    AuditLogTable.insert {
                        it[AuditLogTable.auditTableName] = tableName
                        it[AuditLogTable.recordId] = recordId
                        it[AuditLogTable.action] = action
                        it[AuditLogTable.changedBy] = changedBy
                        it[AuditLogTable.changedAt] = changedAt
                        if (branchId != null) it[AuditLogTable.branchId] = branchId
                        it[AuditLogTable.isFlagged] = isFlagged
                        if (acknowledgedBy != null) {
                            it[AuditLogTable.acknowledgedBy] = acknowledgedBy
                            it[AuditLogTable.acknowledgedAt] = acknowledgedAt
                        }
                    } get AuditLogTable.id
                trackOwned(AuditLogTable, AuditLogTable.id, id)
                return id
            }

            // Branch-scoped rows.
            branchARowId =
                insert("session", recordA, AuditAction.UPDATE, editorA, at(8), branchA)
            insert("session", recordB, AuditAction.UPDATE, editorA, at(7), branchB)
            // Branchless policy rows (NULL branch).
            insert("client", UUID.randomUUID(), AuditAction.INSERT, editorA, at(6), null)
            insert("app_user", UUID.randomUUID(), AuditAction.UPDATE, editorA, at(5), null)
            insert("product", UUID.randomUUID(), AuditAction.INSERT, editorA, at(4), null)
            insert("product_category", UUID.randomUUID(), AuditAction.INSERT, editorA, at(3), null)
            insert("concern", UUID.randomUUID(), AuditAction.INSERT, editorA, at(2), null)
            // Unlisted table with NULL branch — Owner/Accountant (global view) only.
            insert("mystery_table", UUID.randomUUID(), AuditAction.INSERT, editorA, at(1), null)
            // Flagged rows for acknowledge tests.
            selfAckId =
                insert(
                    "session",
                    UUID.randomUUID(),
                    AuditAction.UPDATE,
                    editorA,
                    at(0),
                    branchA,
                    isFlagged = true,
                )
            branchBFlaggedId =
                insert(
                    "session",
                    UUID.randomUUID(),
                    AuditAction.UPDATE,
                    editorA,
                    at(-1),
                    branchB,
                    isFlagged = true,
                )
            // Already-acknowledged flagged row — 404 on re-ack.
            insert(
                "session",
                UUID.randomUUID(),
                AuditAction.UPDATE,
                editorA,
                at(-2),
                branchA,
                isFlagged = true,
                acknowledgedBy = editorB,
                acknowledgedAt = at(-1),
            )
        }
    }

    private fun at(hourOffset: Int): OffsetDateTime =
        OffsetDateTime.of(2026, 8, 1, 10 + hourOffset, 0, 0, 0, ZoneOffset.UTC)

    private fun createApp(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
                ctx.attribute("userId", ctx.header("X-Test-User") ?: noneUser.toString())
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
            AuditLogRoutes.register(cfg)
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun browse(
        user: UUID,
        query: String = "",
    ): Pair<Int, AuditLogBrowseResponse> {
        var status = 0
        var body: AuditLogBrowseResponse? = null
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/audit-log/entries$query", asUser(user))
            status = response.code
            body =
                response.body
                    ?.string()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString<AuditLogBrowseResponse>(it) }
        }
        return status to (body ?: AuditLogBrowseResponse(emptyList()))
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/entries — window scoping
    // ──────────────────────────────────────────────

    @Test
    fun `browse returns only rows in the caller branch window`() {
        val (status, body) = browse(editorA)
        assertEquals(200, status)
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertTrue("session" in tableNames)
        assertTrue("client" in tableNames)
        assertTrue("mystery_table" !in tableNames)
        assertEquals(5, body.entries.size)
    }

    @Test
    fun `browse excludes other-branch rows entirely`() {
        val (_, body) = browse(editorA)
        body.entries.forEach {
            assertTrue(it.tableName != "session" || it.branchId != branchB.toString())
        }
    }

    @Test
    fun `browse MANAGE_USERS holder sees app_user and branch rows but not client`() {
        val (_, body) = browse(manageUsersUser)
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertTrue("app_user" in tableNames)
        assertTrue("client" !in tableNames)
        assertTrue("session" !in tableNames)
        assertEquals(1, body.entries.size)
    }

    @Test
    fun `browse MANAGE_PRODUCTS holder sees product and category rows`() {
        val (_, body) = browse(manageProductsUser)
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertTrue("product" in tableNames)
        assertTrue("product_category" in tableNames)
        assertTrue("client" !in tableNames)
        assertEquals(2, body.entries.size)
    }

    @Test
    fun `browse any EDIT_BRANCH_DATA holder sees client and concern rows`() {
        val (_, body) = browse(editorB)
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertTrue("client" in tableNames)
        assertTrue("concern" in tableNames)
        assertTrue("app_user" !in tableNames)
    }

    @Test
    fun `browse global VIEW_BRANCH_DATA holder sees everything including NULL unlisted`() {
        val (_, body) = browse(globalViewUser)
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertTrue("session" in tableNames)
        assertTrue("client" in tableNames)
        assertTrue("app_user" in tableNames)
        assertTrue("product" in tableNames)
        assertTrue("mystery_table" in tableNames)
        assertEquals(11, body.entries.size)
    }

    @Test
    fun `browse zero-grant user gets empty list not 403`() {
        val (status, body) = browse(noneUser)
        assertEquals(200, status)
        assertTrue(body.entries.isEmpty())
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/entries — DTO fields
    // ──────────────────────────────────────────────

    @Test
    fun `browse entries carry changedByName and branchId`() {
        val (_, body) = browse(editorA)
        val sessionEntry = body.entries.first { it.tableName == "session" }
        assertEquals("Test editor-a", sessionEntry.changedByName)
        assertEquals(branchA.toString(), sessionEntry.branchId)
        val clientEntry = body.entries.first { it.tableName == "client" }
        assertNull(clientEntry.branchId)
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/entries — filters
    // ──────────────────────────────────────────────

    @Test
    fun `browse filters by tableName`() {
        val (_, body) = browse(editorA, "?tableName=session")
        assertTrue(body.entries.isNotEmpty())
        body.entries.forEach { assertEquals("session", it.tableName) }
    }

    @Test
    fun `browse filters by action`() {
        val (_, body) = browse(editorA, "?action=INSERT")
        val tableNames = body.entries.map { it.tableName }.toSet()
        assertEquals(setOf("client", "concern"), tableNames)
    }

    @Test
    fun `browse filters by caller name ILIKE`() {
        val (_, body) = browse(editorA, "?callerName=EDITOR-A")
        assertEquals(5, body.entries.size)
        body.entries.forEach { assertEquals("Test editor-a", it.changedByName) }
    }

    @Test
    fun `browse filters by date range inclusive`() {
        val (_, body) = browse(editorA, "?dateFrom=2026-08-01&dateTo=2026-08-01")
        assertTrue(body.entries.isNotEmpty())
    }

    @Test
    fun `browse dateFrom is inclusive of its day boundary`() {
        // Rows sit at 10:00 UTC on 2026-08-01 — a dateFrom of 2026-08-01 must
        // include them even at the exact Manila-day start.
        val (status, body) = browse(editorA, "?dateFrom=2026-08-01")
        assertEquals(200, status)
        assertTrue(body.entries.isNotEmpty())
    }

    @Test
    fun `browse dateTo excludes the next day`() {
        val (_, body) = browse(editorA, "?dateTo=2026-07-31")
        assertTrue(body.entries.isEmpty())
    }

    @Test
    fun `browse rejects invalid action`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/audit-log/entries?action=NOPE", asUser(editorA)).code)
        }
    }

    @Test
    fun `browse rejects invalid date`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/audit-log/entries?dateFrom=not-a-date", asUser(editorA)).code)
        }
    }

    @Test
    fun `browse rejects inverted date range`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client
                    .get("/api/audit-log/entries?dateFrom=2026-08-02&dateTo=2026-08-01", asUser(editorA))
                    .code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/entries — cursor pagination
    // ──────────────────────────────────────────────

    @Test
    fun `browse paginates with keyset cursor without dup or skip`() {
        var cursor: String? = null
        val ids = mutableListOf<String>()
        var pageCount = 0
        do {
            val query = "?limit=4" + (cursor?.let { "&cursor=$it" } ?: "")
            val (status, body) = browse(globalViewUser, query)
            assertEquals(200, status)
            ids += body.entries.map { it.id }
            cursor = body.nextCursor
            pageCount++
        } while (cursor != null)

        assertEquals(11, ids.size)
        assertEquals(11, ids.distinct().size, "cursor pagination must not duplicate or skip rows")
        assertTrue(pageCount >= 3)
    }

    @Test
    fun `browse rejects malformed cursor`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client.get("/api/audit-log/entries?cursor=not-a-cursor", asUser(editorA)).code,
            )
        }
    }

    @Test
    fun `browse rejects limit out of range`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/audit-log/entries?limit=0", asUser(editorA)).code)
            assertEquals(400, client.get("/api/audit-log/entries?limit=101", asUser(editorA)).code)
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log — per-record, window scoped
    // ──────────────────────────────────────────────

    @Test
    fun `per-record history scopes to the caller window`() {
        JavalinTest.test(createApp()) { _, client ->
            val visible =
                client.get(
                    "/api/audit-log?tableName=session&recordId=$recordA",
                    asUser(editorA),
                )
            assertEquals(200, visible.code)
            val visibleBody = visible.body?.string().orEmpty()
            assertTrue(visibleBody.contains(branchARowId.toString()))
        }
    }

    @Test
    fun `per-record history for other-branch record returns empty`() {
        JavalinTest.test(createApp()) { _, client ->
            val invisible =
                client
                    .get(
                        "/api/audit-log?tableName=session&recordId=$recordA",
                        asUser(editorB),
                    )
            assertEquals(200, invisible.code)
            val body = invisible.body?.string().orEmpty()
            assertTrue(!body.contains(branchARowId.toString()))
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/flagged — window scoped
    // ──────────────────────────────────────────────

    @Test
    fun `flagged list scopes to window and carries changedByName`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/audit-log/flagged", asUser(editorB))
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains(branchBFlaggedId.toString()))
            assertTrue(body.contains("Test editor-a"))
            val entries = json.decodeFromString<List<AuditLogEntryResponse>>(body)
            assertEquals(1, entries.size)
            assertNull(entries[0].acknowledgedBy)
        }
    }

    @Test
    fun `flagged list excludes out-of-window and acknowledged rows`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/audit-log/flagged", asUser(editorA))
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(!body.contains(branchBFlaggedId.toString()))
            assertEquals(1, json.decodeFromString<List<AuditLogEntryResponse>>(body).size)
        }
    }

    // ──────────────────────────────────────────────
    // PATCH /api/audit-log/{entryId}/acknowledge
    // ──────────────────────────────────────────────

    @Test
    fun `acknowledge succeeds for a different user in the same window`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.patch("/api/audit-log/$branchBFlaggedId/acknowledge", null, asUser(editorB))
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains(editorB.toString()))
        }
    }

    @Test
    fun `self-acknowledge returns 409`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(409, client.patch("/api/audit-log/$selfAckId/acknowledge", null, asUser(editorA)).code)
        }
    }

    @Test
    fun `acknowledge outside the window returns 404`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                404,
                client.patch("/api/audit-log/$branchBFlaggedId/acknowledge", null, asUser(manageUsersUser)).code,
            )
        }
    }

    @Test
    fun `acknowledge already-acknowledged returns 404`() {
        JavalinTest.test(createApp()) { _, client ->
            val ackedId =
                transaction {
                    AuditLogTable
                        .selectAll()
                        .where { AuditLogTable.acknowledgedBy eq editorB }
                        .single()[AuditLogTable.id]
                }
            assertEquals(404, client.patch("/api/audit-log/$ackedId/acknowledge", null, asUser(editorB)).code)
        }
    }

    @Test
    fun `acknowledge nonexistent entry returns 404`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                404,
                client.patch("/api/audit-log/${UUID.randomUUID()}/acknowledge", null, asUser(editorA)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/audit-log/tables — registry
    // ──────────────────────────────────────────────

    @Test
    fun `tables endpoint serves the audited-table registry with labels`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/audit-log/tables", asUser(noneUser))
            assertEquals(200, response.code)
            val tables = json.decodeFromString<List<AuditLogTableResponse>>(response.body?.string().orEmpty())
            assertTrue(tables.size >= 20)
            val clientEntry = tables.first { it.tableName == "client" }
            assertEquals("Client", clientEntry.label)
        }
    }

    @Test
    fun `tables endpoint is reachable with zero grants`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(200, client.get("/api/audit-log/tables", asUser(noneUser)).code)
        }
    }
}
