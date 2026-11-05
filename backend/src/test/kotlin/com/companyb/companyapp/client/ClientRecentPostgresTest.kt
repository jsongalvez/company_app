package com.companyb.companyapp.client

import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClientRecentPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "recent-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Recent Test Branch $branchId")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
    }

    @Test
    fun `recent lists clients by their newest live session, the same as grouping every session`() {
        val clients = (0 until RECENT_CLIENTS).map { TestFixtures.uuid() }
        clients.forEachIndexed { i, id ->
            ClientService.create(
                callerId = callerId,
                id = id,
                firstName = "Recent$i",
                lastName = "Client",
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = Gender.F,
                age = 30,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )
        }
        val rows = seedSessions(clients)
        transaction {
            exec(
                "UPDATE session SET created_at = v.at::timestamptz FROM (VALUES " +
                    rows.joinToString(",") { (id, _, at) -> "('$id'::uuid, '$at')" } +
                    ") AS v(id, at) WHERE session.id = v.id",
            )
            exec("UPDATE client SET deleted_at = now() WHERE id = '${clients[7]}'")
        }
        transaction {
            SessionVoidTable.insert {
                it[SessionVoidTable.id] = TestFixtures.uuid()
                it[SessionVoidTable.sessionId] = rows.first { row -> row.second == clients[6] }.first
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "Test void"
            }
        }

        val expected = newestLiveByClient()
        val newest = expected.toMap()
        val actual = ClientService.recent().map { it.id }

        assertEquals(expected.map { it.first }.toSet(), actual.toSet())
        assertEquals(expected.map { it.second }, actual.map { newest.getValue(it) })
        assertFalse(clients[7] in actual)
    }

    /** 400 sessions with set times; returns (session, client, time) newest first. */
    private fun seedSessions(clients: List<UUID>): List<Triple<UUID, UUID, java.time.OffsetDateTime>> {
        val base = java.time.OffsetDateTime.parse("2026-01-01T12:00:00+08:00")
        val rows = mutableListOf<Triple<UUID, UUID, java.time.OffsetDateTime>>()
        // The newest 198 sessions belong to 5 clients, so the walk has to page past its first batch of 200.
        // Sessions 198-202 share one time and are the only sessions of five other clients, so a page boundary
        // falls inside a tie and a cursor that skips tied rows loses those clients.
        val tied = listOf(25, 26, 27, 28, 29)
        for (i in 0 until RECENT_SESSIONS) {
            val client =
                when {
                    i < 198 -> clients[i % 5]
                    i <= 202 -> clients[tied[i - 198]]
                    else -> clients[5 + (i - 203) % (RECENT_CLIENTS - 10)]
                }
            val at = if (i in 198..202) base.minusSeconds(198) else base.minusSeconds(i.toLong())
            val sessionId = TestFixtures.uuid()
            SessionClientFixtures.insertTestSession(
                id = sessionId,
                clientId = client,
                branchDayId = branchDayId,
                sessionType = SessionType.REGULAR,
                sessionStatus = SessionStatus.COMPLETED,
            )
            rows += Triple(sessionId, client, at)
        }
        return rows
    }

    /** The query recent() used to run: live clients and their newest live session time, newest first. */
    private fun newestLiveByClient(): List<Pair<UUID, String>> =
        transaction {
            exec(
                """
                SELECT s.client_id, max(s.created_at) FROM session s JOIN client c ON c.id = s.client_id
                LEFT JOIN active_session_voids v ON v.session_id = s.id
                WHERE v.session_id IS NULL AND c.deleted_at IS NULL
                GROUP BY s.client_id ORDER BY max(s.created_at) DESC LIMIT 20
                """.trimIndent(),
            ) { rs ->
                val rows = mutableListOf<Pair<UUID, String>>()
                while (rs.next()) rows += (rs.getObject(1) as UUID) to rs.getObject(2).toString()
                rows
            }!!
        }

    private companion object {
        const val RECENT_CLIENTS = 30
        const val RECENT_SESSIONS = 400
    }
}
