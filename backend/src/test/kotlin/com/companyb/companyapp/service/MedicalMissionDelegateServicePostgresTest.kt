package com.companyb.companyapp.service
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedicalMissionDelegateServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchName = "Mission-${branchId.toString().take(8)}"

    override fun initTestData() {
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "delegate-caller-$callerId",
            passwordHash = "test-password-hash",
            email = "${callerId.toString().take(8)}@t.st",
            displayName = "Delegate Caller",
        )
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertUser(
            id = targetUserId,
            username = "delegate-target-$targetUserId",
            passwordHash = "test-password-hash",
            email = "${targetUserId.toString().take(8)}@t.st",
            displayName = "Delegate Target",
        )
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.grantAssignDelegate(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        insertBranch()
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, targetUserId)
    }

    @Test
    fun `successful assign creates delegate and user_capability`() {
        val delegateId = TestFixtures.uuid()

        val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

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
        val delegateId = TestFixtures.uuid()

        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
        val duplicate = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertEquals(delegateId, duplicate.id)
        assertEquals(targetUserId, duplicate.targetUser)
        assertEquals(branchId, duplicate.branchId)
        assertNull(duplicate.endedAt)
    }

    @Test
    fun `assign without ASSIGN_DELEGATE is allowed at service layer`() {
        val delegateId = TestFixtures.uuid()
        val noCapCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = noCapCaller,
            username = "no-cap-$noCapCaller",
            passwordHash = "test-password-hash",
            email = "${noCapCaller.toString().take(8)}@t.st",
            displayName = "No Capability",
        )

        trackOwned(AppUserTable, AppUserTable.id, noCapCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, noCapCaller)

        val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, noCapCaller)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

        assertEquals(delegateId, result.id)
        assertEquals(targetUserId, result.targetUser)
    }

    @Test
    fun `successful revoke sets ended_at and expires user_capability`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

        val result = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        assertEquals(delegateId, result.id)
        assertNotNull(result.endedAt)
        assertTrue(delegateHasEndedAt(delegateId))
        assertTrue(capabilityIsExpired(delegateId))
    }

    @Test
    fun `retrying revoked delegate id returns ended row without regranting capability`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
        MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        val retry = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertNotNull(retry.endedAt)
        assertEquals(0L, activeCapabilityCount(delegateId))
        assertEquals(2L, delegateAuditCount(delegateId))
    }

    @Test
    fun `revoke on non-existent delegate fails with 404`() {
        assertFailsWith<NotFoundException> {
            MedicalMissionDelegateService.revokeDelegate(TestFixtures.uuid(), callerId)
        }
    }

    @Test
    fun `revoke without ASSIGN_DELEGATE is allowed at service layer`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

        val noCapCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = noCapCaller,
            username = "no-cap-$noCapCaller",
            passwordHash = "test-password-hash",
            email = "${noCapCaller.toString().take(8)}@t.st",
            displayName = "No Capability",
        )
        trackOwned(AppUserTable, AppUserTable.id, noCapCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, noCapCaller)

        val result = MedicalMissionDelegateService.revokeDelegate(delegateId, noCapCaller)

        assertEquals(delegateId, result.id)
        assertNotNull(result.endedAt)
        assertTrue(delegateHasEndedAt(delegateId))
    }

    @Test
    fun `assign writes audit log entry`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

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
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
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

    private fun activeCapabilityCount(delegateId: UUID): Long =
        transaction {
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.sourceId eq delegateId) and
                        (UserCapabilityTable.sourceType eq CapabilitySourceType.MEDICAL_MISSION_DELEGATE) and
                        UserCapabilityTable.validTo.isNull()
                }.count()
        }

    private fun delegateAuditCount(delegateId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "medical_mission_delegate") and
                        (AuditLogTable.recordId eq delegateId)
                }.count()
        }
}
