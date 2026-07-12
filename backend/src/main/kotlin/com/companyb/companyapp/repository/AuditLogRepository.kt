package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

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
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.auditTableName] = tableName
                it[AuditLogTable.recordId] = recordId
                it[AuditLogTable.action] = action
                it[AuditLogTable.changedBy] = changedBy
                if (oldValue != null) it[AuditLogTable.oldValue] = oldValue
                if (newValue != null) it[AuditLogTable.newValue] = newValue
                if (reason != null) it[AuditLogTable.reason] = reason
            }
        }
        logger.info { "[AUDIT-LOG] Recorded $action on $tableName/$recordId" }
    }

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
}
