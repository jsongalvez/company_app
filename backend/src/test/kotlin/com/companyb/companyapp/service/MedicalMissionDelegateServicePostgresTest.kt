package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
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
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
        DatabaseTestHelper.insertUser(
            id = targetUserId,
            username = "delegate-target-$targetUserId",
            passwordHash = "test-password-hash",
            email = "${targetUserId.toString().take(8)}@t.st",
            displayName = "Delegate Target",
        )
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = targetUserId
                it[UserRoleTable.roleId] =
                    RoleTable.selectAll().where { RoleTable.name eq "MANAGER" }.single()[RoleTable.id]
            }
        }
        DatabaseTestHelper.grantAssignDelegate(callerId, sourceId)
        insertBranch()
    }

    @Test
    fun `successful assign creates delegate and user_capability`() {
        val delegateId = TestFixtures.uuid()

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
    fun `assign rejects non-medical-mission branch`() {
        val clinicBranchId = TestFixtures.uuid()
        insertBranch(clinicBranchId, BranchType.CLINIC)

        assertFailsWith<ValidationException> {
            MedicalMissionDelegateService.assignDelegate(
                TestFixtures.uuid(),
                targetUserId,
                clinicBranchId,
                callerId,
            )
        }
        assertEquals(0L, delegateCount(clinicBranchId))
    }

    @Test
    fun `assign rejects target without active manager role`() {
        val ineligibleUserId = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = ineligibleUserId,
            username = "delegate-ineligible-$ineligibleUserId",
            passwordHash = "test-password-hash",
            email = "${ineligibleUserId.toString().take(8)}@t.st",
            displayName = "Delegate Ineligible",
        )

        assertFailsWith<ValidationException> {
            MedicalMissionDelegateService.assignDelegate(
                TestFixtures.uuid(),
                ineligibleUserId,
                branchId,
                callerId,
            )
        }
        assertEquals(0L, delegateCount(branchId))
    }

    @Test
    fun `assign rejects inactive manager`() {
        transaction {
            AppUserTable.update({ AppUserTable.id eq targetUserId }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        assertFailsWith<ValidationException> {
            MedicalMissionDelegateService.assignDelegate(
                TestFixtures.uuid(),
                targetUserId,
                branchId,
                callerId,
            )
        }
        assertEquals(0L, delegateCount(branchId))
    }

    @Test
    fun `list returns active and revoked delegates for medical mission`() {
        val revokedId = TestFixtures.uuid()
        val activeId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(revokedId, targetUserId, branchId, callerId)
        MedicalMissionDelegateService.revokeDelegate(revokedId, callerId)
        MedicalMissionDelegateService.assignDelegate(activeId, targetUserId, branchId, callerId)

        val delegates = MedicalMissionDelegateService.listDelegates(branchId)

        assertEquals(listOf(activeId, revokedId), delegates.map { it.id })
        assertNull(delegates.first().endedAt)
        assertNotNull(delegates.last().endedAt)
    }

    @Test
    fun `duplicate delegate id returns existing row (idempotent)`() {
        val delegateId = TestFixtures.uuid()

        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)
        val duplicate = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertEquals(delegateId, duplicate.id)
        assertEquals(targetUserId, duplicate.targetUser)
        assertEquals(branchId, duplicate.branchId)
        assertNull(duplicate.endedAt)
    }

    @Test
    fun `concurrent same delegate id retries are idempotent`() {
        val delegateId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(2)
        val outcomes =
            try {
                executor
                    .invokeAll(
                        listOf(
                            Callable {
                                runCatching {
                                    MedicalMissionDelegateService.assignDelegate(
                                        delegateId,
                                        targetUserId,
                                        branchId,
                                        callerId,
                                    )
                                }
                            },
                            Callable {
                                runCatching {
                                    MedicalMissionDelegateService.assignDelegate(
                                        delegateId,
                                        targetUserId,
                                        branchId,
                                        callerId,
                                    )
                                }
                            },
                        ),
                    ).map { it.get() }
            } finally {
                executor.shutdown()
            }

        assertEquals(2, outcomes.count { it.isSuccess })
        assertEquals(1L, delegateCount(branchId))
        assertEquals(1L, delegateAuditCount(delegateId))
    }

    @Test
    fun `concurrent different delegate ids reject duplicate target`() {
        val firstDelegateId = TestFixtures.uuid()
        val secondDelegateId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(2)
        val outcomes =
            try {
                executor
                    .invokeAll(
                        listOf(
                            Callable {
                                runCatching {
                                    MedicalMissionDelegateService.assignDelegate(
                                        firstDelegateId,
                                        targetUserId,
                                        branchId,
                                        callerId,
                                    )
                                }
                            },
                            Callable {
                                runCatching {
                                    MedicalMissionDelegateService.assignDelegate(
                                        secondDelegateId,
                                        targetUserId,
                                        branchId,
                                        callerId,
                                    )
                                }
                            },
                        ),
                    ).map { it.get() }
            } finally {
                executor.shutdown()
            }

        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.any { it.exceptionOrNull() is ConflictException })
        assertEquals(1L, delegateCount(branchId))
    }

    @Test
    fun `duplicate delegate id with another caller is rejected`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertFailsWith<ConflictException> {
            MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, targetUserId)
        }
    }

    @Test
    fun `assign rejects duplicate active target at branch`() {
        val firstId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(firstId, targetUserId, branchId, callerId)

        assertFailsWith<ConflictException> {
            MedicalMissionDelegateService.assignDelegate(TestFixtures.uuid(), targetUserId, branchId, callerId)
        }
        assertEquals(1L, delegateCount(branchId))
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

        val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, noCapCaller)

        assertEquals(delegateId, result.id)
        assertEquals(targetUserId, result.targetUser)
    }

    @Test
    fun `successful revoke sets ended_at and expires user_capability`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

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
        MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        val retry = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        assertNotNull(retry.endedAt)
        assertEquals(0L, activeCapabilityCount(delegateId))
        assertEquals(2L, delegateAuditCount(delegateId))
    }

    @Test
    fun `retrying revoke is idempotent`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        val first = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)
        val retry = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

        assertEquals(first.endedAt, retry.endedAt)
        assertEquals(2L, delegateAuditCount(delegateId))
    }

    @Test
    fun `concurrent revoke is idempotent and closes capability once`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

        val executor = Executors.newFixedThreadPool(CONCURRENT_REVOKES)
        val ready = CountDownLatch(CONCURRENT_REVOKES)
        val start = CountDownLatch(1)
        val futures =
            (1..CONCURRENT_REVOKES).map {
                executor.submit<Result<MedicalMissionDelegate>> {
                    ready.countDown()
                    start.await()
                    runCatching { MedicalMissionDelegateService.revokeDelegate(delegateId, callerId) }
                }
            }
        val outcomes =
            try {
                assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            }

        assertTrue(outcomes.all { it.isSuccess && it.getOrNull()?.endedAt != null })
        assertEquals(2L, delegateAuditCount(delegateId))
        assertEquals(1L, delegateUpdateAuditCount(delegateId))
        assertEquals(0L, activeCapabilityCount(delegateId))
        assertTrue(capabilityIsExpired(delegateId))
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

        val noCapCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = noCapCaller,
            username = "no-cap-$noCapCaller",
            passwordHash = "test-password-hash",
            email = "${noCapCaller.toString().take(8)}@t.st",
            displayName = "No Capability",
        )

        val result = MedicalMissionDelegateService.revokeDelegate(delegateId, noCapCaller)

        assertEquals(delegateId, result.id)
        assertNotNull(result.endedAt)
        assertTrue(delegateHasEndedAt(delegateId))
    }

    @Test
    fun `assign writes audit log entry`() {
        val delegateId = TestFixtures.uuid()
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
        val delegateId = TestFixtures.uuid()
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

    private fun insertBranch(
        id: UUID = branchId,
        type: BranchType = BranchType.MEDICAL_MISSION,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = branchName
                it[BranchTable.branchType] = type
            }
        }
    }

    private fun delegateCount(branchId: UUID): Long =
        transaction {
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.branchId eq branchId }
                .count()
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

    private fun delegateUpdateAuditCount(delegateId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "medical_mission_delegate") and
                        (AuditLogTable.recordId eq delegateId) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.count()
        }

    private companion object {
        const val CONCURRENT_REVOKES = 2
        const val EXECUTOR_TERMINATION_SECONDS = 5L
    }
}
