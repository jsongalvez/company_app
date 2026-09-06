package com.companyb.companyapp.service
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.client.ClientCreateParams
import com.companyb.companyapp.client.ClientRepository
import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.session.SessionConcernService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #323 batches 1–2 atomicity proofs (ADR-0024). Representative risk classes: simple CRUD
 * commands and optimistic-version updates.
 * Proves the migrated composition — store write on the command transaction, then the audit
 * insert into that same transaction — commits atomically, and that neither a failing audit
 * statement nor a failing mutation can leave one side of the pair behind.
 */
class CrudCommandAtomicityPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "crud-atomic-caller")
    }

    @Test
    fun `command commits domain write and audit atomically`() {
        val result =
            BranchService.create(
                callerId = callerId,
                id = branchId,
                name = "Atomic Branch $branchId",
                branchType = BranchType.CLINIC,
            )

        assertTrue(result.created)
        // #418 — branch creation seeds five default base rates as a side effect.
        val (branchRows, insertAudits) =
            transaction {
                val branches = BranchTable.selectAll().where { BranchTable.id eq branchId }.count()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.auditTableName eq BranchTable.tableName) and
                                (AuditLogTable.recordId eq branchId) and
                                (AuditLogTable.action eq AuditAction.INSERT)
                        }.count()
                branches to audits
            }
        assertEquals(1L, branchRows, "domain row committed")
        assertEquals(1L, insertAudits, "audit row committed in the same transaction")
    }

    @Test
    fun `audit failure inside the command rolls the mutation back`() {
        val clientId = TestFixtures.uuid()

        // Exercises the exact composition ClientService.create runs. A failing audit statement
        // must abort the whole transaction: no client row, no partial audit.
        val error =
            runCatching {
                transaction {
                    ClientRepository.createInTransaction(
                        ClientCreateParams(
                            id = clientId,
                            firstName = "Roll",
                            lastName = "Back",
                            middleName = null,
                            suffix = null,
                            phoneNumber = null,
                            address = "N/A",
                            gender = Gender.M,
                            age = 30,
                            systolicBp = null,
                            diastolicBp = null,
                            medicalConditions = null,
                            changedBy = callerId,
                        ),
                    )
                    AuditLog.record(
                        tableName = ClientTable.tableName,
                        recordId = clientId,
                        action = AuditAction.INSERT,
                        changedBy = callerId,
                        oldValue = "{not-valid-json",
                    )
                }
            }.exceptionOrNull()

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

        val (clientRows, allAudits) =
            transaction {
                val clients = ClientTable.selectAll().where { ClientTable.id eq clientId }.count()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.auditTableName eq ClientTable.tableName) and
                                (AuditLogTable.recordId eq clientId)
                        }.count()
                clients to audits
            }
        assertEquals(0L, clientRows, "mutation rolled back with the failed audit")
        assertEquals(0L, allAudits, "no partial audit row survived")
    }

    @Test
    fun `mutation failure writes no audit row`() {
        val missingClientId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            ClientService.update(
                callerId = callerId,
                clientId = missingClientId,
                firstName = "Ghost",
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = null,
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )
        }

        val updateAudits =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ClientTable.tableName) and
                            (AuditLogTable.recordId eq missingClientId)
                    }.count()
            }
        assertEquals(0L, updateAudits, "no audit row for a failed mutation")
    }

    @Test
    fun `version-mismatched compensation update writes no audit row`() {
        val targetUserId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(targetUserId, "crud-atomic-target")
        // initTestData only tracks the branch id; this test needs a real branch_day under it.
        BranchWorkforceFixtures.insertTestBranch(branchId)
        val workDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val payingDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val created =
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = workDayId,
                payingBranchDayId = payingDayId,
                userId = targetUserId,
                amount = BigDecimal("100.00"),
                note = null,
            )

        assertFailsWith<ConflictException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = created.id,
                amount = BigDecimal("200.00"),
                note = null,
                expectedVersion = 999,
            )
        }

        val updateAudits =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq created.id) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertEquals(0L, updateAudits, "version mismatch must abort before any audit write")
    }

    @Test
    fun `promote concern commits mutation and all three audit rows atomically`() {
        // New risk class for batch 3: one command writing THREE audit rows (concern insert,
        // session_concern link insert, session other-concerns clear). All three must commit
        // with the mutations in the command's single transaction.
        BranchWorkforceFixtures.insertTestBranch(branchId)
        val dayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val clientId = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(clientId)

        val sessionId = TestFixtures.uuid()
        transaction {
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = dayId
                it[SessionTable.sessionType] = SessionType.REGULAR
                it[SessionTable.isWalkIn] = false
                it[SessionTable.basePrice] = BigDecimal("100.00")
                it[SessionTable.finalPrice] = BigDecimal("100.00")
                it[SessionTable.otherConcerns] = "stale concerns"
            }
        }

        val concernId = TestFixtures.uuid()

        val promoted = SessionConcernService.promoteConcern(callerId, sessionId, concernId, "Promoted", "reason")

        assertEquals(concernId, promoted.id)
        val (concernInserts, linkInserts, sessionUpdates) =
            transaction {
                Triple(
                    countAudits(ConcernTable.tableName, concernId, AuditAction.INSERT),
                    countAudits(SessionConcernTable.tableName, sessionId, AuditAction.INSERT),
                    countAudits(SessionTable.tableName, sessionId, AuditAction.UPDATE),
                )
            }
        val otherConcerns =
            transaction {
                SessionTable
                    .selectAll()
                    .where { SessionTable.id eq sessionId }
                    .single()[SessionTable.otherConcerns]
            }

        assertEquals(1L, concernInserts, "concern insert audited")
        assertEquals(1L, linkInserts, "session_concern link insert audited")
        assertEquals(1L, sessionUpdates, "other-concerns clear audited")
        assertEquals(null, otherConcerns, "other_concerns cleared on the session row")
    }

    private fun countAudits(
        tableName: String,
        recordId: UUID,
        action: AuditAction,
    ): Long =
        AuditLogTable
            .selectAll()
            .where {
                (AuditLogTable.auditTableName eq tableName) and
                    (AuditLogTable.recordId eq recordId) and
                    (AuditLogTable.action eq action)
            }.count()
}
