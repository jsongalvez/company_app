package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.Auditable
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
    ) {
        AuditLogTable.insert {
            it[AuditLogTable.auditTableName] = tableName
            it[AuditLogTable.recordId] = recordId
            it[AuditLogTable.action] = action
            it[AuditLogTable.changedBy] = changedBy
            if (oldValue != null) it[AuditLogTable.oldValue] = oldValue
            if (newValue != null) it[AuditLogTable.newValue] = newValue
            if (reason != null) it[AuditLogTable.reason] = reason
        }
        logger.info { "[AUDIT-LOG] Recorded $action on $tableName/$recordId" }
    }

    fun recordInsert(
        tableName: String,
        entity: Auditable,
        changedBy: UUID,
    ) {
        record(
            tableName = tableName,
            recordId = entity.id,
            action = AuditAction.INSERT,
            changedBy = changedBy,
            newValue = jsonFields(entity.toAuditFields()),
        )
    }

    fun recordUpdate(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String>,
        newFields: Map<String, String>,
        changedBy: UUID,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.UPDATE,
            changedBy = changedBy,
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
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
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.DELETE,
            changedBy = changedBy,
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
            reason = reason,
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

    fun jsonFields(vararg fields: Pair<String, String>): String =
        buildJsonObject {
            fields.forEach { (key, value) ->
                put(key, JsonPrimitive(value))
            }
        }.toString()

    fun jsonFields(fields: Map<String, String>): String =
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
