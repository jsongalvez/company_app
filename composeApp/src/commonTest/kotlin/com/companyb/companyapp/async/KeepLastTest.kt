package com.companyb.companyapp.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the #162 keep-last unifier helpers.
 *
 * [KeepLast] mirrors every Success landing on its state flow into [freshest] and keeps it
 * through Idle/Loading/Error; [freshestValue] is the synchronous exact read for VM-internal
 * decisions, and [mutate] is the in-place write — the exact read, the transform (null = no
 * change), and the Success write in one call (under the test dispatcher the flow's value lags
 * a just-made assignment by one collector hop — tests advance the scheduler before reading a
 * converged flow value, except the deliberate pre-advance lag assertion at the freshestValue
 * test; on Main.immediate it converges inline, see the KeepLast KDoc).
 *
 * [KeepLastByKey] mirrors per-key and coalesces same-key in-flight loads while leaving other
 * keys' loads unblocked (the Remittance tab-switch contract); [KeyedMirror] is its mirror
 * half, for mirror-only consumers (ReliefInvite's `keptSent` — the #166 P5 mirror-only
 * split); [InFlightGuard] is its coalescing set-guard, now composed rather than hand-rolled
 * at the UserVM / FinanceReportsVM / AuditLogVM ack sites (#166). [ActionTracker] composes
 * [InFlightGuard] + a per-key error map — the guard and per-action inline-error bookkeeping
 * adopted by the UserVM / FinanceReportsVM / AuditLogVM action sites (#167).
 *
 * The KeepLast collector runs on the scope passed at construction — the tests pass a scope on
 * the test Main dispatcher (the production shape: VMs pass viewModelScope). Note: a
 * `backgroundScope` collector does NOT run under `advanceUntilIdle` in this runTest(scheduler)
 * shape (probe-verified) — Main is required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepLastTest {
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
    fun keepLast_success_mirrors_into_freshest() =
        runTest(testScheduler) {
            val kept = KeepLast<Int>(CoroutineScope(Dispatchers.Main))
            assertNull(kept.freshest.value)

            kept.stateFlow.value = UiState.Success(1)
            advanceUntilIdle()

            assertEquals(1, kept.freshest.value)
            assertEquals(1, kept.freshestValue())
        }

    @Test
    fun keepLast_loading_and_error_keep_last_payload() =
        runTest(testScheduler) {
            val kept = KeepLast<Int>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(1)
            advanceUntilIdle()

            kept.stateFlow.value = UiState.Loading
            advanceUntilIdle()
            assertEquals(1, kept.freshest.value, "Loading must keep the last payload")

            kept.stateFlow.value = UiState.Error("boom")
            advanceUntilIdle()
            assertEquals(1, kept.freshest.value, "Error must keep the last payload")

            kept.stateFlow.value = UiState.Success(2)
            advanceUntilIdle()
            assertEquals(2, kept.freshest.value, "a fresh Success supersedes the mirror")
        }

    @Test
    fun keepLast_in_place_mutation_writes_mirror_too() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2))
            advanceUntilIdle()

            // In-place mutation write (the #161 mutateUser shape) lands on the same state flow.
            kept.stateFlow.value = UiState.Success(listOf(1))
            advanceUntilIdle()

            assertEquals(listOf(1), kept.freshest.value)
        }

    @Test
    fun keepLast_freshestValue_is_synchronous_and_exact() =
        runTest(testScheduler) {
            val kept = KeepLast<Int>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(7)

            // No scheduler advance: the direct state read must already be exact (mutation
            // transforms read this mid-frame, where the collected flow would still lag).
            assertEquals(7, kept.freshestValue())
            assertNull(kept.freshest.value, "the collected flow converges only after a hop")

            // The mirror converges on the next dispatch under the test dispatcher; the advance
            // runs first so the Loading-window fallback reads the converged mirror (on
            // Main.immediate the collector resumes inline at the Success assignment — see the
            // KeepLast KDoc).
            advanceUntilIdle()
            kept.stateFlow.value = UiState.Loading
            assertEquals(7, kept.freshestValue())
        }

    @Test
    fun mutate_writes_success_and_mirrors_into_freshest() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2))
            advanceUntilIdle()

            val written = kept.mutate { list -> list + 3 }

            assertTrue(written)
            assertEquals(listOf(1, 2, 3), kept.freshestValue(), "the exact read is immediate")
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), kept.freshest.value, "the write mirrors into freshest")
            assertEquals(UiState.Success(listOf(1, 2, 3)), kept.state.value)
        }

    @Test
    fun mutate_is_noop_and_false_when_nothing_loaded() =
        runTest(testScheduler) {
            val kept = KeepLast<Int>(CoroutineScope(Dispatchers.Main))

            var transformRan = false
            val written =
                kept.mutate {
                    transformRan = true
                    it + 1
                }

            assertFalse(written, "nothing loaded — no write")
            assertFalse(transformRan, "the transform must not run against nothing")
            assertNull(kept.freshest.value)
        }

    @Test
    fun mutate_null_transform_is_noop_and_false() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2))
            advanceUntilIdle()

            val written = kept.mutate { null }

            assertFalse(written, "a null transform result means no change")
            assertEquals(listOf(1, 2), kept.freshestValue())
            assertEquals(listOf(1, 2), kept.freshest.value)
        }

    @Test
    fun mutate_from_error_state_mutates_the_mirror_held_list() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2))
            advanceUntilIdle()
            kept.stateFlow.value = UiState.Error("reload failed")
            advanceUntilIdle()

            // The #161 shape: a failed reload leaves Error while the rows still render — a
            // row action must mutate the mirror-held list, and the Success write supersedes
            // Error (the freshest truth for the mutated row).
            val written = kept.mutate { list -> list.filterNot { it == 2 } }

            assertTrue(written)
            assertEquals(UiState.Success(listOf(1)), kept.state.value)
            advanceUntilIdle()
            assertEquals(listOf(1), kept.freshest.value)
        }

    @Test
    fun mutateRemoved_removes_matching_rows_and_returns_true() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2, 2, 3))
            advanceUntilIdle()

            // filterNot semantics: EVERY matching element leaves, not just the first (the
            // moveToReadThisSession / removeReceived call sites rely on it).
            val removed = kept.mutateRemoved { it == 2 }

            assertTrue(removed)
            assertEquals(listOf(1, 3), kept.freshestValue(), "the exact read is immediate")
            advanceUntilIdle()
            assertEquals(listOf(1, 3), kept.freshest.value, "the write mirrors into freshest")
            assertEquals(UiState.Success(listOf(1, 3)), kept.state.value)
        }

    @Test
    fun mutateRemoved_no_match_is_noop_and_false() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))
            kept.stateFlow.value = UiState.Success(listOf(1, 2))
            advanceUntilIdle()

            // No element matched — no write (the no-match path never reaches a write: mutate
            // returns false before assigning). The content asserts below carry the pin; an
            // equal-content write is StateFlow-conflated (old instance kept) so identity could
            // never distinguish it from no-write anyway (P5a).
            val removed = kept.mutateRemoved { it == 9 }

            assertFalse(removed, "no element matched — no change")
            assertTrue(
                kept.state.value is UiState.Success,
                "the state stays Success (mutate only writes Success or nothing)",
            )
            assertEquals(listOf(1, 2), kept.freshestValue())
            advanceUntilIdle()
            assertEquals(listOf(1, 2), kept.freshest.value)
        }

    @Test
    fun mutateRemoved_nothing_loaded_is_noop_and_false() =
        runTest(testScheduler) {
            val kept = KeepLast<List<Int>>(CoroutineScope(Dispatchers.Main))

            val removed = kept.mutateRemoved { it == 1 }

            assertFalse(removed, "nothing loaded — no write")
            assertNull(kept.freshest.value)
            assertTrue(kept.state.value is UiState.Idle, "Idle was never left")
        }

    // ─────────────────────────── KeyedMirror (#166 P5 mirror-only split) ───────────────────────────

    @Test
    fun keyedMirror_commit_mirrors_per_key() =
        runTest(testScheduler) {
            val mirror = KeyedMirror<String, Int>()
            assertTrue(mirror.lastByKey.value.isEmpty(), "no entry before any commit")

            mirror.commit("a", 1)

            assertEquals(1, mirror.lastByKey.value["a"])
            assertEquals(mapOf("a" to 1), mirror.lastByKey.value)
        }

    @Test
    fun keyedMirror_commit_last_writer_wins_per_key_others_untouched() =
        runTest(testScheduler) {
            val mirror = KeyedMirror<String, Int>()
            mirror.commit("a", 1)
            mirror.commit("b", 2)

            mirror.commit("a", 10)

            assertEquals(10, mirror.lastByKey.value["a"], "a same-key re-commit is last-writer-wins")
            assertEquals(2, mirror.lastByKey.value["b"], "another key's entry is untouched")
        }

    @Test
    fun keyedMirror_commit_reads_flow_per_key() =
        runTest(testScheduler) {
            val mirror = KeyedMirror<String, Int>()
            mirror.commit("a", 1)
            mirror.commit("b", 2)

            assertEquals(1, mirror.lastByKey.value["a"])
            assertEquals(2, mirror.lastByKey.value["b"])
            assertNull(mirror.lastByKey.value["c"], "a never-loaded key has no mirror")
            assertEquals(setOf("a", "b"), mirror.lastByKey.value.keys)
        }

    @Test
    fun keepLastByKey_same_key_coalesces_other_keys_unblocked() =
        runTest(testScheduler) {
            val kept = KeepLastByKey<String, Int>()

            assertTrue(kept.tryBegin("a"))
            assertFalse(kept.tryBegin("a"), "a same-key double-fire coalesces")
            assertTrue(kept.tryBegin("b"), "a switch to another key is never skipped")
            assertEquals(setOf("a", "b"), kept.inFlight.value)
        }

    @Test
    fun keepLastByKey_commit_mirrors_and_clears() =
        runTest(testScheduler) {
            val kept = KeepLastByKey<String, Int>()
            assertTrue(kept.tryBegin("a"))

            kept.commit("a", 42)

            assertEquals(42, kept.freshest("a"))
            assertFalse("a" in kept.inFlight.value)
        }

    @Test
    fun keepLastByKey_finish_clears_without_commit_and_is_noop_when_idle() =
        runTest(testScheduler) {
            val kept = KeepLastByKey<String, Int>()
            assertTrue(kept.tryBegin("a"))

            kept.finish("a")

            assertNull(kept.freshest("a"), "finish must not mirror")
            assertFalse("a" in kept.inFlight.value)
            kept.finish("a")
            assertFalse("a" in kept.inFlight.value, "finish is a no-op when not in flight")
        }

    @Test
    fun keepLastByKey_freshest_is_per_key() =
        runTest(testScheduler) {
            val kept = KeepLastByKey<String, Int>()
            kept.commit("a", 1)
            kept.commit("b", 2)

            assertEquals(1, kept.freshest("a"))
            assertEquals(2, kept.freshest("b"))
            assertNull(kept.freshest("c"), "a never-loaded key has no mirror")
        }

    // ─────────────────────────── InFlightGuard (#166) ───────────────────────────

    @Test
    fun inFlightGuard_same_key_coalesces_other_keys_unblocked() =
        runTest(testScheduler) {
            val guard = InFlightGuard<String>()

            assertTrue(guard.tryBegin("a"))
            assertFalse(guard.tryBegin("a"), "a same-key double-fire coalesces")
            assertTrue(guard.tryBegin("b"), "a switch to another key is never skipped")
            assertEquals(setOf("a", "b"), guard.inFlight.value)
        }

    @Test
    fun inFlightGuard_finish_clears_and_is_noop_when_idle() =
        runTest(testScheduler) {
            val guard = InFlightGuard<String>()
            assertTrue(guard.tryBegin("a"))

            guard.finish("a")

            assertFalse("a" in guard.inFlight.value)
            guard.finish("a")
            assertFalse("a" in guard.inFlight.value, "finish is a no-op when not in flight")
        }

    @Test
    fun inFlightGuard_clear_resets_wholesale() =
        runTest(testScheduler) {
            val guard = InFlightGuard<String>()
            guard.tryBegin("a")
            guard.tryBegin("b")

            guard.clear()

            assertTrue(guard.inFlight.value.isEmpty())
            assertTrue(guard.tryBegin("a"), "a cleared key re-arms")
        }

    // ─────────────────────────── ActionTracker (#167) ───────────────────────────

    @Test
    fun actionTracker_tryBegin_takes_marker_and_other_keys_unblocked() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()

            assertTrue(tracker.tryBegin("a"))
            assertFalse(tracker.tryBegin("a"), "a same-key double-fire coalesces")
            assertTrue(tracker.tryBegin("b"), "another key is never blocked")
            assertEquals(setOf("a", "b"), tracker.inFlight.value)
        }

    @Test
    fun actionTracker_fail_records_error_and_clears_marker() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            assertTrue(tracker.tryBegin("a"))

            tracker.fail("a", "boom")

            assertFalse("a" in tracker.inFlight.value, "fail clears the marker")
            assertEquals("boom", tracker.errors.value["a"])
        }

    @Test
    fun actionTracker_finish_clears_without_error_and_is_noop_when_idle() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            assertTrue(tracker.tryBegin("a"))

            tracker.finish("a")

            assertFalse("a" in tracker.inFlight.value)
            assertTrue(tracker.errors.value.isEmpty(), "finish records no error")
            tracker.finish("a")
            assertFalse("a" in tracker.inFlight.value, "finish is a no-op when not in flight")
        }

    @Test
    fun actionTracker_tryBegin_after_fail_clears_stale_error() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            tracker.fail("a", "first attempt failed")

            assertTrue(tracker.tryBegin("a"), "a retry after a failure begins")
            assertTrue("a" in tracker.inFlight.value)
            assertTrue(tracker.errors.value.isEmpty(), "the retry clears the stale error")
        }

    @Test
    fun actionTracker_clear_resets_markers_and_errors() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            tracker.tryBegin("a")
            tracker.tryBegin("b")
            tracker.fail("a", "boom")

            tracker.clear()

            assertTrue(tracker.inFlight.value.isEmpty())
            assertTrue(tracker.errors.value.isEmpty())
            assertTrue(tracker.tryBegin("a"), "a cleared key re-arms")
        }

    @Test
    fun actionTracker_clearErrors_drops_errors_leaves_markers() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            tracker.tryBegin("a")
            tracker.fail("b", "boom")

            tracker.clearErrors()

            assertTrue(tracker.errors.value.isEmpty())
            assertTrue("a" in tracker.inFlight.value, "clearErrors leaves markers untouched")
        }

    @Test
    fun actionTracker_clearWhere_removes_matching_errors_only_and_leaves_markers() =
        runTest(testScheduler) {
            val tracker = ActionTracker<String>()
            tracker.fail("expense:create", "create failed")
            tracker.fail("expense:update:e1", "update failed")
            tracker.fail("comp:create", "create failed")
            tracker.tryBegin("comp:update:c1")

            tracker.clearWhere { key -> key.startsWith("expense:") }

            assertFalse("expense:create" in tracker.errors.value)
            assertFalse("expense:update:e1" in tracker.errors.value)
            assertEquals("create failed", tracker.errors.value["comp:create"])
            assertTrue("comp:update:c1" in tracker.inFlight.value, "clearWhere leaves markers untouched")
        }
}
