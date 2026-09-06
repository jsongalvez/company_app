package com.companyb.companyapp.test

import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.service.branchday.BranchDayService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Observable row-lock barrier for day-gate concurrency tests (#527).
 *
 * The holder keeps the contested row locked inside one open transaction while the
 * contender runs the production gate on its own connection. Release happens only after
 * `pg_stat_activity` shows a backend blocked by the holder's pid, so the test proves real
 * lock contention instead of sleeping a fixed hold. All waits are bounded; worker results
 * and holder failures surface to the caller.
 */
object LockBarrier {
    private const val NO_PID = -1
    private const val ACQUIRE_SECONDS = 10L
    private const val CONTENDED_SECONDS = 10L
    private const val DONE_SECONDS = 30L
    private const val RELEASE_SECONDS = 30L
    private const val JOIN_MILLIS = 30_000L
    private const val POLL_MILLIS = 25L
    private const val REQUIRED_CONFIRMATIONS = 2
    private const val HOLDER_NAME = "lock-barrier-holder"
    private const val CONTENDER_NAME = "lock-barrier-contender"

    fun <T> withDayRemitBarrier(
        branchDayId: UUID,
        contender: () -> T,
    ): T =
        withHeldLock(
            lock = { BranchDayService.lockDaysInTransaction(listOf(branchDayId)) },
            finish = { BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId)) },
            contender = contender,
        )

    fun <T> withSessionBarrier(
        sessionId: UUID,
        contender: () -> T,
    ): T =
        withHeldLock(
            lock = {
                SessionRepository.acquireLockInTransaction(sessionId)
                    ?: error("session not found for lock barrier: $sessionId")
            },
            finish = {},
            contender = contender,
        )

    private fun <T> withHeldLock(
        lock: () -> Unit,
        finish: () -> Unit,
        contender: () -> T,
    ): T {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holderPid = AtomicInteger(NO_PID)
        val holderFailure = AtomicReference<Throwable>(null)
        val holder =
            Thread {
                try {
                    transaction {
                        lock()
                        val pid =
                            exec("SELECT pg_backend_pid()") { rs ->
                                rs.next()
                                rs.getInt(1)
                            } ?: NO_PID
                        holderPid.set(pid)
                        locked.countDown()
                        release.await(RELEASE_SECONDS, TimeUnit.SECONDS)
                        finish()
                    }
                } catch (failure: Throwable) {
                    holderFailure.set(failure)
                    locked.countDown()
                }
            }.apply {
                isDaemon = true
                name = HOLDER_NAME
            }
        holder.start()
        try {
            check(locked.await(ACQUIRE_SECONDS, TimeUnit.SECONDS)) { "holder never acquired the row lock" }
            holderFailure.get()?.let { throw it }
            return runContender(release, holderPid.get(), holder, holderFailure, contender)
        } finally {
            release.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    private fun <T> runContender(
        release: CountDownLatch,
        holderPid: Int,
        holder: Thread,
        holderFailure: AtomicReference<Throwable>,
        contender: () -> T,
    ): T {
        val executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, CONTENDER_NAME).apply { isDaemon = true }
            }
        try {
            val future = executor.submit(Callable(contender))
            val observed = awaitBlockedBy(holderPid, future)
            release.countDown()
            val outcome = settle(future)
            holder.join(JOIN_MILLIS)
            holderFailure.get()?.let { throw it }
            check(observed) { "contender never blocked on the expected row lock" }
            return outcome.getOrThrow()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun <T> awaitBlockedBy(
        holderPid: Int,
        future: Future<T>,
    ): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONTENDED_SECONDS)
        var confirmations = 0
        while (System.nanoTime() < deadlineNanos && !future.isDone) {
            if (isBlockedBy(holderPid)) {
                confirmations += 1
                if (confirmations >= REQUIRED_CONFIRMATIONS) {
                    return true
                }
            } else {
                confirmations = 0
            }
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    private fun isBlockedBy(holderPid: Int): Boolean {
        if (holderPid == NO_PID) {
            return false
        }
        return transaction {
            val waiting =
                exec(
                    "SELECT count(*) FROM pg_stat_activity " +
                        "WHERE pg_blocking_pids(pid) @> ARRAY[$holderPid] AND wait_event_type = 'Lock'",
                ) { rs ->
                    rs.next()
                    rs.getLong(1)
                } ?: 0L
            waiting > 0L
        }
    }

    private fun <T> settle(future: Future<T>): Result<T> =
        try {
            Result.success(future.get(DONE_SECONDS, TimeUnit.SECONDS))
        } catch (failure: ExecutionException) {
            Result.failure(failure.cause ?: failure)
        }
}
