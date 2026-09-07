package com.companyb.companyapp.async

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-key keep-last mirror (the #166 P5 mirror-only split): the map mirror of the last
 * successful payload per key — [commit] + [lastByKey] — WITHOUT the [InFlightGuard]. For
 * consumers whose same-key ordering is closed by a stamp instead of a guard (ReliefInvite's
 * `keptSent` newest-launch-wins via `sentStamp`), the composed guard was dead weight.
 * [KeepLastByKey] composes this mirror with the [InFlightGuard] for the full shape
 * (Remittance).
 */
class KeyedMirror<K, T> {
    private val _lastByKey = MutableStateFlow<Map<K, T>>(emptyMap())
    val lastByKey: StateFlow<Map<K, T>> = _lastByKey.asStateFlow()

    /**
     * Mirror a successful payload for [key]: the map becomes `lastByKey + (key to data)`
     * (last-writer-wins per key). The caller's ordering guard decides who may commit.
     */
    fun commit(
        key: K,
        data: T,
    ) {
        _lastByKey.value = _lastByKey.value + (key to data)
    }
}

/**
 * Per-key keep-last-results + in-flight guard (the #161 port shape, unified by #162): a map
 * mirror of the last successful payload per key, plus the [InFlightGuard] — absorbs
 * Remittance's `_lastByTab` + `listLoadsInFlight` manual set bookkeeping. Per-key, NOT
 * single-slot: a switch to another key while one key's load is in flight must not skip the
 * new key's fetch; a same-key double-fire coalesces.
 *
 * Composes [KeyedMirror] (the mirror half) + [InFlightGuard] (the #166 P5 mirror-only
 * split); consumers needing only the mirror — ReliefInvite's `keptSent` — use [KeyedMirror]
 * directly.
 *
 * [commit] mirrors + clears the guard together (the mirror and the in-flight marker must never
 * disagree); [finish] is a no-op for keys not in flight, so failure paths can call it
 * unconditionally.
 */
class KeepLastByKey<K, T> {
    private val mirror = KeyedMirror<K, T>()
    val lastByKey: StateFlow<Map<K, T>> = mirror.lastByKey

    private val inFlightGuard = InFlightGuard<K>()
    val inFlight: StateFlow<Set<K>> = inFlightGuard.inFlight

    /**
     * Begin a load for [key] unless one is already in flight for it. Synchronous
     * check-and-add, coalescing — see [InFlightGuard] for the #143 in-flight shape rationale.
     * Returns false (and does nothing) when a load is already running for [key].
     */
    fun tryBegin(key: K): Boolean = inFlightGuard.tryBegin(key)

    /**
     * Commit a successful payload for [key]: mirror it and clear the in-flight marker.
     * Safe to call when [key] is not in flight (the marker removal is a no-op then).
     */
    fun commit(
        key: K,
        data: T,
    ) {
        mirror.commit(key, data)
        inFlightGuard.finish(key)
    }

    /**
     * Clear the in-flight marker without committing (failure / non-success / exception paths).
     * No-op when [key] is not in flight.
     */
    fun finish(key: K) = inFlightGuard.finish(key)

    /** The last successful payload for [key], or null when that key never loaded. */
    fun freshest(key: K): T? = mirror.lastByKey.value[key]
}
