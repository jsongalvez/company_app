package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayRepository
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * #518 — sale and movement share one lock order (day → product → card), so a sale
 * racing a movement on the same day/product cannot cross-deadlock.
 *
 * A holder transaction keeps the day row locked while the actual
 * [InventoryService.recordMovement] path runs on a second connection and blocks on
 * that day row. The holder then requests the product row with the same locked read
 * movement uses. Post-fix movement waits on the day row holding nothing, so the
 * holder acquires the product row at once; pre-fix movement already holds the
 * product row, so the two transactions cross-deadlock and the detector aborts one
 * side. The verdict is lock-determined, not timing-determined: the only wait is a
 * readiness margin letting movement reach its blocked state (~10ms of queries; the
 * margin is orders of magnitude larger), with an alive-check proving it is blocked
 * rather than finished.
 *
 * Exposed retries top-level transactions ([TransactionManager.defaultMaxAttempts] is
 * 3), which silently heals a deadlock victim and would mask the cycle — so the
 * probe runs with a single attempt and restores the default afterwards.
 * [ProductSaleService.sell] is the unchanged reference side of the order (day, then
 * product, then card), so only the changed movement path needs the probe; a serial
 * sell afterwards proves both paths still compose on the same rows with
 * exactly-once ledger/audit effects.
 */
class SaleMovementLockOrderPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "sale-movement-lock-caller")
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
    }

    @Test
    fun `movement blocked on day row never holds product row`() {
        val branchId = TestFixtures.uuid()
        val categoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(branchId, "Lock Order Branch ${TestFixtures.uuid()}")
        DatabaseTestHelper.insertTestCategory(categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        val dayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        seedCard(branchId, productId)
        val cardId = cardId(branchId, productId)
        val movementId = TestFixtures.uuid()

        val transactionManager = TransactionManager.manager
        val savedAttempts = transactionManager.defaultMaxAttempts
        transactionManager.defaultMaxAttempts = SINGLE_ATTEMPT
        val dayHeld = CountDownLatch(1)
        val releaseProbe = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(1)
        try {
            val (holderFailure, movementFailure) =
                runProbe(executor, dayHeld, releaseProbe, RaceSetup(branchId, dayId, productId, movementId))

            val cycleEvidence = listOfNotNull(holderFailure, movementFailure).firstOrNull(::isDeadlock)
            if (cycleEvidence != null) {
                fail(
                    "recordMovement holds the product row while waiting for the day row " +
                        "(day → product → card order violated): $cycleEvidence",
                )
            }
            if (holderFailure != null) fail("day/product holder failed unexpectedly: $holderFailure")
            if (movementFailure != null) fail("movement failed unexpectedly: $movementFailure")

            assertComposedState(branchId, dayId, productId, cardId, movementId)
        } finally {
            transactionManager.defaultMaxAttempts = savedAttempts
            releaseProbe.countDown()
            executor.shutdownNow()
            executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun runProbe(
        executor: ExecutorService,
        dayHeld: CountDownLatch,
        releaseProbe: CountDownLatch,
        setup: RaceSetup,
    ): Pair<Throwable?, Throwable?> {
        val holderFuture =
            executor.submit<String> {
                transaction {
                    BranchDayRepository.acquireLockInTransaction(setup.dayId)
                        ?: error("branch day missing for ${setup.dayId}")
                    dayHeld.countDown()
                    releaseProbe.await()
                    ProductRepository.findByIdForUpdateInTransaction(setup.productId)
                        ?: error("product missing for ${setup.productId}")
                    "acquired"
                }
            }
        assertTrue(dayHeld.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        var movementFailure: Throwable? = null
        val mover =
            thread {
                movementFailure =
                    runCatching {
                        movementAttempt(setup.movementId, setup.branchId, setup.dayId, setup.productId)
                    }.exceptionOrNull()
            }
        Thread.sleep(READINESS_MILLIS)
        if (!mover.isAlive) {
            fail("movement finished while the day row was held (failure=$movementFailure)")
        }
        releaseProbe.countDown()
        val holderFailure =
            runCatching { holderFuture.get(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.exceptionOrNull()
        mover.join(JOIN_MILLIS)
        assertTrue(!mover.isAlive, "movement still alive after the day lock was released")
        return holderFailure to movementFailure
    }

    private fun assertComposedState(
        branchId: UUID,
        dayId: UUID,
        productId: UUID,
        cardId: UUID,
        movementId: UUID,
    ) {
        val saleId = TestFixtures.uuid()
        sellAttempt(saleId, branchId, dayId, productId)

        assertEquals(1L, saleCount(saleId), "sale must commit exactly once")
        assertEquals(1L, movementCount(movementId), "movement must commit exactly once")
        assertEquals(EXPECTED_STOCK, currentStock(branchId, productId), "stock is wrong")
        assertEquals(EXPECTED_CARD_VERSION, currentCardVersion(branchId, productId), "card version is wrong")
        assertEquals(
            1L,
            auditCount(ProductSaleTable.tableName, saleId, AuditAction.INSERT),
            "sale audit is wrong",
        )
        assertEquals(
            1L,
            auditCount(InventoryMovementTable.tableName, movementId, AuditAction.INSERT),
            "movement audit is wrong",
        )
        assertEquals(
            CARD_UPDATE_AUDITS,
            auditCount(BranchInventoryTable.tableName, cardId, AuditAction.UPDATE),
            "card audit is wrong",
        )
    }

    private fun sellAttempt(
        saleId: UUID,
        branchId: UUID,
        dayId: UUID,
        productId: UUID,
    ) = ProductSaleService.sell(
        callerId = callerId,
        id = saleId,
        branchDayId = dayId,
        sessionId = null,
        clientId = null,
        isWalkIn = true,
        productId = productId,
        quantity = SALE_QTY,
        expectedVersion = currentCardVersion(branchId, productId),
    )

    private fun movementAttempt(
        movementId: UUID,
        branchId: UUID,
        dayId: UUID,
        productId: UUID,
    ) = InventoryService.recordMovement(
        callerId = callerId,
        movementId = movementId,
        branchId = branchId,
        productId = productId,
        movementType = MovementType.Restock,
        quantityChange = RESTOCK_QTY,
        notes = null,
        branchDayId = dayId,
    )

    private fun isDeadlock(failure: Throwable): Boolean =
        generateSequence<Throwable>(failure) { it.cause }.any { cause ->
            cause.message?.contains("deadlock", ignoreCase = true) == true ||
                cause.message?.contains("40P01") == true
        }

    private fun seedCard(
        branchId: UUID,
        productId: UUID,
    ) {
        transaction {
            BranchInventoryTable.insertIgnore {
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[BranchInventoryTable.currentStock] = INITIAL_STOCK
                it[BranchInventoryTable.version] = 1
            }
        }
    }

    private fun cardId(
        branchId: UUID,
        productId: UUID,
    ): UUID =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId)
                }.single()[BranchInventoryTable.id]
        }

    private fun currentStock(
        branchId: UUID,
        productId: UUID,
    ): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId)
                }.single()[BranchInventoryTable.currentStock]
        }

    private fun currentCardVersion(
        branchId: UUID,
        productId: UUID,
    ): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId)
                }.single()[BranchInventoryTable.version]
        }

    private fun saleCount(saleId: UUID): Long =
        transaction {
            ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count()
        }

    private fun movementCount(movementId: UUID): Long =
        transaction {
            InventoryMovementTable.selectAll().where { InventoryMovementTable.id eq movementId }.count()
        }

    private fun auditCount(
        tableName: String,
        recordId: UUID,
        action: AuditAction,
    ): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId) and
                        (AuditLogTable.action eq action)
                }.count()
        }

    private data class RaceSetup(
        val branchId: UUID,
        val dayId: UUID,
        val productId: UUID,
        val movementId: UUID,
    )

    companion object {
        private const val INITIAL_STOCK = 20
        private const val RESTOCK_QTY = 5
        private const val SALE_QTY = 2
        private const val EXPECTED_STOCK = 23
        private const val EXPECTED_CARD_VERSION = 3
        private const val CARD_UPDATE_AUDITS = 2L
        private const val SINGLE_ATTEMPT = 1
        private const val READINESS_MILLIS = 5000L
        private const val JOIN_MILLIS = 30000L
        private const val EXECUTOR_TIMEOUT_SECONDS = 30L
        private const val HOLDER_TIMEOUT_SECONDS = 60L
    }
}
