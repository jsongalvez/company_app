package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionBaseRateServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rateId = UUID.randomUUID()
    private val rateId2 = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "rate-caller")
        DatabaseTestHelper.insertTestBranch(branchId, name = "Rate-Clinic-$branchId")
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `set rate for branch creates rate and writes audit`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        val result = SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "2500.00")

        assertTrue(result.created)
        assertEquals(SessionType.REGULAR, result.rate.sessionType)
        assertEquals("2500.00", result.rate.rate.toPlainString())
        assertEquals(branchId, result.rate.branchId)
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `set rate without MANAGE_PRODUCTS throws forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "2500.00")
        }
    }

    @Test
    fun `set rate with invalid amount throws bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        assertFailsWith<BadRequestResponse> {
            SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "not-a-number")
        }
    }

    @Test
    fun `set rate with negative amount throws bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        assertFailsWith<BadRequestResponse> {
            SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "-100.00")
        }
    }

    @Test
    fun `setting second rate for same branch and type deactivates first`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        val first = SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "2500.00")
        val second = SessionBaseRateService.setRate(callerId, rateId2, branchId, SessionType.REGULAR, "3000.00")

        assertTrue(first.created)
        assertTrue(second.created)

        val activeRates = SessionBaseRateService.findActiveRates(callerId, branchId)
        assertEquals(1, activeRates.size)
        assertEquals("3000.00", activeRates[0].rate.toPlainString())
    }

    @Test
    fun `find active rates returns only current rates`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "2500.00")
        SessionBaseRateService.setRate(callerId, rateId2, branchId, SessionType.SECOND_SESSION, "2000.00")

        val activeRates = SessionBaseRateService.findActiveRates(callerId, branchId)

        assertEquals(2, activeRates.size)
    }

    @Test
    fun `duplicate idempotent set rate returns existing row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        val first = SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "2500.00")
        val duplicate = SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, "3000.00")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("2500.00", duplicate.rate.rate.toPlainString())
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `find rates for branch with no rates returns empty`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val rates = SessionBaseRateService.findActiveRates(callerId, branchId)
        assertTrue(rates.isEmpty())
    }

    @Test
    fun `findActiveRates without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            SessionBaseRateService.findActiveRates(callerId, branchId)
        }
    }

    private fun auditEntryCount(rateId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionBaseRateTable.tableName) and
                        (AuditLogTable.recordId eq rateId)
                }.count()
        }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.recordId eq rateId) or
                    (AuditLogTable.recordId eq rateId2)
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            SessionBaseRateTable.deleteWhere {
                (SessionBaseRateTable.branchId eq branchId) or
                    (SessionBaseRateTable.id eq rateId) or
                    (SessionBaseRateTable.id eq rateId2)
            }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }
}
