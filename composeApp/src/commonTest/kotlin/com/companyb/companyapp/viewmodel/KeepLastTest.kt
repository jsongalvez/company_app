package com.companyb.companyapp.viewmodel

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
 * keys' loads unblocked (the Remittance tab-switch contract).
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

            // Identity pin (captured BEFORE the call so a write inside it cannot hide under
            // the baseline): a content-CHANGING write would land a fresh Success instance and
            // fail the ===. An equal-value write is StateFlow-conflated (the update is skipped,
            // the old instance kept), so identity cannot distinguish "no write" from a
            // same-content write — but the no-match path never reaches a write anyway (mutate
            // returns false before assigning); the assert catches the realistic regression, a
            // no-match that mutates the list (inverted predicate → new instance → fails).
            val before = kept.state.value
            val removed = kept.mutateRemoved { it == 9 }

            assertFalse(removed, "no element matched — no change")
            advanceUntilIdle()
            assertTrue(kept.state.value === before, "no content-changing Success write happened")
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
}
