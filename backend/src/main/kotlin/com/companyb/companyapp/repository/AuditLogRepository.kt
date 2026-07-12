package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Reusable audit-trail writer. Every mutating service must record an entry here so
 * accountability is preserved (see `audit_log` in V1__full_schema.sql).
 *
 * [record] opens its own Exposed [transaction]; when called inside another transaction it joins
 * the outer one (Exposed reuses the connection unless nested transactions are explicitly enabled),
 * so the audit insert commits atomically with the change it describes.
 */
object AuditLogRepository {
    // old_value/new_value are JSONB; bind as text and cast (NULL text casts to a NULL jsonb).
    private val INSERT_SQL =
        """
        INSERT INTO audit_log (table_name, record_id, action, changed_by, old_value, new_value, reason)
        VALUES (?, ?::uuid, ?::audit_action, ?::uuid, ?::jsonb, ?::jsonb, ?)
        """.trimIndent()

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
            exec(
                INSERT_SQL,
                args =
                    listOf(
                        TextColumnType() to tableName,
                        TextColumnType() to recordId.toString(),
                        TextColumnType() to action.name,
                        TextColumnType() to changedBy.toString(),
                        TextColumnType() to oldValue,
                        TextColumnType() to newValue,
                        TextColumnType() to reason,
                    ),
            )
        }
        logger.info { "[AUDIT-LOG] Recorded $action on $tableName/$recordId" }
    }

    /** Builds a single-field JSON object (e.g. `{"status":"INACTIVE"}`) for audit values. */
    fun jsonField(
        key: String,
        value: String,
    ): String =
        buildJsonObject {
            put(key, JsonPrimitive(value))
        }.toString()
}
