package com.companyb.companyapp.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Keep-last-results, VM-side (the #143/#161 port shape, unified by #162): a [UiState] flow plus
 * the "freshest" payload — Success data when the state is Success, else the last successful
 * payload — so the screen keeps rendering the last list across Loading/Error and composition
 * re-entries (a screen-side `remember` would die on re-entry; the VM is entry-scoped).
 *
 * The freshest-list decision (formerly `(state as? UiState.Success)?.data ?: last` re-implemented
 * at 4+ call sites: UserVM mutate/swap, UserManagementScreen gate, NotificationVM
 * currentUnreadList, ReliefInviteVM currentReceivedList) lives here once.
 *
 * Usage: pass [stateFlow] to [ApiCallHandler.launch] (the handler assigns Loading/Error/Success
 * on it); screens collect [freshest] for the render payload; VM-internal mutation writes go
 * through [mutate] — the exact-sync-read + changed-guard + Success-write discipline in one
 * call (the #162 P5 keep-last-mutation-write graduate) — and synchronous reads use
 * [freshestValue], exact where the [freshest] flow's value can lag a just-made assignment by
 * one collector hop under test dispatchers (on Main.immediate it converges inline — see
 * [freshestValue]).
 */
class KeepLast<T>(
    scope: CoroutineScope,
) {
    // The mutable state flow, for [ApiCallHandler.launch] and synchronous pre-sets (e.g. the
    // #161 state-as-guard Loading pre-set). Every Success landing on it — loads AND in-place
    // mutation writes — mirrors into [freshest]. `state` is its read-only projection.
    val stateFlow = MutableStateFlow<UiState<T>>(UiState.Idle)
    val state: StateFlow<UiState<T>> = stateFlow.asStateFlow()

    private val _freshest = MutableStateFlow<T?>(null)

    /**
     * The freshest renderable payload: Success data when the state is Success, else the last
     * successful payload. Null only when nothing has ever loaded (screens show a spinner /
     * error card then). Collect this; do NOT read `.value` for VM-internal decisions — use
     * [freshestValue] (under test dispatchers this flow's value can lag a just-made state
     * assignment by one collector hop; on Main.immediate it converges inline).
     */
    val freshest: StateFlow<T?> = _freshest.asStateFlow()

    /**
     * Synchronous freshest read for VM-internal decisions (mutation transforms must read exact
     * current values — never a collected snapshot; the #141 stale-snapshot class). Reads the
     * state directly, so it is exact even mid-collector-hop. The mirror fallback (reached only
     * during any non-Success state — Idle/Loading/Error) is exact on Main.immediate — the
     * collector resumes inline at every Success assignment, so any later read sees the
     * converged mirror; under test dispatchers it can lag one hop until the scheduler advances
     * (tests advance before reading).
     */
    fun freshestValue(): T? = (stateFlow.value as? UiState.Success<T>)?.data ?: _freshest.value

    /**
     * In-place mutation write (the #162 P5 keep-last-mutation-write graduate): the exact
     * synchronous read, the transform, the changed-guard, and the Success write — the
     * hand-rolled mutation discipline at the UserVM/NotificationVM/ReliefInviteVM sites,
     * once.
     *
     * Returns true when a Success was written; false when nothing was ever loaded (the
     * no-op-when-nothing-loaded guard: with no list rendered there is nothing to mutate in
     * place — the caller's action should no-op, not dead-tap) or the transform returned null
     * (the changed-guard: no write happened, so e.g. a badge decrement must not fire). The
     * transform runs synchronously in the caller's frame — no dispatch, so reads it makes
     * (other VM state) are exact at the write, and writes it makes land before the Success
     * write.
     */
    fun mutate(transform: (T) -> T?): Boolean {
        val current = freshestValue() ?: return false
        val updated = transform(current) ?: return false
        stateFlow.value = UiState.Success(updated)
        return true
    }

    init {
        // Single writer for the freshest flow: every Success landing on the state flow (loads
        // AND in-place mutation writes, including [mutateRemoved]'s) mirrors into it. Relies
        // on the FIFO-Main ordering contract — viewModelScope dispatches on Main.immediate, so
        // a state assignment queues this collector BEFORE any later-started coroutine's resume
        // (a dispatcher change would silently re-open the #141 resurrect window).
        scope.launch {
            stateFlow
                .filterIsInstance<UiState.Success<T>>()
                .collect { state -> _freshest.value = state.data }
        }
    }
}

/**
 * Remove-or-noop mutation write (the #163 P5 mutateRemoved graduate): the remove sub-shape of
 * [KeepLast.mutate], the identical `filterNot + size-compare` block formerly hand-rolled at
 * NotificationVM.moveToReadThisSession + ReliefInviteVM.removeReceived — once.
 *
 * Extension on `KeepLast<List<T>>` because the remove shape is only meaningful when the kept
 * payload is a list: the element predicate cannot be expressed against an arbitrary `T`.
 *
 * Returns [KeepLast.mutate]'s Boolean: true when a matching row left (a Success was written);
 * false when nothing was ever loaded or no element matched — the caller's follow-up (badge
 * decrement, read-session append) must fire only on true. `filterNot` semantics: EVERY matching
 * element leaves, not just the first.
 */
fun <T> KeepLast<List<T>>.mutateRemoved(predicate: (T) -> Boolean): Boolean =
    mutate { current ->
        val remaining = current.filterNot(predicate)
        if (remaining.size == current.size) null else remaining
    }

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
 * Per-key keep-last-results + in-flight guard (the #161 port shape, unified by #162): a map
 * mirror of the last successful payload per key, plus the [InFlightGuard] — absorbs
 * Remittance's `_lastByTab` + `listLoadsInFlight` manual set bookkeeping. Per-key, NOT
 * single-slot: a switch to another key while one key's load is in flight must not skip the
 * new key's fetch; a same-key double-fire coalesces.
 *
 * [commit] mirrors + clears the guard together (the mirror and the in-flight marker must never
 * disagree); [finish] is a no-op for keys not in flight, so failure paths can call it
 * unconditionally.
 */
class KeepLastByKey<K, T> {
    private val _lastByKey = MutableStateFlow<Map<K, T>>(emptyMap())
    val lastByKey: StateFlow<Map<K, T>> = _lastByKey.asStateFlow()

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
        _lastByKey.value = _lastByKey.value + (key to data)
        inFlightGuard.finish(key)
    }

    /**
     * Clear the in-flight marker without committing (failure / non-success / exception paths).
     * No-op when [key] is not in flight.
     */
    fun finish(key: K) = inFlightGuard.finish(key)

    /** The last successful payload for [key], or null when that key never loaded. */
    fun freshest(key: K): T? = _lastByKey.value[key]
}
