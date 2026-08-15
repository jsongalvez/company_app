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
 * through Loading/Error; [freshestValue] is the synchronous exact read for VM-internal
 * mutation transforms (the flow's value can lag a just-made assignment by one collector hop).
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

            // The mirror converges on the next dispatch under the test dispatcher; the assert
            // advances first so the Loading-window fallback reads the converged mirror (on
            // Main.immediate the collector resumes inline at the Success assignment — see the
            // KeepLast KDoc).
            advanceUntilIdle()
            kept.stateFlow.value = UiState.Loading
            assertEquals(7, kept.freshestValue())
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
