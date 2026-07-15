package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ClientCreateResult(
    val client: Client,
    val created: Boolean,
)

object ClientRepository {
    private const val SEARCH_LIMIT = 20

    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        firstName: String,
        lastName: String,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String,
        gender: String,
        age: Int,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
        changedBy: UUID,
    ): ClientCreateResult =
        transaction {
            val insertedCount =
                ClientTable
                    .insertIgnore {
                        it[ClientTable.id] = id
                        it[ClientTable.firstName] = firstName
                        it[ClientTable.lastName] = lastName
                        if (middleName != null) it[ClientTable.middleName] = middleName
                        if (suffix != null) it[ClientTable.suffix] = suffix
                        if (phoneNumber != null) it[ClientTable.phoneNumber] = phoneNumber
                        it[ClientTable.address] = address
                        it[ClientTable.gender] = gender
                        it[ClientTable.age] = age
                        if (systolicBp != null) it[ClientTable.systolicBp] = systolicBp
                        if (diastolicBp != null) it[ClientTable.diastolicBp] = diastolicBp
                        if (medicalConditions != null) it[ClientTable.medicalConditions] = medicalConditions
                    }.insertedCount
            val created = insertedCount > 0
            val client =
                findByIdInTransaction(id) ?: error("client row not found after idempotent insert for $id")

            if (created) {
                AuditLogRepository.record(
                    tableName = ClientTable.tableName,
                    recordId = client.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to client.id.toString(),
                            "firstName" to client.firstName,
                            "lastName" to client.lastName,
                        ),
                )
            }
            ClientCreateResult(client, created)
        }.also {
            logger.info {
                "[CREATE-CLIENT] Client ${it.client.id.toString().maskUUID()} created=${it.created}"
            }
        }

    fun findById(id: UUID): Client? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-CLIENT] Client ${id.toString().maskUUID()} found=${it != null}" } }

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun update(
        clientId: UUID,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: String?,
        age: Int?,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
        changedBy: UUID,
    ): Client? =
        transaction {
            val old = findByIdInTransaction(clientId) ?: return@transaction null

            val updatedCount =
                ClientTable.update({ ClientTable.id eq clientId }) {
                    if (firstName != null) it[ClientTable.firstName] = firstName
                    if (lastName != null) it[ClientTable.lastName] = lastName
                    if (middleName != null) it[ClientTable.middleName] = middleName
                    if (suffix != null) it[ClientTable.suffix] = suffix
                    if (phoneNumber != null) it[ClientTable.phoneNumber] = phoneNumber
                    if (address != null) it[ClientTable.address] = address
                    if (gender != null) it[ClientTable.gender] = gender
                    if (age != null) it[ClientTable.age] = age
                    if (systolicBp != null) it[ClientTable.systolicBp] = systolicBp
                    if (diastolicBp != null) it[ClientTable.diastolicBp] = diastolicBp
                    if (medicalConditions != null) it[ClientTable.medicalConditions] = medicalConditions
                }
            val updated = findByIdInTransaction(clientId) ?: return@transaction null

            if (updatedCount > 0) {
                AuditLogRepository.record(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    action = AuditAction.UPDATE,
                    changedBy = changedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to old.firstName,
                            "lastName" to old.lastName,
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to updated.firstName,
                            "lastName" to updated.lastName,
                        ),
                )
            }
            updated
        }

    fun anonymize(
        clientId: UUID,
        changedBy: UUID,
    ): Boolean =
        transaction {
            val old = findByIdInTransaction(clientId) ?: return@transaction false
            val now = OffsetDateTime.now()

            val updatedCount =
                ClientTable.update({ (ClientTable.id eq clientId) and (ClientTable.deletedAt.isNull()) }) {
                    it[ClientTable.deletedAt] = now
                    it[ClientTable.firstName] = ""
                    it[ClientTable.lastName] = ""
                    it[ClientTable.middleName] = null
                    it[ClientTable.suffix] = null
                    it[ClientTable.phoneNumber] = null
                    it[ClientTable.address] = ""
                    it[ClientTable.medicalConditions] = null
                    it[ClientTable.systolicBp] = null
                    it[ClientTable.diastolicBp] = null
                }

            if (updatedCount > 0) {
                AuditLogRepository.record(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    action = AuditAction.UPDATE,
                    changedBy = changedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to old.firstName,
                            "lastName" to old.lastName,
                            "deletedAt" to (old.deletedAt?.toString() ?: "null"),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to "",
                            "lastName" to "",
                            "deletedAt" to now.toString(),
                        ),
                )
            }
            updatedCount > 0
        }

    fun search(query: String): List<Client> =
        transaction {
            val namePattern = "%$query%"
            val phonePattern = "$query%"
            ClientTable
                .selectAll()
                .where {
                    (ClientTable.deletedAt.isNull()) and
                        (
                            ilike(ClientTable.firstName, namePattern) or
                                ilike(ClientTable.lastName, namePattern) or
                                ilike(ClientTable.middleName, namePattern) or
                                (ClientTable.phoneNumber like phonePattern)
                        )
                }.orderBy(ClientTable.lastName to SortOrder.ASC, ClientTable.firstName to SortOrder.ASC)
                .limit(SEARCH_LIMIT)
                .map { it.toClient() }
        }.also { logger.info { "[SEARCH-CLIENTS] Matched ${it.size} result(s) for query '$query'" } }

    private class ILikeOp(
        expr1: Expression<*>,
        expr2: Expression<*>,
    ) : ComparisonOp(expr1, expr2, "ILIKE")

    @Suppress("UNCHECKED_CAST")
    private fun <T : String?> ilike(
        col: Column<T>,
        pattern: String,
    ): Op<Boolean> =
        ILikeOp(
            col,
            QueryParameter(pattern, col.columnType as org.jetbrains.exposed.sql.IColumnType<String>),
        )

    private fun findByIdInTransaction(id: UUID): Client? =
        ClientTable
            .selectAll()
            .where { ClientTable.id eq id }
            .singleOrNull()
            ?.let { it.toClient() }

    private fun org.jetbrains.exposed.sql.ResultRow.toClient(): Client =
        Client(
            id = this[ClientTable.id],
            firstName = this[ClientTable.firstName],
            lastName = this[ClientTable.lastName],
            middleName = this[ClientTable.middleName],
            suffix = this[ClientTable.suffix],
            phoneNumber = this[ClientTable.phoneNumber],
            address = this[ClientTable.address],
            gender = this[ClientTable.gender],
            age = this[ClientTable.age],
            systolicBp = this[ClientTable.systolicBp],
            diastolicBp = this[ClientTable.diastolicBp],
            medicalConditions = this[ClientTable.medicalConditions],
            deletedAt = this[ClientTable.deletedAt],
        )
}
