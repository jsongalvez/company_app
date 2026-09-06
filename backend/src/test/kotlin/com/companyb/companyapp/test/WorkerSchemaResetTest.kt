package com.companyb.companyapp.test

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * #494 — behavioral proof for owned-schema reset. No row is ever registered;
 * reset erases side-effect writes, preserves seeds, and never toggles the
 * snapshot immutability trigger.
 */
class WorkerSchemaResetTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @Test
    fun `reset erases unregistered side-effect rows and preserves seeds`() {
        val seedsBefore = seedCounts()
        val callerId = TestFixtures.uuid()
        val branchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(callerId, "reset-proof")
        BranchService.create(callerId, branchId, "Reset Branch", BranchType.CLINIC)
        assertTrue(baseRateCount(branchId) >= 1, "branch create must seed base rates")
        assertTrue(branchCount(branchId) == 1L, "branch row must exist before reset")

        DatabaseTestHelper.resetWorkerSchema()

        assertEquals(0L, branchCount(branchId), "branch row must disappear after reset")
        assertEquals(0, baseRateCount(branchId), "side-effect base rates must disappear after reset")
        assertEquals(0L, userCount(callerId), "fixture user must disappear after reset")
        assertEquals(seedsBefore, seedCounts(), "seed reference rows must survive reset")
    }

    @Test
    fun `reset allows repeat setup and keeps snapshot trigger enforced`() {
        val callerId = TestFixtures.uuid()
        val branchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(callerId, "reset-repeat")
        BranchService.create(callerId, branchId, "Repeat Branch", BranchType.CLINIC)
        val remittanceId = insertSubmittedRemittance(branchId, callerId)
        assertUpdateBlocked(remittanceId)

        DatabaseTestHelper.resetWorkerSchema()

        DatabaseTestHelper.insertTestUser(callerId, "reset-repeat")
        BranchService.create(callerId, branchId, "Repeat Branch", BranchType.CLINIC)
        assertEquals(1L, branchCount(branchId), "repeat setup must work after reset")
        val secondRemittance = insertSubmittedRemittance(branchId, callerId)
        assertUpdateBlocked(secondRemittance)
    }

    private fun seedCounts(): Triple<Long, Long, Long> =
        transaction {
            Triple(
                RoleTable.selectAll().count(),
                CapabilityTable.selectAll().count(),
                transactionSeedHistoryCount(),
            )
        }

    private fun transactionSeedHistoryCount(): Long =
        transaction {
            exec("SELECT count(*) FROM flyway_schema_history") { rs ->
                rs.next()
                rs.getLong(1)
            } ?: 0L
        }

    private fun baseRateCount(branchId: java.util.UUID): Int =
        transaction {
            SessionBaseRateTable
                .selectAll()
                .where { SessionBaseRateTable.branchId eq branchId }
                .count()
                .toInt()
        }

    private fun branchCount(branchId: java.util.UUID): Long =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .count()
        }

    private fun userCount(userId: java.util.UUID): Long =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .count()
        }

    private fun insertSubmittedRemittance(
        branchId: java.util.UUID,
        callerId: java.util.UUID,
    ): java.util.UUID {
        val remittanceId = TestFixtures.uuid()
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.branchId] = branchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = LocalDate.of(2026, 7, 15)
                it[RemittanceTable.submittedBy] = callerId
                it[RemittanceTable.dateRangeStart] = LocalDate.of(2026, 7, 1)
                it[RemittanceTable.dateRangeEnd] = LocalDate.of(2026, 7, 15)
            }
            RemittanceFinancialSnapshotTable.insert {
                it[RemittanceFinancialSnapshotTable.remittanceId] = remittanceId
                it[RemittanceFinancialSnapshotTable.grossIncome] = BigDecimal("500.00")
                it[RemittanceFinancialSnapshotTable.totalCompensation] = BigDecimal("100.00")
                it[RemittanceFinancialSnapshotTable.totalExpenses] = BigDecimal("50.00")
                it[RemittanceFinancialSnapshotTable.netIncome] = BigDecimal("350.00")
            }
        }
        return remittanceId
    }

    private fun assertUpdateBlocked(remittanceId: java.util.UUID) {
        assertFailsWith<Exception> {
            transaction {
                RemittanceFinancialSnapshotTable.update(
                    { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId },
                ) {
                    it[RemittanceFinancialSnapshotTable.netIncome] = BigDecimal("0.00")
                }
            }
        }
    }
}
