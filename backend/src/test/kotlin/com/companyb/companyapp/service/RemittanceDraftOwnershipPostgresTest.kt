package com.companyb.companyapp.service
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.service.finance.remittance.Remittance
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemittanceDraftOwnershipPostgresTest : BasePostgresTest() {
    private companion object {
        const val CONCURRENT_ATTEMPTS = 2
    }

    private val callerId = TestFixtures.uuid()
    private val firstBranchId = TestFixtures.uuid()
    private val secondBranchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "remittance-owner")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        listOf(firstBranchId to "First", secondBranchId to "Second").forEach { (id, name) ->
            DatabaseTestHelper.insertTestBranch(id, "Remittance $name Branch ${TestFixtures.uuid()}")
            trackOwned(BranchTable, BranchTable.id, id)
            trackOwned(BranchDayTable, BranchDayTable.branchId, id)
        }
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create draft UUID collision across branches returns conflict`() {
        val remittanceId = TestFixtures.uuid()
        val rangeStart = LocalDate.of(2026, 7, 1)
        val rangeEnd = LocalDate.of(2026, 7, 15)

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = firstBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )

        assertFailsWith<ConflictException> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = secondBranchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = rangeStart,
                dateRangeEnd = rangeEnd,
            )
        }

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName)
                    }.count()
            }
        assertEquals(1, auditCount)
    }

    @Test
    fun `concurrent draft UUID collision across branches returns one conflict`() {
        val remittanceId = TestFixtures.uuid()
        val rangeStart = LocalDate.of(2026, 7, 1)
        val rangeEnd = LocalDate.of(2026, 7, 15)
        val ready = CountDownLatch(CONCURRENT_ATTEMPTS)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS)

        val results =
            listOf(firstBranchId, secondBranchId).map { branchIdForAttempt ->
                executor.submit<Result<Remittance>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        RemittanceService.createDraft(
                            callerId = callerId,
                            id = remittanceId,
                            type = RemittanceType.SESSION,
                            branchId = branchIdForAttempt,
                            method = RemittanceMethod.BANK_TRANSFER,
                            dateRangeStart = rangeStart,
                            dateRangeEnd = rangeEnd,
                        )
                    }
                }
            }
        assertEquals(true, ready.await(10, TimeUnit.SECONDS))
        start.countDown()
        val outcomes = results.map { it.get() }
        executor.shutdown()
        assertEquals(true, executor.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.exceptionOrNull() is ConflictException })
        val persistedBranch =
            transaction {
                RemittanceTable
                    .selectAll()
                    .where {
                        RemittanceTable.id eq remittanceId
                    }.single()[RemittanceTable.branchId]
            }
        assertEquals(true, persistedBranch == firstBranchId || persistedBranch == secondBranchId)
        assertEquals(
            1L,
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName)
                    }.count()
            },
        )
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `drafts with same branch type and date can coexist`() {
        val firstId = TestFixtures.uuid()
        val secondId = TestFixtures.uuid()
        val rangeStart = LocalDate.of(2026, 8, 1)
        val rangeEnd = LocalDate.of(2026, 8, 15)

        RemittanceService.createDraft(
            callerId = callerId,
            id = firstId,
            type = RemittanceType.SESSION,
            branchId = firstBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        RemittanceService.createDraft(
            callerId = callerId,
            id = secondId,
            type = RemittanceType.SESSION,
            branchId = firstBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )

        assertEquals(
            2L,
            transaction {
                RemittanceTable.selectAll().where { RemittanceTable.branchId eq firstBranchId }.count()
            },
        )
        trackOwned(RemittanceTable, RemittanceTable.id, firstId)
        trackOwned(RemittanceTable, RemittanceTable.id, secondId)
    }

    @Test
    fun `submitted remittance overlap remains rejected`() {
        val firstId = TestFixtures.uuid()
        val secondId = TestFixtures.uuid()
        val submittedDate = LocalDate.of(2026, 8, 20)
        val insertRemittance = { id: UUID ->
            RemittanceTable.insert {
                it[RemittanceTable.id] = id
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.branchId] = firstBranchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = submittedDate
                it[RemittanceTable.submittedBy] = callerId
                it[RemittanceTable.dateRangeStart] = LocalDate.of(2026, 8, 1)
                it[RemittanceTable.dateRangeEnd] = LocalDate.of(2026, 8, 5)
            }
        }

        transaction { insertRemittance(firstId) }
        trackOwned(RemittanceTable, RemittanceTable.id, firstId)
        assertFailsWith<org.jetbrains.exposed.v1.exceptions.ExposedSQLException> {
            transaction { insertRemittance(secondId) }
        }
    }
}
