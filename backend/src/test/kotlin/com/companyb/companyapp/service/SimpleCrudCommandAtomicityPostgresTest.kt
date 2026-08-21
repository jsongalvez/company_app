package com.companyb.companyapp.service

import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #323 batch 1 atomicity proofs (ADR-0024). Representative risk class: simple CRUD commands.
 * Proves the migrated composition — store write on the command transaction, then the audit
 * insert into that same transaction — commits atomically, and that neither a failing audit
 * statement nor a failing mutation can leave one side of the pair behind.
 */
class SimpleCrudCommandAtomicityPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "crud-atomic-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(BranchTable, BranchTable.id, branchId)
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
        trackOwned(ClientTable, ClientTable.id, clientId)

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
                    AuditLogRepository.record(
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
}
