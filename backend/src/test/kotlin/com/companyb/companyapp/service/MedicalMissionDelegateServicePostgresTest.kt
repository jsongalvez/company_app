package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedicalMissionDelegateServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchName = "Mission-${branchId.toString().take(8)}"

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "delegate-caller-$callerId",
            passwordHash = "test-password-hash",
            email = "${callerId.toString().take(8)}@t.st",
            displayName = "Delegate Caller",
        )
        DatabaseTestHelper.insertUser(
            id = targetUserId,
            username = "delegate-target-$targetUserId",
            passwordHash = "test-password-hash",
            email = "${targetUserId.toString().take(8)}@t.st",
            displayName = "Delegate Target",
        )
        DatabaseTestHelper.grantAssignDelegate(callerId, sourceId)
        insertBranch()
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `successful assign creates delegate and user_capability`() {
        val delegateId = UUID.randomUUID()

        val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertEquals(delegateId, result.id)
        assertEquals(targetUserId, result.targetUser)
        assertEquals(branchId, result.branchId)
        assertEquals(callerId, result.assignedBy)
        assertNull(result.endedAt)
        assertTrue(delegateExists(delegateId))
        assertTrue(capabilityExistsForDelegate(delegateId, targetUserId, branchId))
    }

    @Test
    fun `duplicate delegate id returns existing row (idempotent)`() {
        val delegateId = UUID.randomUUID()

        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        val duplicate = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertEquals(delegateId, duplicate.id)
        assertEquals(targetUserId, duplicate.targetUser)
        assertEquals(branchId, duplicate.branchId)
        assertNull(duplicate.endedAt)
    }

    @Test
    fun `assign without ASSIGN_DELEGATE capability fails with 403`() {
        val noCapCaller = UUID.randomUUID()
        DatabaseTestHelper.insertUser(
            id = noCapCaller,
            username = "no-cap-$noCapCaller",
            passwordHash = "test-password-hash",
            email = "${noCapCaller.toString().take(8)}@t.st",
            displayName = "No Capability",
        )

        assertFailsWith<ForbiddenResponse> {
            MedicalMissionDelegateService.assignDelegate(UUID.randomUUID(), targetUserId, branchId, noCapCaller)
        }
    }

    @Test
    fun `successful revoke sets ended_at and expires user_capability`() {
        val delegateId = UUID.randomUUID()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        val result = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        assertEquals(delegateId, result.id)
        assertNotNull(result.endedAt)
        assertTrue(delegateHasEndedAt(delegateId))
        assertTrue(capabilityIsExpired(delegateId))
    }

    @Test
    fun `revoke on non-existent delegate fails with 404`() {
        assertFailsWith<NotFoundResponse> {
            MedicalMissionDelegateService.revokeDelegate(UUID.randomUUID(), callerId)
        }
    }

    @Test
    fun `revoke without ASSIGN_DELEGATE capability fails with 403`() {
        val delegateId = UUID.randomUUID()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        val noCapCaller = UUID.randomUUID()
        DatabaseTestHelper.insertUser(
            id = noCapCaller,
            username = "no-cap-$noCapCaller",
            passwordHash = "test-password-hash",
            email = "${noCapCaller.toString().take(8)}@t.st",
            displayName = "No Capability",
        )

        assertFailsWith<ForbiddenResponse> {
            MedicalMissionDelegateService.revokeDelegate(delegateId, noCapCaller)
        }
    }

    @Test
    fun `assign writes audit log entry`() {
        val delegateId = UUID.randomUUID()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "medical_mission_delegate") and
                            (AuditLogTable.recordId eq delegateId)
                    }.count()
            }
        assertEquals(1L, auditCount)
    }

    @Test
    fun `revoke writes audit log entry`() {
        val delegateId = UUID.randomUUID()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "medical_mission_delegate") and
                            (AuditLogTable.recordId eq delegateId)
                    }.count()
            }
        assertEquals(2L, auditCount)
    }

    private fun insertBranch() {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = branchId
                it[BranchTable.name] = branchName
                it[BranchTable.branchType] = BranchType.MEDICAL_MISSION
            }
        }
    }

    private fun delegateExists(delegateId: UUID): Boolean =
        transaction {
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.id eq delegateId }
                .empty()
                .not()
        }

    private fun delegateHasEndedAt(delegateId: UUID): Boolean =
        transaction {
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.id eq delegateId }
                .single()[MedicalMissionDelegateTable.endedAt] != null
        }

    private fun capabilityExistsForDelegate(
        delegateId: UUID,
        userId: UUID,
        contextId: UUID,
    ): Boolean =
        transaction {
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.sourceId eq delegateId) and
                        (UserCapabilityTable.userId eq userId) and
                        (UserCapabilityTable.contextId eq contextId) and
                        (UserCapabilityTable.contextType eq CapabilityContextType.BRANCH) and
                        (UserCapabilityTable.sourceType eq CapabilitySourceType.MEDICAL_MISSION_DELEGATE) and
                        (UserCapabilityTable.validTo.isNull())
                }.empty()
                .not()
        }

    private fun capabilityIsExpired(delegateId: UUID): Boolean =
        transaction {
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.sourceId eq delegateId) and
                        (UserCapabilityTable.sourceType eq CapabilitySourceType.MEDICAL_MISSION_DELEGATE) and
                        (UserCapabilityTable.validTo.isNotNull())
                }.empty()
                .not()
        }

    private fun deleteTestRows() {
        transaction {
            UserCapabilityTable.deleteWhere {
                (UserCapabilityTable.userId eq callerId) or (UserCapabilityTable.userId eq targetUserId)
            }
            MedicalMissionDelegateTable.deleteWhere {
                (MedicalMissionDelegateTable.targetUser eq targetUserId) or
                    (MedicalMissionDelegateTable.assignedBy eq callerId)
            }
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or (AuditLogTable.changedBy eq targetUserId)
            }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq callerId) or (AppUserTable.id eq targetUserId)
            }
        }
    }
}
