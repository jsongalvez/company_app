package com.companyb.companyapp.audit

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.client.DEFAULT_CLIENT_ADDRESS
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Audit persistence (#549): table/query internals behind the [AuditLog] append seam and
 * the [AuditLogService] scoped reads. Same-owner only — other owners cross [AuditLog].
 * #600 record unanimity stays whole per #535; the append seam mirrors store signatures 1:1
 * (#549) so bundling would fork the seam without removing a parameter.
 */
internal object AuditLogStore {
    @Suppress("LongParameterList") // #600
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
    ) {
        AuditLogTable.insert {
            it[AuditLogTable.auditTableName] = tableName
            it[AuditLogTable.recordId] = recordId
            it[AuditLogTable.action] = action
            it[AuditLogTable.changedBy] = changedBy
            it[AuditLogTable.branchId] = branchId
            it[AuditLogTable.isFlagged] = isFlagged
            if (oldValue != null) it[AuditLogTable.oldValue] = oldValue
            if (newValue != null) it[AuditLogTable.newValue] = newValue
            if (reason != null) it[AuditLogTable.reason] = reason
        }
        logger.info { "[AUDIT-LOG] Recorded $action on $tableName/$recordId isFlagged=$isFlagged" }
    }

    @Suppress("LongParameterList") // #600
    fun recordInsert(
        tableName: String,
        recordId: UUID,
        changedBy: UUID,
        fields: Map<String, String?>,
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.INSERT,
            changedBy = changedBy,
            branchId = branchId,
            newValue = AuditLog.jsonFields(fields),
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList") // #600
    fun recordUpdate(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
        changedBy: UUID,
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.UPDATE,
            changedBy = changedBy,
            branchId = branchId,
            oldValue = AuditLog.jsonFields(oldFields),
            newValue = AuditLog.jsonFields(newFields),
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList") // #600
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
    ) {
        val oldFields = auditFields(before)
        val newFields = auditFields(after)
        val allKeys = oldFields.keys + newFields.keys
        val changedKeys = allKeys.filter { oldFields[it] != newFields[it] }
        // #525 — nulls stay JSON null (no NULL-sentinel fallback): a missing key
        // cannot occur at runtime (one mapper serves both sides), and a null value
        // must survive as null so literal "null" text stays distinguishable.
        val changedOldFields = changedKeys.associateWith { oldFields[it] }
        val changedNewFields = changedKeys.associateWith { newFields[it] }
        recordUpdate(
            tableName = tableName,
            recordId = recordId,
            oldFields = changedOldFields,
            newFields = changedNewFields,
            changedBy = changedBy,
            branchId = branchId,
            isFlagged = isFlagged,
            reason = reason,
        )
    }

    @Suppress("LongParameterList") // #600
    fun <T> recordDelete(
        tableName: String,
        recordId: UUID,
        before: T,
        changedBy: UUID,
        branchId: UUID? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
        auditFields: (T) -> Map<String, String?>,
    ) {
        val oldFields = auditFields(before)
        recordDelete(
            tableName = tableName,
            recordId = recordId,
            oldFields = oldFields,
            newFields = emptyMap(),
            changedBy = changedBy,
            branchId = branchId,
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    @Suppress("LongParameterList") // #600
    fun recordDelete(
        tableName: String,
        recordId: UUID,
        oldFields: Map<String, String?>,
        newFields: Map<String, String?>,
        changedBy: UUID,
        branchId: UUID? = null,
        reason: String? = null,
        isFlagged: Boolean = false,
    ) {
        record(
            tableName = tableName,
            recordId = recordId,
            action = AuditAction.DELETE,
            changedBy = changedBy,
            branchId = branchId,
            oldValue = AuditLog.jsonFields(oldFields),
            newValue = AuditLog.jsonFields(newFields),
            reason = reason,
            isFlagged = isFlagged,
        )
    }

    /**
     * Anonymization redaction (#524) — the single documented exception to
     * audit-payload immutability. Rewrites the identifying values in every
     * retained audit row for one anonymized client, in place, on the caller's
     * command transaction (no new events; event identity — actor, timestamp,
     * action, record, changed-field keys — is preserved).
     *
     * Field policy: only `firstName`/`lastName` values that still identify a
     * person become [AuditValues.REDACTED]. Uniform `null` sentinels and
     * existing markers are left alone so cleared-state history keeps its
     * shape. #525 must extend [CLIENT_IDENTIFYING_KEYS] when it widens the
     * client audit vocabulary — no new identifying value may land in a
     * permanent payload without passing through this gate.
     */
    fun redactClientNamesInTransaction(recordId: UUID): Int {
        val rows =
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ClientTable.tableName) and
                        (AuditLogTable.recordId eq recordId)
                }.toList()
        var redacted = 0
        for (row in rows) {
            val oldValue = row[AuditLogTable.oldValue]
            val newValue = row[AuditLogTable.newValue]
            val scrubbedOld = redactClientNames(oldValue)
            val scrubbedNew = redactClientNames(newValue)
            if (scrubbedOld != oldValue || scrubbedNew != newValue) {
                val rowId = row[AuditLogTable.id]
                AuditLogTable.update({ AuditLogTable.id eq rowId }) {
                    it[AuditLogTable.oldValue] = scrubbedOld
                    it[AuditLogTable.newValue] = scrubbedNew
                }
                redacted++
            }
        }
        return redacted
    }

    /**
     * Scope predicate: branch rows must be in the caller's window (null window =
     * all branches — GLOBAL VIEW_BRANCH_DATA holder); NULL-branch rows are
     * readable when their table passes the branchless policy or the caller holds
     * the global-view fallback (AuditLogReadScope owns the policy).
     */
    private fun scopedWhere(
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
        base: Op<Boolean>,
    ): Op<Boolean> {
        val branchWindowOp: Op<Boolean> =
            when {
                windowBranchIds == null -> Op.TRUE
                windowBranchIds.isEmpty() -> Op.FALSE
                else -> AuditLogTable.branchId inList windowBranchIds
            }
        val branchlessOp: Op<Boolean> =
            when {
                canReadNullRows -> Op.TRUE
                branchlessTables.isEmpty() -> Op.FALSE
                else -> AuditLogTable.auditTableName inList branchlessTables.toList()
            }
        return base and (branchWindowOp or (AuditLogTable.branchId.isNull() and branchlessOp))
    }

    // Both read joins live here so every audit entry carries its resolved display names:
    // the actor's (changedByName) and the branch's (branchName — #383; NULL for branchless
    // rows, which the client renders as an explicit marker).
    private fun AuditLogTable.withDisplayJoins() =
        leftJoin(AppUserTable, { changedBy }, { AppUserTable.id })
            .leftJoin(BranchTable, { branchId }, { BranchTable.id })

    private fun scopedQuery(
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
        base: Op<Boolean>,
    ): Query =
        AuditLogTable
            .withDisplayJoins()
            .selectAll()
            .where { scopedWhere(windowBranchIds, branchlessTables, canReadNullRows, base) }
            .orderBy(AuditLogTable.changedAt to SortOrder.DESC, AuditLogTable.id to SortOrder.DESC)

    fun findByTableAndRecord(
        tableName: String,
        recordId: UUID,
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
    ): List<AuditLogEntry> =
        transaction {
            scopedQuery(
                windowBranchIds = windowBranchIds,
                branchlessTables = branchlessTables,
                canReadNullRows = canReadNullRows,
                base =
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId),
            ).map { it.toAuditLogEntry() }
        }.also {
            logger.info {
                "[FIND-AUDIT] Found ${it.size} entries " +
                    "for $tableName/${recordId.toString().maskUUID()}"
            }
        }

    fun findFlagged(
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
    ): List<AuditLogEntry> =
        transaction {
            scopedQuery(
                windowBranchIds = windowBranchIds,
                branchlessTables = branchlessTables,
                canReadNullRows = canReadNullRows,
                base = (AuditLogTable.isFlagged eq true) and AuditLogTable.acknowledgedAt.isNull(),
            ).map { it.toAuditLogEntry() }
        }.also { logger.info { "[FIND-FLAGGED] Found ${it.size} unacknowledged flagged entries" } }

    /**
     * Keyset browse over `(changed_at DESC, id DESC)`. [cursor] is the
     * strictly-before position (exclusive). [limit] rows are returned; the
     * caller decides pagination via [AuditLogService.browse] encoding the last row.
     */
    @Suppress("LongParameterList") // #600 10-param browse mirrors the service filter set 1:1 per #535
    fun browse(
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
        tableName: String?,
        action: AuditAction?,
        callerName: String?,
        dateFrom: OffsetDateTime?,
        dateTo: OffsetDateTime?,
        cursor: AuditBrowseCursor?,
        limit: Int,
    ): List<AuditLogEntry> =
        transaction {
            var base: Op<Boolean> = Op.TRUE
            if (tableName != null) base = base and (AuditLogTable.auditTableName eq tableName)
            if (action != null) base = base and (AuditLogTable.action eq action)
            if (callerName != null) {
                base = base and ilike(AppUserTable.displayName, "%$callerName%")
            }
            if (dateFrom != null) {
                base = base and (AuditLogTable.changedAt greaterEq dateFrom)
            }
            if (dateTo != null) {
                base = base and (AuditLogTable.changedAt less dateTo)
            }
            if (cursor != null) {
                val keyset =
                    (AuditLogTable.changedAt less cursor.changedAt) or
                        (
                            (AuditLogTable.changedAt eq cursor.changedAt) and
                                (AuditLogTable.id less cursor.id)
                        )
                base = base and keyset
            }
            scopedQuery(
                windowBranchIds = windowBranchIds,
                branchlessTables = branchlessTables,
                canReadNullRows = canReadNullRows,
                base = base,
            ).limit(limit).map { it.toAuditLogEntry() }
        }.also { logger.info { "[AUDIT-BROWSE] Returned ${it.size} entries" } }

    fun findById(entryId: UUID): AuditLogEntry? =
        transaction {
            AuditLogTable
                .withDisplayJoins()
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

    private fun ResultRow.toAuditLogEntry(): AuditLogEntry =
        AuditLogEntry(
            id = this[AuditLogTable.id],
            tableName = this[AuditLogTable.auditTableName],
            recordId = this[AuditLogTable.recordId],
            action = this[AuditLogTable.action],
            changedBy = this[AuditLogTable.changedBy],
            changedByName = this[AppUserTable.displayName],
            branchId = this[AuditLogTable.branchId],
            branchName = this[BranchTable.name],
            changedAt = this[AuditLogTable.changedAt],
            oldValue = this[AuditLogTable.oldValue],
            newValue = this[AuditLogTable.newValue],
            isFlagged = this[AuditLogTable.isFlagged],
            reason = this[AuditLogTable.reason],
            acknowledgedBy = this[AuditLogTable.acknowledgedBy],
            acknowledgedAt = this[AuditLogTable.acknowledgedAt],
        )
}

/**
 * Client audit keys whose values are nullified on anonymize (BR §Privacy and
 * Cleanup) and therefore must never survive in a retained payload (#524
 * redaction set, extended by #525 when the client vocabulary widened beyond
 * names). Gender/age/id are retained aggregates/identifiers and stay out.
 */
private val CLIENT_IDENTIFYING_KEYS =
    setOf(
        "firstName",
        "lastName",
        "middleName",
        "suffix",
        "phoneNumber",
        "address",
        "medicalConditions",
        "systolicBp",
        "diastolicBp",
    )

/**
 * One audit payload's PII redaction (#524, extended #525): identifying values
 * become the uniform [AuditValues.REDACTED] marker; JSON nulls, historical
 * `"null"` sentinels, existing markers, the `N/A` address default, non-object
 * shapes, and unparseable payloads pass through untouched so cleared-state
 * history keeps its shape.
 */
private fun redactClientNames(raw: String?): String? {
    if (raw == null) return null
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrElse { return raw }
    if (element !is JsonObject) return raw
    var changed = false
    val scrubbed =
        buildJsonObject {
            element.forEach { (key, value) ->
                val keep =
                    key !in CLIENT_IDENTIFYING_KEYS ||
                        value is JsonNull ||
                        value == JsonPrimitive(AuditValues.NULL) ||
                        value == JsonPrimitive(AuditValues.REDACTED) ||
                        value == JsonPrimitive(DEFAULT_CLIENT_ADDRESS)
                if (keep) {
                    put(key, value)
                } else {
                    put(key, JsonPrimitive(AuditValues.REDACTED))
                    changed = true
                }
            }
        }
    return if (changed) scrubbed.toString() else raw
}

private class AuditILikeOp(
    expr1: Expression<*>,
    expr2: Expression<*>,
) : ComparisonOp(expr1, expr2, "ILIKE")

@Suppress("UNCHECKED_CAST") // #600 String-backed columns narrow to IColumnType<String> per #467
private fun <T : String?> ilike(
    col: Column<T>,
    pattern: String,
): Op<Boolean> =
    AuditILikeOp(
        col,
        // SAFETY: ilike takes String-backed columns; columnType narrows to IColumnType<String> #467
        QueryParameter(pattern, col.columnType as IColumnType<String>),
    )
