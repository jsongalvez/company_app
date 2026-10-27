package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.telemetry.TelemetryBatchRequest
import com.companyb.companyapp.contracts.telemetry.TelemetryEventInput
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import io.javalin.http.BadRequestResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TelemetryServicePostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()

    override fun initTestData() = Unit

    private fun events(
        count: Int,
        from: Int = 0,
    ) = (from until from + count).map { TelemetryEvent(it, OffsetDateTime.now(), "tap", """{"h":"abc"}""") }

    private fun rows(session: java.util.UUID) =
        transaction { TelemetryEventTable.selectAll().where { TelemetryEventTable.sessionId eq session }.count() }

    @Test
    fun `a batch sent twice is stored once`() {
        val session = TestFixtures.uuid()
        TelemetryService.record(userId, session, events(3))
        TelemetryService.record(userId, session, events(3))
        assertEquals(3, rows(session))
    }

    @Test
    fun `trim deletes rows past retention and the oldest past the cap`() {
        val old = TestFixtures.uuid()
        val recent = TestFixtures.uuid()
        TelemetryService.record(userId, old, events(2))
        transaction {
            TelemetryEventTable.update({ TelemetryEventTable.sessionId eq old }) {
                it[receivedAt] = OffsetDateTime.now().minusDays(TelemetryService.RETENTION_DAYS + 1)
            }
        }
        TelemetryService.record(userId, recent, events(5))
        transaction {
            for (seq in 0 until 5) {
                TelemetryEventTable.update({
                    (TelemetryEventTable.sessionId eq recent) and
                        (TelemetryEventTable.seq eq seq)
                }) {
                    it[receivedAt] = OffsetDateTime.now().minusMinutes((10 - seq).toLong())
                }
            }
        }

        TelemetryService.trim(OffsetDateTime.now(), cap = 3)

        assertEquals(0, rows(old))
        val kept =
            transaction {
                TelemetryEventTable
                    .selectAll()
                    .where {
                        TelemetryEventTable.sessionId eq recent
                    }.map { it[TelemetryEventTable.seq] }
                    .sorted()
            }
        assertEquals(listOf(1, 2, 3, 4), kept)
    }

    @Test
    fun `parse rejects unknown kinds and replaces a clock a week off`() {
        val now = System.currentTimeMillis()
        val bad =
            TelemetryBatchRequest(TestFixtures.uuid().toString(), listOf(TelemetryEventInput(0, now, "keylog", "{}")))
        assertFailsWith<BadRequestResponse> { TelemetryRoutes.parse(bad, now) }

        val skewed =
            TelemetryBatchRequest(TestFixtures.uuid().toString(), listOf(TelemetryEventInput(0, 0, "nav", "{}")))
        assertEquals(
            now,
            TelemetryRoutes
                .parse(skewed, now)
                .single()
                .at
                .toInstant()
                .toEpochMilli(),
        )
    }
}
