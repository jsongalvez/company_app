package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val clinicId = UUID.randomUUID()
    private val provincialTourId = UUID.randomUUID()
    private val medicalMissionId = UUID.randomUUID()
    private val branchIds = listOf(clinicId, provincialTourId, medicalMissionId)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows(callerId, branchIds)
        insertUser(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows(callerId, branchIds)
        }
    }

    @Test
    fun `create persists all branch types and writes audit row`() {
        grantManageUsers(callerId)

        val clinic = BranchService.create(callerId, clinicId, " Main Clinic ", BranchType.CLINIC)
        val tour = BranchService.create(callerId, provincialTourId, "Cebu Tour", BranchType.PROVINCIAL_TOUR)
        val mission = BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

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
        grantManageUsers(callerId)
        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)

        val duplicate = BranchService.create(callerId, clinicId, "Changed Name", BranchType.MEDICAL_MISSION)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Main Clinic", duplicate.branch.name)
        assertEquals(BranchType.CLINIC, duplicate.branch.branchType)
        assertEquals(1L, auditEntryCount(clinicId))
    }

    @Test
    fun `find by id and list return persisted branches`() {
        grantManageUsers(callerId)
        BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        val found = BranchService.findById(medicalMissionId)
        val allBranchIds = BranchService.findAll().map { it.id }.toSet()

        assertEquals("Free Mission", found.name)
        assertTrue(clinicId in allBranchIds)
        assertTrue(medicalMissionId in allBranchIds)
    }

    @Test
    fun `create without MANAGE_USERS is forbidden and does not insert branch`() {
        assertFailsWith<ForbiddenResponse> {
            BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        }

        assertFalse(branchExists(clinicId))
        assertEquals(0L, auditEntryCount(clinicId))
    }

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "branch-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Branch Caller",
        )
    }

    private fun grantManageUsers(userId: UUID) {
        DatabaseTestHelper.grantManageUsers(userId, sourceId)
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
            extractJsonField(row[AuditLogTable.newValue] ?: "{}", "name")
        }

    private fun deleteTestRows(
        userId: UUID,
        branchIds: List<UUID>,
    ) {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq userId) or (AuditLogTable.recordId eq branchIds[0]) or
                    (AuditLogTable.recordId eq branchIds[1]) or (AuditLogTable.recordId eq branchIds[2])
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            BranchTable.deleteWhere {
                (BranchTable.id eq branchIds[0]) or (BranchTable.id eq branchIds[1]) or
                    (BranchTable.id eq branchIds[2])
            }
            AppUserTable.deleteWhere { AppUserTable.id eq userId }
        }
    }

    private companion object {
        private val json = Json

        private fun extractJsonField(
            jsonString: String,
            field: String,
        ): String {
            val jsonElement = json.parseToJsonElement(jsonString)
            return jsonElement.jsonObject[field]?.jsonPrimitive?.content ?: ""
        }
    }
}
