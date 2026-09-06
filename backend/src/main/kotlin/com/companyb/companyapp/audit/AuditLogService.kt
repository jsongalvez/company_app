package com.companyb.companyapp.audit

import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

object AuditLogService {
    private fun window(callerId: UUID) = AuditLogReadScope.windowBranchIds(callerId)

    private fun branchlessTables(callerId: UUID) = AuditLogReadScope.branchlessTablesReadableBy(callerId)

    private fun canReadNullRows(callerId: UUID) = AuditLogReadScope.canReadNullRows(callerId)

    /**
     * Per-record history (#104 D8 "Full history for this record"), scoped to
     * the caller's read window. A record whose rows all sit outside the window
     * returns an empty list — the rows are invisible, not an error.
     */
    fun findByTableAndRecord(
        callerId: UUID,
        tableName: String,
        recordId: UUID,
    ): List<AuditLogEntry> {
        logger.info { "[AUDIT-LIST] $callerId fetching audit entries for $tableName/$recordId" }
        return AuditLogStore.findByTableAndRecord(
            tableName = tableName,
            recordId = recordId,
            windowBranchIds = window(callerId),
            branchlessTables = branchlessTables(callerId),
            canReadNullRows = canReadNullRows(callerId),
        )
    }

    fun findFlagged(callerId: UUID): List<AuditLogEntry> {
        logger.info { "[AUDIT-FLAGGED] $callerId fetching unacknowledged flagged entries" }
        return AuditLogStore.findFlagged(
            windowBranchIds = window(callerId),
            branchlessTables = branchlessTables(callerId),
            canReadNullRows = canReadNullRows(callerId),
        )
    }

    /**
     * Acknowledge within the read window, minus the editor (D2): the editor
     * cannot clear their own flag — self-acknowledge is a 409.
     */
    @Suppress("ThrowsCount")
    fun acknowledge(
        callerId: UUID,
        entryId: UUID,
    ): AuditLogEntry {
        val entry =
            AuditLogStore.findById(entryId)
                ?: throw NotFoundException("Audit entry not found")
        if (!AuditLogReadScope.canReadEntry(callerId, entry.tableName, entry.branchId)) {
            throw NotFoundException("Audit entry not found")
        }

        if (entry.changedBy == callerId) {
            throw ConflictException("Editor cannot acknowledge their own flagged entry")
        }

        val updated = AuditLogStore.acknowledge(entryId, callerId)
        if (!updated) throw NotFoundException("Audit entry not found or already acknowledged")

        return AuditLogStore.findById(entryId)
            ?: throw NotFoundException("Audit entry not found")
    }

    /**
     * Browse with filters + keyset pagination (D5). Returns one page; the
     * caller receives [AuditLogBrowseResponse.nextCursor] to fetch the next.
     */
    @Suppress("LongParameterList")
    fun browse(
        callerId: UUID,
        tableName: String?,
        action: AuditAction?,
        callerName: String?,
        dateFrom: OffsetDateTime?,
        dateTo: OffsetDateTime?,
        cursor: AuditBrowseCursor?,
        limit: Int,
    ): AuditLogBrowseResponse {
        logger.info { "[AUDIT-BROWSE] $callerId browsing audit log" }
        val fetched =
            AuditLogStore.browse(
                windowBranchIds = window(callerId),
                branchlessTables = branchlessTables(callerId),
                canReadNullRows = canReadNullRows(callerId),
                tableName = tableName,
                action = action,
                callerName = callerName,
                dateFrom = dateFrom,
                dateTo = dateTo,
                cursor = cursor,
                limit = limit + 1,
            )
        val hasMore = fetched.size > limit
        val entries = if (hasMore) fetched.dropLast(1) else fetched
        val nextCursor =
            if (hasMore) {
                entries.lastOrNull()?.let { encodeCursor(AuditBrowseCursor(it.changedAt, it.id)) }
            } else {
                null
            }
        return AuditLogBrowseResponse(
            entries = entries.map { it.toResponse() },
            nextCursor = nextCursor,
        )
    }

    /** Server-driven table list (D4): the #121 audited-table registry. */
    fun listTables(): List<AuditLogTableResponse> =
        AuditLogTableRegistry.tables.map {
            AuditLogTableResponse(tableName = it.tableName, label = it.label)
        }
}

fun AuditLogEntry.toResponse(): AuditLogEntryResponse =
    AuditLogEntryResponse(
        id = id.toString(),
        tableName = tableName,
        recordId = recordId.toString(),
        action = action,
        changedBy = changedBy.toString(),
        changedByName = changedByName,
        branchId = branchId?.toString(),
        branchName = branchName,
        changedAt = changedAt.toString(),
        oldValue = oldValue,
        newValue = newValue,
        isFlagged = isFlagged,
        reason = reason,
        acknowledgedBy = acknowledgedBy?.toString(),
        acknowledgedAt = acknowledgedAt?.toString(),
    )
