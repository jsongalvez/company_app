package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemittanceLineDuplicatePostgresTest : RemittanceLineServicePostgresBase() {
    @Test
    fun `add line idempotent duplicate returns existing`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        val first =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(lineId),
            )
        val versionAfterFirst = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountAfterFirst = remittanceLineAuditCount()

        val second =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(lineId),
            )

        assertEquals(first.id, second.id)
        assertEquals(first.amount, second.amount)
        assertEquals(versionAfterFirst, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountAfterFirst, remittanceLineAuditCount())
    }

    @Test
    fun `add line rejects altered UUID retry payload`() {
        val remittance = createDraftRemittance()
        createSession()
        val secondSessionId = TestFixtures.uuid()
        completeSession(sessionId)
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                NewRemittanceLine(
                    id = lineId,
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = sessionId,
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                ),
            )
        }
        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(lineId, BigDecimal("500.00")),
            )
        }

        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line rejects altered UUID retry source and creator`() {
        val remittance = createDraftRemittance()
        createSession()
        val secondSessionId = TestFixtures.uuid()
        completeSession(sessionId)
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()
        val otherUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUser, "rl")

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                NewRemittanceLine(
                    id = lineId,
                    type = RemittanceLineType.SESSION,
                    sessionId = secondSessionId,
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                ),
            )
        }

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = otherUser,
                remittanceId = remittance.id,
                sessionLine(lineId),
            )
        }

        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line concurrent same UUID returns one line and audit`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(CONCURRENT_LINES)
        val ready = CountDownLatch(CONCURRENT_LINES)
        val start = CountDownLatch(1)
        val futures =
            (1..CONCURRENT_LINES).map {
                executor.submit<UUID> {
                    ready.countDown()
                    start.await()
                    val line =
                        RemittanceService.addLine(
                            callerId = callerId,
                            remittanceId = remittance.id,
                            sessionLine(lineId),
                        )
                    line.id
                }
            }

        try {
            assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, futures.map { it.get() }.toSet().size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals(1, remittanceLineAuditCount())
        assertEquals(1, RemittanceService.getRemittance(remittance.id).lines.size)
    }

    @Test
    fun `add line rejects client ID already used by another remittance`() {
        val firstRemittance = createDraftRemittance()
        val secondRemittance = createDraftRemittance()
        createSession()
        completeSession(sessionId)
        val secondSessionId = TestFixtures.uuid()
        createSession(secondSessionId)
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = firstRemittance.id,
            sessionLine(lineId),
        )
        val firstVersion = RemittanceService.getRemittance(firstRemittance.id).remittance.version
        val versionBefore = RemittanceService.getRemittance(secondRemittance.id).remittance.version
        val auditCountBefore = remittanceLineAuditCount()

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = secondRemittance.id,
                NewRemittanceLine(
                    id = lineId,
                    type = RemittanceLineType.SESSION,
                    sessionId = secondSessionId,
                    productSaleId = null,
                    amount = BigDecimal("2000.00"),
                ),
            )
        }

        assertEquals(firstVersion, RemittanceService.getRemittance(firstRemittance.id).remittance.version)
        assertEquals(versionBefore, RemittanceService.getRemittance(secondRemittance.id).remittance.version)
        assertTrue(RemittanceService.getRemittance(secondRemittance.id).lines.isEmpty())
        assertEquals(auditCountBefore, remittanceLineAuditCount())
    }

    @Test
    fun `add line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()
        createSession()

        val line =
            RemittanceService.addLine(
                callerId = otherUser,
                remittanceId = remittance.id,
                sessionLine(),
            )

        assertNotNull(line)
        assertEquals(RemittanceLineType.SESSION, line.type)
    }

    @Test
    fun `add SESSION line already in another draft returns conflict`() {
        val firstDraft = createDraftRemittance()
        val secondDraft = createDraftRemittance()
        createSession()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = firstDraft.id,
            sessionLine(),
        )

        val error =
            assertFailsWith<ConflictException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = secondDraft.id,
                    sessionLine(),
                )
            }
        assertEquals("Session already included in a remittance line", error.message)
    }

    @Test
    fun `add SESSION line duplicate in same draft with different line id returns conflict`() {
        val remittance = createDraftRemittance()
        createSession()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(),
        )

        val versionBeforeConflict =
            transaction {
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittance.id }
                    .single()[RemittanceTable.version]
            }
        val auditCountBeforeConflict =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq "remittance_line" }
                    .count()
            }

        assertFailsWith<ConflictException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(),
            )
        }

        val versionAfterConflict =
            transaction {
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittance.id }
                    .single()[RemittanceTable.version]
            }
        val auditCountAfterConflict =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq "remittance_line" }
                    .count()
            }
        assertEquals(versionBeforeConflict, versionAfterConflict)
        assertEquals(auditCountBeforeConflict, auditCountAfterConflict)
    }

    @Test
    fun `add PRODUCT_SALE line duplicate returns conflict`() {
        val remittance = createDraftRemittance(RemittanceType.PRODUCT)
        createSession()
        createProductSale()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = null,
                productSaleId = productSaleId,
                amount = BigDecimal("500.00"),
            ),
        )

        val error =
            assertFailsWith<ConflictException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    NewRemittanceLine(
                        id = TestFixtures.uuid(),
                        type = RemittanceLineType.PRODUCT_SALE,
                        sessionId = null,
                        productSaleId = productSaleId,
                        amount = BigDecimal("500.00"),
                    ),
                )
            }
        assertEquals("Product sale already included in a remittance line", error.message)
    }

    @Test
    fun `add SESSION line after soft-delete of previous line succeeds`() {
        val remittance = createDraftRemittance()
        createSession()

        val firstLineId = TestFixtures.uuid()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(firstLineId),
        )
        RemittanceService.removeLine(callerId, remittance.id, firstLineId)

        val reAdded =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(amount = BigDecimal("2000.00")),
            )

        assertNotNull(reAdded)
        assertEquals("2000.00", reAdded.amount.toPlainString())
    }

    @Test
    fun `add line to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = TestFixtures.uuid(),
                NewRemittanceLine(
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.SESSION,
                    sessionId = TestFixtures.uuid(),
                    productSaleId = null,
                    amount = BigDecimal("1500.00"),
                ),
            )
        }
    }

    @Test
    fun `add line to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        createSession()
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<ValidationException> {
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                sessionLine(),
            )
        }
    }
}
