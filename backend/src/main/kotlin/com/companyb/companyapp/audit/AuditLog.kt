package com.companyb.companyapp.audit

import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.repository.decodeOpaqueCursor
import com.companyb.companyapp.repository.encodeOpaqueCursor
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.OffsetDateTime
import java.util.UUID

/** Keyset cursor for audit browse: strictly-before position on `(changed_at, id) DESC`. */
data class AuditBrowseCursor(
    val changedAt: OffsetDateTime,
    val id: UUID,
)

/**
 * Command context stamped onto audit rows (#323): who acted, on which branch, and the
 * REMITTED-day flag vocabulary. Audit seams take this as their first parameter so their
 * domain-row parameters stay separate from the who/where/why of the audit event.
 */
data class AuditContext(
    val changedBy: UUID,
    val branchId: UUID? = null,
    val isFlagged: Boolean = false,
    val reason: String? = null,
)

/**
 * Cross-feature audit append seam (#549): every mutating command records its audit row
 * through here, inside its own command transaction — never in routes, never opened from
 * a store. The only audit surface other owners may import; table/query internals live in
 * [AuditLogStore] and scoped reads in [AuditLogService].
 */
@Suppress("TooManyFunctions") // #549 ten-function append vocabulary on one seam
object AuditLog {
    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun record(
        tableName: String,
        recordId: UUID,
        action: AuditAction,
        changedBy: UUID,
        branchId: UUID? = null,
        oldValue: String? = null,
        newValue: String? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
    ) = AuditLogStore.record(
        tableName = tableName,
        recordId = recordId,
        action = action,
        changedBy = changedBy,
        branchId = branchId,
        oldValue = oldValue,
        newValue = newValue,
        reason = reason,
        isFlagged = isFlagged,
    )

    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun recordInsert(
        tableName: String,
        recordId: UUID,
        changedBy: UUID,
        fields: Map<String, String?>,
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
    ) = AuditLogStore.recordInsert(
        tableName = tableName,
        recordId = recordId,
        changedBy = changedBy,
        fields = fields,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
    )

    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun recordUpdate(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
        changedBy: UUID,
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
    ) = AuditLogStore.recordUpdate(
        tableName = tableName,
        recordId = recordId,
        oldFields = oldFields,
        newFields = newFields,
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
    )

    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun <T> recordUpdate(
        tableName: String,
        recordId: UUID,
        before: T,
        after: T,
        changedBy: UUID,
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
        auditFields: (T) -> Map<String, String?>,
    ) = AuditLogStore.recordUpdate(
        tableName = tableName,
        recordId = recordId,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
        auditFields = auditFields,
    )

    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun <T> recordDelete(
        tableName: String,
        recordId: UUID,
        before: T,
        changedBy: UUID,
        branchId: UUID? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
        auditFields: (T) -> Map<String, String?>,
    ) = AuditLogStore.recordDelete(
        tableName = tableName,
        recordId = recordId,
        before = before,
        changedBy = changedBy,
        branchId = branchId,
        reason = reason,
        isFlagged = isFlagged,
        auditFields = auditFields,
    )

    @Suppress("LongParameterList") // #549 seam mirrors the store signature
    fun recordDelete(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
        changedBy: UUID,
        branchId: UUID? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
    ) = AuditLogStore.recordDelete(
        tableName = tableName,
        recordId = recordId,
        oldFields = oldFields,
        newFields = newFields,
        changedBy = changedBy,
        branchId = branchId,
        reason = reason,
        isFlagged = isFlagged,
    )

    /**
     * Anonymization redaction (#524) — the single documented exception to
     * audit-payload immutability. Runs on the caller's command transaction.
     */
    fun redactClientNamesInTransaction(recordId: UUID): Int = AuditLogStore.redactClientNamesInTransaction(recordId)

    fun jsonField(
        key: String,
        value: String?,
    ): String = jsonFields(key to value)

    fun jsonFields(vararg fields: Pair<String, String?>): String = buildJson(fields.toList())

    fun jsonFields(fields: Map<String, String?>): String = buildJson(fields.toList())

    /**
     * #525 — nulls encode as JSON null via the existing kotlinx-serialization
     * library; every non-null value stays a JSON string for historical
     * compatibility. Historical `{"k":"null"}` payloads therefore keep parsing;
     * new `{"k":null}` rows are distinguishable from literal `"null"` text.
     */
    private fun buildJson(fields: List<Pair<String, String?>>): String =
        buildJsonObject {
            fields.forEach { (key, value) ->
                if (value == null) {
                    put(key, JsonNull)
                } else {
                    put(key, JsonPrimitive(value))
                }
            }
        }.toString()
}

/**
 * Opaque URL-safe cursor encoding for audit browse: `changedAt|id`, base64url.
 * Format is internal — decode with [decodeCursor]; never parse client-side.
 */
fun encodeCursor(cursor: AuditBrowseCursor): String =
    encodeOpaqueCursor(cursor.changedAt.toString(), cursor.id.toString())

fun decodeCursor(raw: String): AuditBrowseCursor {
    val parts =
        runCatching { decodeOpaqueCursor(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid audit cursor") }
    require(parts.size == 2) { "Invalid audit cursor" }
    return AuditBrowseCursor(
        changedAt = OffsetDateTime.parse(parts[0]),
        id = UUID.fromString(parts[1]),
    )
}
