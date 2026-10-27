package com.companyb.companyapp.observability

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal object TelemetryEventTable : Table("telemetry_event") {
    private const val KIND_LENGTH = 10

    val sessionId = javaUUID("session_id")
    val seq = integer("seq")
    val userId = javaUUID("user_id")
    val at = timestampWithTimeZone("at")
    val kind = varchar("kind", KIND_LENGTH)
    val data = text("data")
    val receivedAt = timestampWithTimeZone("received_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(sessionId, seq)
}

data class TelemetryEvent(
    val seq: Int,
    val at: OffsetDateTime,
    val kind: String,
    val data: String,
)

/** Rows of [TelemetryEventTable]; callers own the transaction ([TelemetryService]). */
internal object TelemetryStore {
    /** Idempotent on (session, seq): a batch sent twice is stored once. */
    fun insertInTransaction(
        userId: UUID,
        sessionId: UUID,
        events: List<TelemetryEvent>,
    ) {
        TelemetryEventTable.batchInsert(events, ignore = true, shouldReturnGeneratedValues = false) { e ->
            this[TelemetryEventTable.sessionId] = sessionId
            this[TelemetryEventTable.seq] = e.seq
            this[TelemetryEventTable.userId] = userId
            this[TelemetryEventTable.at] = e.at
            this[TelemetryEventTable.kind] = e.kind
            this[TelemetryEventTable.data] = e.data
        }
    }

    /** Deletes rows received before [before], then the oldest rows past [cap]. Returns how many went. */
    fun trimInTransaction(
        before: OffsetDateTime,
        cap: Long,
    ): Int {
        val expired = TelemetryEventTable.deleteWhere { receivedAt less before }
        val boundary =
            TelemetryEventTable
                .select(TelemetryEventTable.receivedAt)
                .orderBy(TelemetryEventTable.receivedAt to SortOrder.DESC)
                .limit(1)
                .offset(cap)
                .singleOrNull()
                ?.get(TelemetryEventTable.receivedAt)
        val overCap = boundary?.let { b -> TelemetryEventTable.deleteWhere { receivedAt less b } } ?: 0
        return expired + overCap
    }
}

/**
 * Stores uploaded events and keeps the table bounded: rows older than [RETENTION_DAYS] and rows past
 * [ROW_CAP] (oldest first) are deleted. The trim runs inside an upload, at most every [TRIM_EVERY_MS] or
 * after [TRIM_EVERY_ROWS] new rows, so no scheduler is needed and the table can't outgrow the cap by more
 * than one trim's worth.
 */
internal object TelemetryService {
    const val RETENTION_DAYS = 14L
    const val ROW_CAP = 500_000L
    private const val TRIM_EVERY_MS = 10 * 60 * 1000L
    private const val TRIM_EVERY_ROWS = 20_000L

    private val lastTrimMs = AtomicLong(0)
    private val rowsSinceTrim = AtomicLong(0)

    fun record(
        userId: UUID,
        sessionId: UUID,
        events: List<TelemetryEvent>,
    ) {
        transaction { TelemetryStore.insertInTransaction(userId, sessionId, events) }
        val rows = rowsSinceTrim.addAndGet(events.size.toLong())
        val nowMs = System.currentTimeMillis()
        val last = lastTrimMs.get()
        if (rows < TRIM_EVERY_ROWS && nowMs - last < TRIM_EVERY_MS) return
        if (!lastTrimMs.compareAndSet(last, nowMs)) return
        rowsSinceTrim.set(0)
        trim(OffsetDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC))
    }

    fun trim(
        now: OffsetDateTime,
        cap: Long = ROW_CAP,
    ): Int = transaction { TelemetryStore.trimInTransaction(now.minusDays(RETENTION_DAYS), cap) }
}
