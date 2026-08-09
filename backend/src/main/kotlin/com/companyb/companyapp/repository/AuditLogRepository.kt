package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.repository.model.AuditLogTable
import io.github.oshai.kotlinlogging.KotlinLogging
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
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** Keyset cursor for audit browse: strictly-before position on `(changed_at, id) DESC`. */
data class AuditBrowseCursor(
    val changedAt: OffsetDateTime,
    val id: UUID,
)

@Suppress("TooManyFunctions")
object AuditLogRepository {
    @Suppress("LongParameterList")
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

    @Suppress("LongParameterList")
    fun recordInsert(
        tableName: String,
        recordId: UUID,
        changedBy: UUID,
        fields: Map<String, String>,
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
            newValue = jsonFields(fields),
            reason = reason,
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
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
            reason = reason,
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
        branchId: UUID? = null,
        isFlagged: Boolean = false,
        reason: String? = null,
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
            branchId = branchId,
            isFlagged = isFlagged,
            reason = reason,
        )
    }

    @Suppress("LongParameterList")
    fun <T> recordDelete(
        tableName: String,
        recordId: UUID,
        before: T,
        changedBy: UUID,
        branchId: UUID? = null,
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
            branchId = branchId,
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
            oldValue = jsonFields(oldFields),
            newValue = jsonFields(newFields),
            reason = reason,
            isFlagged = isFlagged,
        )
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

    private fun scopedQuery(
        windowBranchIds: List<UUID>?,
        branchlessTables: Set<String>,
        canReadNullRows: Boolean,
        base: Op<Boolean>,
    ): Query =
        AuditLogTable
            .leftJoin(AppUserTable, { AuditLogTable.changedBy }, { AppUserTable.id })
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
     * caller decides pagination via [encodeCursor] on the last row.
     */
    @Suppress("LongParameterList")
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
                .leftJoin(AppUserTable, { AuditLogTable.changedBy }, { AppUserTable.id })
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
            changedByName = this[AppUserTable.displayName],
            branchId = this[AuditLogTable.branchId],
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
 * Opaque URL-safe cursor encoding for audit browse: `changedAt|id`, base64url.
 * Format is internal — decode with [decodeCursor]; never parse client-side.
 */
fun encodeCursor(cursor: AuditBrowseCursor): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString("${cursor.changedAt}|${cursor.id}".toByteArray(Charsets.UTF_8))

fun decodeCursor(raw: String): AuditBrowseCursor {
    val decoded =
        runCatching {
            String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("Invalid audit cursor") }
    val parts = decoded.split("|")
    require(parts.size == 2) { "Invalid audit cursor" }
    return AuditBrowseCursor(
        changedAt = OffsetDateTime.parse(parts[0]),
        id = UUID.fromString(parts[1]),
    )
}

private class AuditILikeOp(
    expr1: Expression<*>,
    expr2: Expression<*>,
) : ComparisonOp(expr1, expr2, "ILIKE")

@Suppress("UNCHECKED_CAST")
private fun <T : String?> ilike(
    col: Column<T>,
    pattern: String,
): Op<Boolean> =
    AuditILikeOp(
        col,
        QueryParameter(pattern, col.columnType as IColumnType<String>),
    )
