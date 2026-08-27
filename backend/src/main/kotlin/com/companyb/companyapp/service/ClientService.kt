package com.companyb.companyapp.service

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.ClientUpdateParams
import com.companyb.companyapp.repository.hasActivePendingSessionInTransaction
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Client feature commands (#323, ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `ClientRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 * The anonymize guard (client row lock + PENDING-session check) runs inside the command
 * transaction, keeping the atomic check+write ordering it always had.
 */
object ClientService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        firstName: String,
        lastName: String,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: Gender,
        age: Int,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): ClientCreateResult =
        transaction {
            val result =
                ClientRepository.createInTransaction(
                    ClientCreateParams(
                        id = id,
                        firstName = firstName,
                        lastName = lastName,
                        middleName = middleName?.trim()?.takeIf { it.isNotEmpty() },
                        suffix = suffix?.trim()?.takeIf { it.isNotEmpty() },
                        phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                        address = address?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A",
                        gender = gender,
                        age = age,
                        systolicBp = systolicBp,
                        diastolicBp = diastolicBp,
                        medicalConditions = medicalConditions?.trim()?.takeIf { it.isNotEmpty() },
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                ClientAudit.inserted(callerId, result.client)
            }
            result
        }.also {
            logger.info { "[CREATE-CLIENT] Client ${it.client.id.toString().maskUUID()} created=${it.created}" }
        }

    fun search(query: String): List<Client> = ClientRepository.search(query)

    fun findById(clientId: UUID): Client =
        ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount", "CyclomaticComplexMethod")
    fun update(
        callerId: UUID,
        clientId: UUID,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: Gender?,
        age: Int?,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): Client =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val before =
                ClientRepository.findByIdInTransaction(clientId)
                    ?: throw NotFoundException("Client not found")

            val (updatedCount, after) =
                ClientRepository.updateInTransaction(
                    ClientUpdateParams(
                        clientId = clientId,
                        firstName = firstName?.takeIf { it.isNotEmpty() },
                        lastName = lastName?.takeIf { it.isNotEmpty() },
                        middleName = middleName?.trim()?.takeIf { it.isNotEmpty() },
                        suffix = suffix?.trim()?.takeIf { it.isNotEmpty() },
                        phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                        address = address?.trim()?.takeIf { it.isNotEmpty() },
                        gender = gender,
                        age = age,
                        systolicBp = systolicBp,
                        diastolicBp = diastolicBp,
                        medicalConditions = medicalConditions?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )

            if (updatedCount > 0 && after != null) {
                ClientAudit.updated(callerId, before, after)
            }
            after ?: throw NotFoundException("Client not found")
        }

    @Suppress("ThrowsCount")
    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        transaction {
            val before =
                ClientRepository.acquireLockInTransaction(clientId)
                    ?: throw NotFoundException("Client not found")

            // Atomic check+write guards (CR-018 C2): lock the client row, then reject when an
            // active PENDING session exists — both inside this command transaction.
            if (hasActivePendingSessionInTransaction(clientId)) {
                throw ConflictException(
                    "Client has an active PENDING session; complete or cancel it before anonymizing",
                )
            }

            val updatedCount = ClientRepository.anonymizeInTransaction(clientId)

            if (updatedCount > 0) {
                val after =
                    ClientRepository.findByIdInTransaction(clientId)
                        ?: error("client not found after anonymize")
                ClientAudit.updated(callerId, before, after)
            } else {
                throw NotFoundException("Client not found")
            }
        }
    }
}

/**
 * Client audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ClientAudit {
    fun inserted(
        changedBy: UUID,
        client: Client,
    ) = AuditLogRepository.recordInsert(
        tableName = ClientTable.tableName,
        recordId = client.id,
        changedBy = changedBy,
        fields = ClientTable.auditFields(client),
    )

    fun updated(
        changedBy: UUID,
        before: Client,
        after: Client,
    ) = AuditLogRepository.recordUpdate(
        tableName = ClientTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        auditFields = ClientTable::auditFields,
    )
}
