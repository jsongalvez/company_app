package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.repository.model.AuditLogTable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Suppress("TooManyFunctions")
object AuditLogRepository {
    @Suppress("LongParameterList")
    fun record(
        tableName: String,
        recordId: UUID,
        action: AuditAction,
        changedBy: UUID,
        oldValue: String? = null,
        newValue: String? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
    ) {
        AuditLogTable.insert {
            it[AuditLogTable.auditTableName] = tableName
            it[AuditLogTable.recordId] = recordId
            it[AuditLogTable.action] = action
            it[AuditLogTable.changedBy] = changedBy
            it[AuditLogTable.isFlagged] = isFlagged
            if (oldValue != null) it[AuditLogTable.oldValue] = oldValue
            if (newValue != null) it[AuditLogTable.newValue] = newValue
            if (reason != null) it[AuditLogTable.reason] = reason
        }
        logger.info { "[AUDIT-LOG] Recorded $action on $tableName/$recordId isFlagged=$isFlagged" }
    }

    @Suppress("LongParameterList")
    fun recordInsert(
        tableName: String,
        recordId: UUID,
        changedBy: UUID,
        fields: Map<String, String>,
        isFlagged: Boolean = false,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.INSERT,
            changedBy = changedBy,
            newValue = jsonFields(fields),
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList")
    fun recordUpdate(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String>,
        newFields: Map<String, String>,
        changedBy: UUID,
        isFlagged: Boolean = false,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.UPDATE,
            changedBy = changedBy,
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList")
    fun <T> recordUpdate(
        tableName: String,
        recordId: UUID,
        before: T,
        after: T,
        changedBy: UUID,
        isFlagged: Boolean = false,
        auditFields: (T) -> Map<String, String>,
    ) {
        val oldFields = auditFields(before)
        val newFields = auditFields(after)
        val allKeys = oldFields.keys + newFields.keys
        val changedKeys = allKeys.filter { oldFields[it] != newFields[it] }
        val changedOldFields = changedKeys.associateWith { oldFields[it] ?: AuditValues.NULL }
        val changedNewFields = changedKeys.associateWith { newFields[it] ?: AuditValues.NULL }
        recordUpdate(
            tableName = tableName,
            recordId = recordId,
            oldFields = changedOldFields,
            newFields = changedNewFields,
            changedBy = changedBy,
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList")
    fun <T> recordDelete(
        tableName: String,
        recordId: UUID,
        before: T,
        changedBy: UUID,
        reason: String? = null,
        isFlagged: Boolean = false,
        auditFields: (T) -> Map<String, String>,
    ) {
        val oldFields = auditFields(before)
        recordDelete(
            tableName = tableName,
            recordId = recordId,
            oldFields = oldFields,
            newFields = emptyMap(),
            changedBy = changedBy,
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList")
    fun recordDelete(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String>,
        newFields: Map<String, String>,
        changedBy: UUID,
        reason: String? = null,
        isFlagged: Boolean = false,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.DELETE,
            changedBy = changedBy,
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    fun findByTableAndRecord(
        tableName: String,
        recordId: UUID,
    ): List<AuditLogEntry> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId)
                }.orderBy(AuditLogTable.changedAt, SortOrder.DESC)
                .map { it.toAuditLogEntry() }
        }.also {
            logger.info {
                "[FIND-AUDIT] Found ${it.size} entries " +
                    "for $tableName/${recordId.toString().maskUUID()}"
            }
        }

    fun findFlagged(): List<AuditLogEntry> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.isFlagged eq true) and
                        AuditLogTable.acknowledgedAt.isNull()
                }.orderBy(AuditLogTable.changedAt, SortOrder.DESC)
                .map { it.toAuditLogEntry() }
        }.also { logger.info { "[FIND-FLAGGED] Found ${it.size} unacknowledged flagged entries" } }

    fun findById(entryId: UUID): AuditLogEntry? =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.id eq entryId }
                .singleOrNull()
                ?.toAuditLogEntry()
        }.also { logger.info { "[FIND-AUDIT-ID] Entry $entryId found=${it != null}" } }

    fun acknowledge(
        entryId: UUID,
        acknowledgedBy: UUID,
    ): Boolean =
        transaction {
            AuditLogTable.update({
                (AuditLogTable.id eq entryId) and AuditLogTable.acknowledgedAt.isNull()
            }) {
                it[AuditLogTable.acknowledgedBy] = acknowledgedBy
                it[AuditLogTable.acknowledgedAt] = CurrentTimestampWithTimeZone
            } > 0
        }.also { acknowledged -> logger.info { "[ACKNOWLEDGE] Entry $entryId acknowledged=$acknowledged" } }

    fun jsonField(
        key: String,
        value: String,
    ): String = jsonFields(key to value)

    fun jsonFields(vararg fields: Pair<String, String>): String = buildJson(fields.toList())

    fun jsonFields(fields: Map<String, String>): String = buildJson(fields.toList())

    private fun buildJson(fields: List<Pair<String, String>>): String =
        buildJsonObject {
            fields.forEach { (key, value) ->
                put(key, JsonPrimitive(value))
            }
        }.toString()

    private fun ResultRow.toAuditLogEntry(): AuditLogEntry =
        AuditLogEntry(
            id = this[AuditLogTable.id],
            tableName = this[AuditLogTable.auditTableName],
            recordId = this[AuditLogTable.recordId],
            action = this[AuditLogTable.action],
            changedBy = this[AuditLogTable.changedBy],
            changedAt = this[AuditLogTable.changedAt],
            oldValue = this[AuditLogTable.oldValue],
            newValue = this[AuditLogTable.newValue],
            isFlagged = this[AuditLogTable.isFlagged],
            reason = this[AuditLogTable.reason],
            acknowledgedBy = this[AuditLogTable.acknowledgedBy],
            acknowledgedAt = this[AuditLogTable.acknowledgedAt],
        )
}
