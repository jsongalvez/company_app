package com.companyb.companyapp.audit

import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.utils.decodeOpaqueCursor
import com.companyb.companyapp.utils.encodeOpaqueCursor
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
 * Command context stamped onto audit rows: who acted, on which branch, and the
 * REMITTED-day flag vocabulary. Audit seams take this as their first parameter so their
 * domain-row parameters stay separate from the who/where/why of the audit event.
 */
data class AuditContext(
    val changedBy: UUID,
    val branchId: UUID? = null,
    val isFlagged: Boolean = false,
    val reason: String? = null,
)

/** The audited row: which table and which record in it. */
data class AuditedRow(
    val tableName: String,
    val recordId: UUID,
)

/** Browse filters; every field is optional and an absent one matches everything. */
data class AuditBrowseFilter(
    val tableName: String? = null,
    val action: AuditAction? = null,
    val callerName: String? = null,
    val dateFrom: OffsetDateTime? = null,
    val dateTo: OffsetDateTime? = null,
)

/**
 * Cross-feature audit append seam: every mutating command records its audit row
 * through here, inside its own command transaction — never in routes, never opened from
 * a store. The only audit surface other owners may import; table/query internals live in
 * [AuditLogStore] and scoped reads in [AuditLogService].
 */
@Suppress("TooManyFunctions") // #549 ten-function append vocabulary on one seam
object AuditLog {
    fun record(
        context: AuditContext,
        row: AuditedRow,
        action: AuditAction,
        oldValue: String? = null,
        newValue: String? = null,
    ) = AuditLogStore.record(context, row, action, oldValue, newValue)

    fun recordInsert(
        context: AuditContext,
        row: AuditedRow,
        fields: Map<String, String?>,
    ) = AuditLogStore.recordInsert(context, row, fields)

    fun recordUpdate(
        context: AuditContext,
        row: AuditedRow,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
    ) = AuditLogStore.recordUpdate(context, row, oldFields, newFields)

    fun <T> recordUpdate(
        context: AuditContext,
        row: AuditedRow,
        before: T,
        after: T,
        auditFields: (T) -> Map<String, String?>,
    ) = AuditLogStore.recordUpdate(context, row, before, after, auditFields)

    fun <T> recordDelete(
        context: AuditContext,
        row: AuditedRow,
        before: T,
        auditFields: (T) -> Map<String, String?>,
    ) = AuditLogStore.recordDelete(context, row, before, auditFields)

    fun recordDelete(
        context: AuditContext,
        row: AuditedRow,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
    ) = AuditLogStore.recordDelete(context, row, oldFields, newFields)

    /**
     * Anonymization redaction — the single documented exception to
     * audit-payload immutability. Runs on the caller's command transaction.
     */
    fun redactClientNamesInTransaction(recordId: UUID): Int = AuditLogStore.redactClientNamesInTransaction(recordId)

    /**
     * Session-text redaction — anonymize-command companion to
     * [redactClientNamesInTransaction]: rewrites session free text for one
     * anonymized client's sessions. Runs on the caller's command transaction.
     */
    fun redactSessionTextInTransaction(
        sessionIds: Collection<UUID>,
        practitionerIds: Collection<UUID>,
    ): Int = AuditLogStore.redactSessionTextInTransaction(sessionIds, practitionerIds)

    /**
     * Void-reason redaction — anonymize-command companion to
     * [redactSessionTextInTransaction]: rewrites void/unvoid free text plus
     * the duplicated audit reason column for one anonymized client's void
     * rows. Runs on the caller's command transaction.
     */
    fun redactVoidReasonInTransaction(voidIds: Collection<UUID>): Int =
        AuditLogStore.redactVoidReasonInTransaction(voidIds)

    fun jsonField(
        key: String,
        value: String?,
    ): String = jsonFields(key to value)

    fun jsonFields(vararg fields: Pair<String, String?>): String = buildJson(fields.toList())

    fun jsonFields(fields: Map<String, String?>): String = buildJson(fields.toList())

    /**
     * nulls encode as JSON null via the existing kotlinx-serialization
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
