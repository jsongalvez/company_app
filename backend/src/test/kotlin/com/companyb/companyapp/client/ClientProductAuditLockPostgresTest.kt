package com.companyb.companyapp.client

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.ProductRepository
import com.companyb.companyapp.commerce.ProductService
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #522 — client/product updates capture their audit before-image under a
 * materialized row lock, so concurrent writers form a truthful serial chain.
 * Each probe holds the row on one connection while the real service command
 * runs on a second connection and blocks on that row. The holder then commits
 * its own change; the contender's audit must show the holder's value as its
 * predecessor — never the stale pre-holder image.
 */
class ClientProductAuditLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "audit-lock-caller")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
    }

    @Test
    fun `disjoint client updates keep each audit to its own field`() {
        val clientId = TestFixtures.uuid()
        createClient(clientId, firstName = ORIG_FIRST, lastName = ORIG_LAST)
        val latches = Latches()
        val holder =
            startHolder(
                latches,
                lock = { ClientRepository.acquireLockInTransaction(clientId) ?: error("client missing") },
                write = {
                    ClientTable.update({ ClientTable.id eq clientId }) {
                        it[ClientTable.firstName] = HOLDER_FIRST
                    }
                },
            )
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val contenderFailure =
                contendAndRelease(latches, holder) {
                    updateClientLastName(clientId, CONTENDER_LAST)
                }
            assertEquals(null, contenderFailure, "contender failed: $contenderFailure")

            val persisted = findClient(clientId)
            assertEquals(HOLDER_FIRST, persisted.first)
            assertEquals(CONTENDER_LAST, persisted.second)

            val updates = clientUpdates(clientId)
            assertEquals(1, updates.size, "holder writes no audit; only the contender UPDATE remains")
            val (oldJson, newJson) = updates.single()
            assertEquals(ORIG_LAST, TestFixtures.extractJsonField(oldJson, "lastName"))
            assertEquals(CONTENDER_LAST, TestFixtures.extractJsonField(newJson, "lastName"))
            assertEquals("", TestFixtures.extractJsonField(oldJson, "firstName"))
            assertEquals("", TestFixtures.extractJsonField(newJson, "firstName"))
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `same-field client updates chain through the holder value`() {
        val clientId = TestFixtures.uuid()
        createClient(clientId, firstName = ORIG_FIRST, lastName = ORIG_LAST)
        val latches = Latches()
        val holder =
            startHolder(
                latches,
                lock = { ClientRepository.acquireLockInTransaction(clientId) ?: error("client missing") },
                write = {
                    ClientTable.update({ ClientTable.id eq clientId }) {
                        it[ClientTable.firstName] = HOLDER_FIRST
                    }
                },
            )
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val contenderFailure =
                contendAndRelease(latches, holder) {
                    updateClientFirstName(clientId, CONTENDER_FIRST)
                }
            assertEquals(null, contenderFailure, "contender failed: $contenderFailure")

            assertEquals(CONTENDER_FIRST, findClient(clientId).first)
            val (oldJson, newJson) = clientUpdates(clientId).single()
            assertEquals(HOLDER_FIRST, TestFixtures.extractJsonField(oldJson, "firstName"))
            assertEquals(CONTENDER_FIRST, TestFixtures.extractJsonField(newJson, "firstName"))
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `disjoint product updates keep each audit to its own field`() {
        val productId = TestFixtures.uuid()
        createProduct(productId, name = ORIG_PRODUCT, price = ORIG_PRICE_DECIMAL)
        val latches = Latches()
        val holder =
            startHolder(
                latches,
                lock = { ProductRepository.findByIdForUpdateInTransaction(productId) ?: error("product missing") },
                write = {
                    ProductTable.update({ ProductTable.id eq productId }) {
                        it[ProductTable.name] = HOLDER_PRODUCT
                    }
                },
            )
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val contenderFailure =
                contendAndRelease(latches, holder) {
                    ProductService.update(
                        callerId = callerId,
                        productId = productId,
                        name = null,
                        productCategoryId = null,
                        unitPrice = CONTENDER_PRICE,
                        commissionAmount = null,
                        isActive = null,
                    )
                }
            assertEquals(null, contenderFailure, "contender failed: $contenderFailure")

            val persisted = findProduct(productId)
            assertEquals(HOLDER_PRODUCT, persisted.first)
            assertEquals(CONTENDER_PRICE, persisted.second)

            val updates = productUpdates(productId)
            assertEquals(1, updates.size, "holder writes no audit; only the contender UPDATE remains")
            val (oldJson, newJson) = updates.single()
            assertEquals(ORIG_PRICE, TestFixtures.extractJsonField(oldJson, "unitPrice"))
            assertEquals(CONTENDER_PRICE_STRING, TestFixtures.extractJsonField(newJson, "unitPrice"))
            assertEquals("", TestFixtures.extractJsonField(oldJson, "name"))
            assertEquals("", TestFixtures.extractJsonField(newJson, "name"))
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `same-field product updates chain through the holder value`() {
        val productId = TestFixtures.uuid()
        createProduct(productId, name = ORIG_PRODUCT, price = ORIG_PRICE_DECIMAL)
        val latches = Latches()
        val holder =
            startHolder(
                latches,
                lock = { ProductRepository.findByIdForUpdateInTransaction(productId) ?: error("product missing") },
                write = {
                    ProductTable.update({ ProductTable.id eq productId }) {
                        it[ProductTable.name] = HOLDER_PRODUCT
                    }
                },
            )
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val contenderFailure =
                contendAndRelease(latches, holder) {
                    ProductService.update(
                        callerId = callerId,
                        productId = productId,
                        name = CONTENDER_PRODUCT,
                        productCategoryId = null,
                        unitPrice = null,
                        commissionAmount = null,
                        isActive = null,
                    )
                }
            assertEquals(null, contenderFailure, "contender failed: $contenderFailure")

            assertEquals(CONTENDER_PRODUCT, findProduct(productId).first)
            val (oldJson, newJson) = productUpdates(productId).single()
            assertEquals(HOLDER_PRODUCT, TestFixtures.extractJsonField(oldJson, "name"))
            assertEquals(CONTENDER_PRODUCT, TestFixtures.extractJsonField(newJson, "name"))
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `concurrent update racing anonymize cannot resurrect PII`() {
        val clientId = TestFixtures.uuid()
        createClient(clientId, firstName = ORIG_FIRST, lastName = ORIG_LAST)
        val latches = Latches()
        val holder =
            thread {
                transaction {
                    val before =
                        ClientRepository.acquireLockInTransaction(clientId) ?: error("client missing")
                    latches.holderReady.countDown()
                    assertTrue(latches.releaseHolder.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    ClientRepository.anonymizeInTransaction(clientId)
                    val after =
                        ClientRepository.findByIdInTransaction(clientId) ?: error("client missing after anonymize")
                    ClientAudit.updated(callerId, before, after)
                }
            }
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val contenderFailure =
                contendAndRelease(latches, holder) {
                    updateClientFirstName(clientId, RESURRECT_FIRST)
                }
            assertTrue(contenderFailure is NotFoundException, "expected 404, got $contenderFailure")

            val persisted = findClient(clientId)
            assertEquals(null, persisted.first)
            assertEquals(null, persisted.second)
            assertEquals(2L, clientAuditCount(clientId), "create + anonymize only; blocked write audits nothing")
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    private fun updateClientLastName(
        id: UUID,
        lastName: String,
    ) {
        ClientService.update(
            callerId = callerId,
            clientId = id,
            firstName = null,
            lastName = lastName,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = null,
            age = null,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun updateClientFirstName(
        id: UUID,
        firstName: String,
    ) {
        ClientService.update(
            callerId = callerId,
            clientId = id,
            firstName = firstName,
            lastName = null,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = null,
            age = null,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun startHolder(
        latches: Latches,
        lock: () -> Any?,
        write: () -> Unit,
    ): Thread =
        thread {
            transaction {
                lock()
                latches.holderReady.countDown()
                assertTrue(latches.releaseHolder.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                write()
            }
        }

    private fun contendAndRelease(
        latches: Latches,
        holder: Thread,
        update: () -> Unit,
    ): Throwable? {
        var failure: Throwable? = null
        val contender = thread { failure = runCatching { update() }.exceptionOrNull() }
        Thread.sleep(READINESS_MILLIS)
        assertTrue(contender.isAlive, "contender finished without contending the row")
        latches.releaseHolder.countDown()
        contender.join(JOIN_MILLIS)
        assertTrue(!contender.isAlive, "contender still alive after the row lock was released")
        holder.join(JOIN_MILLIS)
        return failure
    }

    private fun createClient(
        id: UUID,
        firstName: String,
        lastName: String,
    ) {
        ClientService.create(
            callerId = callerId,
            id = id,
            firstName = firstName,
            lastName = lastName,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = Gender.M,
            age = CLIENT_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun createProduct(
        id: UUID,
        name: String,
        price: BigDecimal,
    ) {
        ProductService.create(
            callerId = callerId,
            id = id,
            name = name,
            productCategoryId = categoryId,
            unitPrice = price,
            commissionAmount = COMMISSION,
        )
    }

    private fun findClient(id: UUID): Pair<String?, String?> =
        transaction {
            ClientTable
                .selectAll()
                .where { ClientTable.id eq id }
                .single()
                .let { it[ClientTable.firstName] to it[ClientTable.lastName] }
        }

    private fun findProduct(id: UUID): Pair<String, BigDecimal> =
        transaction {
            ProductTable
                .selectAll()
                .where { ProductTable.id eq id }
                .single()
                .let { it[ProductTable.name] to it[ProductTable.unitPrice] }
        }

    private fun clientUpdates(id: UUID): List<Pair<String, String>> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ClientTable.tableName) and
                        (AuditLogTable.recordId eq id) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.map { (it[AuditLogTable.oldValue] ?: "") to (it[AuditLogTable.newValue] ?: "") }
        }

    private fun productUpdates(id: UUID): List<Pair<String, String>> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ProductTable.tableName) and
                        (AuditLogTable.recordId eq id) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.map { (it[AuditLogTable.oldValue] ?: "") to (it[AuditLogTable.newValue] ?: "") }
        }

    private fun clientAuditCount(id: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ClientTable.tableName) and
                        (AuditLogTable.recordId eq id)
                }.count()
        }

    private class Latches {
        val holderReady = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
    }

    companion object {
        private const val ORIG_FIRST = "OrigFirst"
        private const val ORIG_LAST = "OrigLast"
        private const val HOLDER_FIRST = "HolderFirst"
        private const val CONTENDER_FIRST = "ContenderFirst"
        private const val CONTENDER_LAST = "ContenderLast"
        private const val RESURRECT_FIRST = "Resurrect"
        private const val ORIG_PRODUCT = "OrigProduct"
        private const val HOLDER_PRODUCT = "HolderProduct"
        private const val CONTENDER_PRODUCT = "ContenderProduct"
        private const val ORIG_PRICE = "100.00"
        private const val CONTENDER_PRICE_STRING = "200.00"
        private const val CLIENT_AGE = 30
        private const val READINESS_MILLIS = 1000L
        private const val JOIN_MILLIS = 30000L
        private const val LATCH_TIMEOUT_SECONDS = 30L
        private val ORIG_PRICE_DECIMAL = BigDecimal(ORIG_PRICE)
        private val CONTENDER_PRICE = BigDecimal(CONTENDER_PRICE_STRING)
        private val COMMISSION = BigDecimal("10.00")
    }
}
