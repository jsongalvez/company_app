package com.companyb.companyapp.observability

import com.companyb.companyapp.dto.IncidentPacket
import java.util.Collections

/**
 * #475 — bounded in-memory dedup for incident packets. Repeat reports for one
 * trace id collapse to the first packet (user hammer + 5xx auto-file for the
 * same request stay one issue). Last-1000 window mirrors [RateLimiter]'s cap.
 */
internal object IncidentRegistry {
    private const val MAX_ENTRIES = 1000
    private const val INITIAL_CAPACITY = 64
    private const val LOAD_FACTOR = 0.75f

    private val filed: MutableMap<String, IncidentPacket> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, IncidentPacket>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, IncidentPacket>): Boolean = size > MAX_ENTRIES
            },
        )

    /** Returns the stored packet plus whether this trace id was already filed. */
    fun fileIfAbsent(
        traceId: String,
        packet: IncidentPacket,
    ): Pair<IncidentPacket, Boolean> =
        synchronized(filed) {
            val existing = filed[traceId]
            if (existing != null) {
                existing to true
            } else {
                filed[traceId] = packet
                packet to false
            }
        }

    fun find(traceId: String): IncidentPacket? = synchronized(filed) { filed[traceId] }

    /** Test-only: clears filed packets so dedup tests are order-independent. */
    internal fun resetForTest() {
        synchronized(filed) { filed.clear() }
    }
}
