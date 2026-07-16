package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.LiteralOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ClientCreateResult(
    val client: Client,
    val created: Boolean,
)

object ClientRepository {
    private const val SEARCH_LIMIT = 20
    private const val TRIGRAM_SIMILARITY_THRESHOLD = 0.2f
    private const val FULL_NAME_CONCAT_WIDTH = 510
    private const val SPACE_COLUMN_WIDTH = 1

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

            val updatedCount =
                ClientTable.update({ (ClientTable.id eq clientId) and (ClientTable.deletedAt.isNull()) }) {
                    it[ClientTable.deletedAt] = CurrentTimestampWithTimeZone
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
                            "deletedAt" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        ),
                )
            }
            updatedCount > 0
        }

    fun search(query: String): List<Client> =
        transaction {
            val tokens = query.split(" ").filter { it.isNotBlank() }
            val searchQuery = query.trim()
            val colType: org.jetbrains.exposed.v1.core.IColumnType<String> = ClientTable.firstName.columnType
            val spaceLiteral = LiteralOp(VarCharColumnType(SPACE_COLUMN_WIDTH), " ")
            val fullNameConcat =
                CustomFunction(
                    "concat",
                    VarCharColumnType(FULL_NAME_CONCAT_WIDTH),
                    ClientTable.firstName,
                    spaceLiteral,
                    ClientTable.lastName,
                )
            val simScore =
                similarity(
                    fullNameConcat,
                    LiteralOp(
                        VarCharColumnType(searchQuery.length),
                        searchQuery,
                    ),
                )

            ClientTable
                .selectAll()
                .where {
                    val tokenConditions =
                        tokens.map { token ->
                            val namePattern = "%$token%"
                            val phonePattern = "$token%"
                            val tokenParam = QueryParameter(token, colType)
                            nameFieldMatch(tokenParam, namePattern) or
                                (ClientTable.phoneNumber like phonePattern)
                        }
                    (ClientTable.deletedAt.isNull()) and
                        tokenConditions.reduce { acc, cond -> acc and cond }
                }.orderBy(
                    simScore to SortOrder.DESC,
                    ClientTable.lastName to SortOrder.ASC,
                    ClientTable.firstName to SortOrder.ASC,
                ).limit(SEARCH_LIMIT)
                .map { it.toClient() }
        }.also { logger.info { "[SEARCH-CLIENTS] Matched ${it.size} result(s) for query '$query'" } }

    private fun findByIdInTransaction(id: UUID): Client? =
        ClientTable
            .selectAll()
            .where { ClientTable.id eq id }
            .singleOrNull()
            ?.let { it.toClient() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toClient(): Client =
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

private const val TRIGRAM_SIMILARITY_THRESHOLD = 0.2f

private fun nameFieldMatch(
    tokenParam: QueryParameter<String>,
    namePattern: String,
): Op<Boolean> =
    trigramMatch(ClientTable.firstName, tokenParam) or
        trigramMatch(ClientTable.lastName, tokenParam) or
        trigramMatch(ClientTable.middleName, tokenParam) or
        ilike(ClientTable.firstName, namePattern) or
        ilike(ClientTable.lastName, namePattern) or
        ilike(ClientTable.middleName, namePattern)

private fun trigramMatch(
    col: Expression<*>,
    tokenParam: QueryParameter<String>,
): Op<Boolean> {
    val sim = similarity(col, tokenParam)
    return sim greaterEq TRIGRAM_SIMILARITY_THRESHOLD
}

private fun similarity(
    expr1: Expression<*>,
    expr2: Expression<*>,
): CustomFunction<Float> = CustomFunction("similarity", FloatColumnType(), expr1, expr2)

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
        QueryParameter(pattern, col.columnType as org.jetbrains.exposed.v1.core.IColumnType<String>),
    )
