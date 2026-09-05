package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.SessionTable
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
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ClientCreateParams(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String,
    val gender: Gender,
    val age: Int,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val changedBy: UUID,
)

data class ClientUpdateParams(
    val clientId: UUID,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String?,
    val gender: Gender?,
    val age: Int?,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
)

data class ClientCreateResult(
    val client: Client,
    val created: Boolean,
)

object ClientRepository {
    private const val SEARCH_LIMIT = 20
    private const val TRIGRAM_SIMILARITY_THRESHOLD = 0.2f
    private const val FULL_NAME_CONCAT_WIDTH = 510
    private const val SPACE_COLUMN_WIDTH = 1

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(params: ClientCreateParams): ClientCreateResult {
        val insertedCount =
            ClientTable
                .insertIgnore {
                    it[ClientTable.id] = params.id
                    it[ClientTable.firstName] = params.firstName
                    it[ClientTable.lastName] = params.lastName
                    if (params.middleName != null) it[ClientTable.middleName] = params.middleName
                    if (params.suffix != null) it[ClientTable.suffix] = params.suffix
                    if (params.phoneNumber != null) it[ClientTable.phoneNumber] = params.phoneNumber
                    it[ClientTable.address] = params.address
                    it[ClientTable.gender] = params.gender.name
                    it[ClientTable.age] = params.age
                    if (params.systolicBp != null) it[ClientTable.systolicBp] = params.systolicBp
                    if (params.diastolicBp != null) it[ClientTable.diastolicBp] = params.diastolicBp
                    if (params.medicalConditions !=
                        null
                    ) {
                        it[ClientTable.medicalConditions] = params.medicalConditions
                    }
                }.insertedCount
        val client =
            findByIdInTransaction(params.id)
                ?: error("client row not found after idempotent insert for ${params.id}")
        return ClientCreateResult(client, created = insertedCount > 0)
    }

    fun findById(id: UUID): Client? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-CLIENT] Client ${id.toString().maskUUID()} found=${it != null}" } }

    /** Locks and reads client row on caller's open transaction. */
    fun acquireLockInTransaction(clientId: UUID): Client? =
        ClientTable
            .selectAll()
            .where { ClientTable.id eq clientId }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toClient()

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction.
     * Returns the updated row count and the post-write row; null after when the row is missing or
     * anonymized (H4 mirror: an update can never land on a soft-deleted record).
     */
    fun updateInTransaction(params: ClientUpdateParams): Pair<Int, Client?> {
        val updatedCount =
            ClientTable.update({ (ClientTable.id eq params.clientId) and (ClientTable.deletedAt.isNull()) }) {
                if (params.firstName != null) it[ClientTable.firstName] = params.firstName
                if (params.lastName != null) it[ClientTable.lastName] = params.lastName
                if (params.middleName != null) it[ClientTable.middleName] = params.middleName
                if (params.suffix != null) it[ClientTable.suffix] = params.suffix
                if (params.phoneNumber != null) it[ClientTable.phoneNumber] = params.phoneNumber
                if (params.address != null) it[ClientTable.address] = params.address
                if (params.gender != null) it[ClientTable.gender] = params.gender.name
                if (params.age != null) it[ClientTable.age] = params.age
                if (params.systolicBp != null) it[ClientTable.systolicBp] = params.systolicBp
                if (params.diastolicBp != null) it[ClientTable.diastolicBp] = params.diastolicBp
                if (params.medicalConditions != null) it[ClientTable.medicalConditions] = params.medicalConditions
            }
        val updated = findByIdInTransaction(params.clientId)
        if (updated?.deletedAt != null) return updatedCount to null
        return updatedCount to updated
    }

    /** In-transaction store operation (#323, ADR-0024) — anonymize write only; guards stay with the command. */
    fun anonymizeInTransaction(clientId: UUID): Int =
        ClientTable.update({ (ClientTable.id eq clientId) and (ClientTable.deletedAt.isNull()) }) {
            it[ClientTable.deletedAt] = CurrentTimestampWithTimeZone
            it[ClientTable.firstName] = null
            it[ClientTable.lastName] = null
            it[ClientTable.middleName] = null
            it[ClientTable.suffix] = null
            it[ClientTable.phoneNumber] = null
            it[ClientTable.address] = null
            it[ClientTable.medicalConditions] = null
            it[ClientTable.systolicBp] = null
            it[ClientTable.diastolicBp] = null
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

    /** Total session history per client for authoritative client read models. */
    fun countSessions(clientIds: Collection<UUID>): Map<UUID, Int> {
        if (clientIds.isEmpty()) return emptyMap()
        val ids = clientIds.toList()
        return transaction {
            val sessionCount = SessionTable.id.count()
            SessionTable
                .leftJoin(
                    ActiveSessionVoidsView,
                    { SessionTable.id },
                    { ActiveSessionVoidsView.sessionId },
                ).select(SessionTable.clientId, sessionCount)
                .where {
                    (SessionTable.clientId inList ids) and
                        (SessionTable.sessionType neq SessionType.MEDICAL_MISSION) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.groupBy(SessionTable.clientId)
                .associate { row -> row[SessionTable.clientId] to row[sessionCount].toInt() }
        }.let { counts -> ids.associateWith { counts[it] ?: 0 } }
    }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Client? =
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
            gender = Gender.valueOf(this[ClientTable.gender]),
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
        // SAFETY: ilike takes String-backed columns; columnType narrows here #467
        QueryParameter(pattern, col.columnType as org.jetbrains.exposed.v1.core.IColumnType<String>),
    )
