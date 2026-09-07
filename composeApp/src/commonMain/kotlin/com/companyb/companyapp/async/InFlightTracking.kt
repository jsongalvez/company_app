package com.companyb.companyapp.async

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-key in-flight coalescer (the #162 P5 per-key in-flight guard graduate): the identical
 * `if (key in set) return; set + key` … `set - key` guard, once. Owns the in-flight marker
 * set; [tryBegin] is synchronous by construction (direct value reads/writes — no dispatch, so
 * a same-frame double-tap coalesces; the #143 in-flight shape: a state-based guard would race
 * the handler's Loading assignment, which is not guaranteed to land before launch returns).
 * [finish] is a no-op for keys not in flight, so failure paths can call it unconditionally.
 * [clear] resets wholesale — the superseded-context escape (FinanceReports' edit-panel close;
 * see [clear] for the re-armed-marker hazard it opens).
 */
class InFlightGuard<K> {
    private val _inFlight = MutableStateFlow<Set<K>>(emptySet())
    val inFlight: StateFlow<Set<K>> = _inFlight.asStateFlow()

    /**
     * Mark [key] in flight unless it already is. Returns false (and does nothing) when a
     * request is already running for [key]; true when this call took the marker.
     */
    fun tryBegin(key: K): Boolean {
        if (key in _inFlight.value) return false
        _inFlight.value = _inFlight.value + key
        return true
    }

    /** Clear the marker for [key]. No-op when [key] is not in flight. */
    fun finish(key: K) {
        _inFlight.value = _inFlight.value - key
    }

    /**
     * Clear every marker — for superseded contexts whose in-flight work is now inert.
     * Re-arming a key after [clear] while its superseded request still runs re-opens the
     * cross-wire: the stale request's unconditional [finish] would remove the NEWER marker
     * (the set-subtract cannot tell whose marker it is) and enable a duplicate dispatch.
     * Adopters must gate stale terminal paths so a superseded completion never touches a
     * re-armed marker — the FinanceReports generation guard is that gate.
     */
    fun clear() {
        _inFlight.value = emptySet()
    }
}

/**
 * Per-key action tracker (the #166 P5 per-key-action-tracker graduate): the guard + per-key
 * error-map pair — "successful `tryBegin` ⇒ clear that key's inline error" — once. Composes
 * [InFlightGuard] for the coalescing marker and owns the per-key error map: [tryBegin] marks the
 * key in flight AND clears its stale error, both gated on the marker being free (the
 * check-before-clear order all adopters used); [fail] records a key's error on a terminal
 * failure path while clearing its marker; [finish] clears the marker on a success path.
 *
 * The tracker owns the error map WITHOUT message semantics: [fail]'s [String] is stored as
 * given and never interpreted, formatted, or derived here — message construction stays at the
 * call sites (the #166-P5 caveat: message-bearing coupling must not leak into the unifier).
 *
 * [clear] resets both the markers and the errors wholesale — the superseded-context escape
 * (an edit-panel close superseding its section actions) — and inherits [InFlightGuard.clear]'s
 * cross-wire hazard: adopters gate stale terminal paths (the FinanceReports generation guard)
 * so a superseded completion never touches a re-armed marker. [clearErrors] resets only the
 * errors — the fresh-data-supersedes-stale-errors shape (a reload replacing the list whose
 * errors describe pre-reload actions).
 */
class ActionTracker<K> {
    private val inFlightGuard = InFlightGuard<K>()
    val inFlight: StateFlow<Set<K>> = inFlightGuard.inFlight

    private val _errors = MutableStateFlow<Map<K, String>>(emptyMap())
    val errors: StateFlow<Map<K, String>> = _errors.asStateFlow()

    /**
     * Mark [key] in flight and clear its stale error. Returns false (and does nothing — the
     * stale error stays) when a request is already running for [key]; true when this call took
     * the marker and cleared the error.
     */
    fun tryBegin(key: K): Boolean {
        if (!inFlightGuard.tryBegin(key)) return false
        _errors.value = _errors.value - key
        return true
    }

    /** Clear the in-flight marker for [key] on a success terminal path. No-op when idle. */
    fun finish(key: K) = inFlightGuard.finish(key)

    /** Clear the marker and record [message] for [key] on a terminal failure path. */
    fun fail(
        key: K,
        message: String,
    ) {
        inFlightGuard.finish(key)
        _errors.value = _errors.value + (key to message)
    }

    /**
     * Reset wholesale: every marker and every error. Re-arming a key while its superseded
     * request still runs re-opens the [InFlightGuard.clear] cross-wire — adopters gate stale
     * terminal paths.
     */
    fun clear() {
        inFlightGuard.clear()
        _errors.value = emptyMap()
    }

    /**
     * Drop EVERY error, markers untouched — the wholesale fresh-data-supersedes-stale-errors
     * shape (a reload replacing the list whose errors describe pre-reload actions); contrast
     * [clearWhere], which drops only the matching family, and [clear], the superseded-context
     * escape that also clears the markers.
     */
    fun clearErrors() {
        _errors.value = emptyMap()
    }

    /**
     * Drop the errors whose keys match [predicate] — the family-scoped fresh-data-supersedes-
     * stale-errors shape (a section reload clearing its own action-error family); contrast
     * [clearErrors], the wholesale variant. Markers are untouched.
     */
    fun clearWhere(predicate: (K) -> Boolean) {
        _errors.value = _errors.value.filterKeys { key -> !predicate(key) }
    }
}
