package com.companyb.companyapp.service
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val clinicId = TestFixtures.uuid()
    private val provincialTourId = TestFixtures.uuid()
    private val medicalMissionId = TestFixtures.uuid()
    private val branchIds = listOf(clinicId, provincialTourId, medicalMissionId)

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
    }

    @Test
    fun `create persists all branch types and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val clinic = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        val tour = BranchService.create(callerId, provincialTourId, "Cebu Tour", BranchType.PROVINCIAL_TOUR)
        val mission = BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        branchIds.forEach { id ->
            trackOwned(BranchTable, BranchTable.id, id)
            // #418 — branch creation seeds five default base rates as a side effect.
            trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, id)
        }

        assertTrue(clinic.created)
        assertTrue(tour.created)
        assertTrue(mission.created)
        assertEquals("Main Clinic", clinic.branch.name)
        assertEquals(BranchType.CLINIC, persistedBranchType(clinicId))
        assertEquals(BranchType.PROVINCIAL_TOUR, persistedBranchType(provincialTourId))
        assertEquals(BranchType.MEDICAL_MISSION, persistedBranchType(medicalMissionId))
        assertEquals(1L, auditEntryCount(clinicId))
        assertEquals("Main Clinic", auditNewName(clinicId))
    }

    @Test
    fun `duplicate client generated id returns existing branch without extra audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, clinicId)
        // #418 — branch creation seeds five default base rates as a side effect.
        trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, clinicId)

        val duplicate = BranchService.create(callerId, clinicId, "Changed Name", BranchType.MEDICAL_MISSION)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Main Clinic", duplicate.branch.name)
        assertEquals(BranchType.CLINIC, duplicate.branch.branchType)
        assertEquals(1L, auditEntryCount(clinicId))
    }

    @Test
    fun `duplicate branch name and type is rejected`() {
        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, clinicId)
        trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, clinicId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        val failure =
            assertFailsWith<ConflictException> {
                BranchService.create(callerId, TestFixtures.uuid(), "Main Clinic", BranchType.CLINIC)
            }

        assertTrue(first.created)
        assertEquals("A branch with this name and type already exists", failure.message)
    }

    @Test
    fun `find by id and list return persisted branches`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        branchIds.forEach { id ->
            trackOwned(BranchTable, BranchTable.id, id)
            // #418 — branch creation seeds five default base rates as a side effect.
            trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, id)
        }

        val found = BranchService.findById(medicalMissionId)
        val allBranchIds = BranchService.findAll().map { it.id }.toSet()

        assertEquals("Free Mission", found.name)
        assertTrue(clinicId in allBranchIds)
        assertTrue(medicalMissionId in allBranchIds)
    }

    @Test
    fun `create without MANAGE_USERS is allowed at service layer`() {
        val newBranchId = TestFixtures.uuid()
        val result = BranchService.create(callerId, newBranchId, "New Branch", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, newBranchId)
        // #418 — branch creation seeds five default base rates as a side effect.
        trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, newBranchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        assertTrue(result.created)
        assertTrue(branchExists(newBranchId))
        assertEquals(1L, auditEntryCount(newBranchId))
    }

    @Test
    fun `findAll without MANAGE_USERS is allowed at service layer`() {
        BranchService.findAll()
    }

    @Test
    fun `findById without MANAGE_USERS is allowed at service layer`() {
        val newBranchId = TestFixtures.uuid()
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        BranchService.create(callerId, newBranchId, "Find Branch", BranchType.CLINIC)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(BranchTable, BranchTable.id, newBranchId)
        // #418 — branch creation seeds five default base rates as a side effect.
        trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, newBranchId)

        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)

        val found = BranchService.findById(newBranchId)
        assertEquals("Find Branch", found.name)
    }

    private fun persistedBranchType(branchId: UUID): BranchType =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .single()[BranchTable.branchType]
        }

    private fun branchExists(branchId: UUID): Boolean =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .empty()
                .not()
        }

    private fun auditEntryCount(branchId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { (AuditLogTable.auditTableName eq "branch") and (AuditLogTable.recordId eq branchId) }
                .count()
        }

    private fun auditNewName(branchId: UUID): String =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where { (AuditLogTable.auditTableName eq "branch") and (AuditLogTable.recordId eq branchId) }
                    .orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()
            DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "name")
        }
}
