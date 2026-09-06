package com.companyb.companyapp.branch
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
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
        IdentityFixtures.insertTestUser(callerId, "caller")
    }

    @Test
    fun `create persists all branch types and writes audit row`() {
        IdentityFixtures.grantManageUsers(callerId, sourceId)

        val clinic = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        val tour = BranchService.create(callerId, provincialTourId, "Cebu Tour", BranchType.PROVINCIAL_TOUR)
        val mission = BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        branchIds.forEach { id ->
            // #418 — branch creation seeds five default base rates as a side effect.
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
        IdentityFixtures.grantManageUsers(callerId, sourceId)

        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        // #418 — branch creation seeds five default base rates as a side effect.

        val duplicate = BranchService.create(callerId, clinicId, "Changed Name", BranchType.MEDICAL_MISSION)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Main Clinic", duplicate.branch.name)
        assertEquals(BranchType.CLINIC, duplicate.branch.branchType)
        assertEquals(1L, auditEntryCount(clinicId))
    }

    @Test
    fun `duplicate branch name and type is rejected`() {
        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)

        val failure =
            assertFailsWith<ConflictException> {
                BranchService.create(callerId, TestFixtures.uuid(), "Main Clinic", BranchType.CLINIC)
            }

        assertTrue(first.created)
        assertEquals("A branch with this name and type already exists", failure.message)
    }

    @Test
    fun `find by id and list return persisted branches`() {
        IdentityFixtures.grantManageUsers(callerId, sourceId)

        BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        branchIds.forEach { id ->
            // #418 — branch creation seeds five default base rates as a side effect.
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
        // #418 — branch creation seeds five default base rates as a side effect.

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
        IdentityFixtures.grantManageUsers(callerId, sourceId)
        BranchService.create(callerId, newBranchId, "Find Branch", BranchType.CLINIC)
        // #418 — branch creation seeds five default base rates as a side effect.

        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "other")

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
            TestFixtures.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "name")
        }
}
