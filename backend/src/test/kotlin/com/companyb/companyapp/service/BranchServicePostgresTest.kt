package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val clinicId = UUID.randomUUID()
    private val provincialTourId = UUID.randomUUID()
    private val medicalMissionId = UUID.randomUUID()
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
        branchIds.forEach { trackOwned(BranchTable, BranchTable.id, it) }

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

        val duplicate = BranchService.create(callerId, clinicId, "Changed Name", BranchType.MEDICAL_MISSION)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Main Clinic", duplicate.branch.name)
        assertEquals(BranchType.CLINIC, duplicate.branch.branchType)
        assertEquals(1L, auditEntryCount(clinicId))
    }

    @Test
    fun `find by id and list return persisted branches`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        branchIds.forEach { trackOwned(BranchTable, BranchTable.id, it) }

        val found = BranchService.findById(medicalMissionId)
        val allBranchIds = BranchService.findAll().map { it.id }.toSet()

        assertEquals("Free Mission", found.name)
        assertTrue(clinicId in allBranchIds)
        assertTrue(medicalMissionId in allBranchIds)
    }

    @Test
    fun `create without MANAGE_USERS is allowed at service layer`() {
        val newBranchId = UUID.randomUUID()
        val result = BranchService.create(callerId, newBranchId, "New Branch", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, newBranchId)
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
        val newBranchId = UUID.randomUUID()
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        BranchService.create(callerId, newBranchId, "Find Branch", BranchType.CLINIC)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(BranchTable, BranchTable.id, newBranchId)

        val otherCaller = UUID.randomUUID()
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
