package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

data class AddLineParams(
    val id: UUID,
    val remittanceId: UUID,
    val type: RemittanceLineType,
    val sessionId: UUID?,
    val productSaleId: UUID?,
    val amount: BigDecimal,
    val createdBy: UUID,
    val expectedVersion: Int,
)

data class AddLineResult(
    val line: RemittanceLine,
    val created: Boolean,
)

private val logger = KotlinLogging.logger {}

/**
 * Remittance-line store (#320, ADR-0024). Mutating functions are in-transaction store operations
 * on the caller's (command-owned) transaction — they keep the idempotent-insert, duplicate-source
 * guard, and optimistic-version bump; audit stays with the command. Read helpers keep wrappers.
 */
internal object RemittanceLineRepository {
    /** Idempotency probe for a retried add request; asserts payload match on a hit. */
    fun findExistingRequestInTransaction(params: AddLineParams): RemittanceLine? =
        RemittanceLineTable
            .selectAll()
            .where {
                (RemittanceLineTable.id eq params.id) and
                    (RemittanceLineTable.remittanceId eq params.remittanceId)
            }.singleOrNull()
            ?.let { row ->
                val line = row.toRemittanceLine()
                assertRequestMatches(line, params)
                line
            }

    /**
     * Inserts the line and bumps the parent version atomically. `created` distinguishes a fresh
     * insert (the command audits it) from an idempotent/raced return of an identical request
     * (no new audit — the original insert already has one).
     */
    fun addLineInTransaction(params: AddLineParams): AddLineResult {
        val existing =
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.id eq params.id) and
                        (RemittanceLineTable.remittanceId eq params.remittanceId)
                }.singleOrNull()
        if (existing != null) {
            assertRequestMatches(existing.toRemittanceLine(), params)
            return AddLineResult(existing.toRemittanceLine(), created = false)
        }

        assertNotAlreadyIncluded(params)

        val inserted =
            RemittanceLineTable.insertIgnore {
                it[RemittanceLineTable.id] = params.id
                it[RemittanceLineTable.remittanceId] = params.remittanceId
                it[RemittanceLineTable.type] = params.type
                it[RemittanceLineTable.sessionId] = params.sessionId
                it[RemittanceLineTable.productSaleId] = params.productSaleId
                it[RemittanceLineTable.amount] = params.amount
                it[RemittanceLineTable.createdBy] = params.createdBy
            }
        // #601 max-2: raced-retry and fresh-insert share one exit.
        return if (inserted.insertedCount == 0) {
            AddLineResult(racedRetryOrFail(params), created = false)
        } else {
            val versionUpdated =
                RemittanceTable.update({
                    (RemittanceTable.id eq params.remittanceId) and (RemittanceTable.version eq params.expectedVersion)
                }) {
                    it[RemittanceTable.version] = params.expectedVersion + 1
                }

            if (versionUpdated == 0) {
                throw remittanceVersionMismatch(params.remittanceId)
            }

            val created =
                RemittanceLineTable
                    .selectAll()
                    .where {
                        (RemittanceLineTable.id eq params.id) and
                            (RemittanceLineTable.remittanceId eq params.remittanceId)
                    }.single()
                    .toRemittanceLine()

            logger.info {
                "[ADD-REMITTANCE-LINE] Line ${created.id.toString().maskUUID()} added to " +
                    "remittance ${created.remittanceId.toString().maskUUID()}"
            }
            AddLineResult(created, created = true)
        }
    }

    /**
     * Soft-deletes the line scoped to its parent remittance (the parent-child convention) and
     * bumps the parent version atomically. Null when the line does not exist under the parent;
     * an already-deleted line is returned unchanged (idempotent retry, no new audit image).
     * Returns the before/after images so the command can audit the diff.
     */
    fun softDeleteLineInTransaction(
        lineId: UUID,
        remittanceId: UUID,
        deletedBy: UUID,
        expectedVersion: Int,
    ): Pair<RemittanceLine, RemittanceLine>? {
        val existing =
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.id eq lineId) and (RemittanceLineTable.remittanceId eq remittanceId)
                }.singleOrNull() ?: return null

        val beforeLine = existing.toRemittanceLine()
        if (existing[RemittanceLineTable.deletedAt] != null) {
            return beforeLine to beforeLine
        }

        RemittanceLineTable
            .update({
                (RemittanceLineTable.id eq lineId) and
                    (RemittanceLineTable.remittanceId eq remittanceId) and
                    RemittanceLineTable.deletedAt.isNull()
            }) {
                it[RemittanceLineTable.deletedBy] = deletedBy
                it[RemittanceLineTable.deletedAt] =
                    CurrentTimestampWithTimeZone
            }

        val versionUpdated =
            RemittanceTable.update({
                (RemittanceTable.id eq remittanceId) and (RemittanceTable.version eq expectedVersion)
            }) {
                it[RemittanceTable.version] = expectedVersion + 1
            }

        if (versionUpdated == 0) {
            throw remittanceVersionMismatch(remittanceId)
        }

        val afterLine =
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.id eq lineId) and
                        (RemittanceLineTable.remittanceId eq remittanceId)
                }.single()
                .toRemittanceLine()

        logger.info { "[DELETE-REMITTANCE-LINE] Line ${afterLine.id.toString().maskUUID()} deleted" }
        return beforeLine to afterLine
    }

    fun findByRemittanceId(remittanceId: UUID): List<RemittanceLine> =
        transaction {
            findByRemittanceIdInTransaction(remittanceId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByRemittanceIdInTransaction(remittanceId: UUID): List<RemittanceLine> =
        RemittanceLineTable
            .selectAll()
            .where {
                (RemittanceLineTable.remittanceId eq remittanceId) and
                    RemittanceLineTable.deletedAt.isNull()
            }.map { it.toRemittanceLine() }

    fun sumAmountsByRemittanceId(remittanceId: UUID): BigDecimal =
        transaction {
            RemittancePolicy.sum(
                RemittanceLineTable
                    .selectAll()
                    .where {
                        (RemittanceLineTable.remittanceId eq remittanceId) and
                            RemittanceLineTable.deletedAt.isNull()
                    }.map { it[RemittanceLineTable.amount] },
            )
        }

    /**
     * Resolves a lost insert race: another transaction committed an identical request between
     * this command's probe and its `insertIgnore`. Returns the existing line after asserting the
     * payload matches; a different source entity under the same ID is a conflict.
     */
    private fun racedRetryOrFail(params: AddLineParams): RemittanceLine {
        val racedRetry =
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.id eq params.id) and
                        (RemittanceLineTable.remittanceId eq params.remittanceId) and
                        entityRefCondition(params)
                }.singleOrNull()
        if (racedRetry != null) {
            assertRequestMatches(racedRetry.toRemittanceLine(), params)
            return racedRetry.toRemittanceLine()
        }
        throw ConflictException(duplicateMessage(params.type))
    }

    private fun assertNotAlreadyIncluded(params: AddLineParams) {
        val existingLine =
            RemittanceLineTable
                .selectAll()
                .where {
                    entityRefCondition(params) and
                        RemittanceLineTable.deletedAt.isNull() and
                        (RemittanceLineTable.id neq params.id)
                }.singleOrNull()
        if (existingLine != null) {
            throw ConflictException(duplicateMessage(params.type))
        }
    }

    private fun assertRequestMatches(
        existing: RemittanceLine,
        params: AddLineParams,
    ) {
        val matches =
            existing.type == params.type &&
                existing.sessionId == params.sessionId &&
                existing.productSaleId == params.productSaleId &&
                existing.amount.compareTo(params.amount) == 0 &&
                existing.createdBy == params.createdBy
        if (!matches) {
            throw ConflictException("Remittance line UUID already belongs to a different request")
        }
    }

    private fun entityRefCondition(params: AddLineParams): Op<Boolean> =
        when (params.type) {
            RemittanceLineType.SESSION -> {
                val sessionId =
                    params.sessionId
                        ?: throw ValidationException("sessionId is required for SESSION line type")
                RemittanceLineTable.sessionId eq sessionId
            }

            RemittanceLineType.PRODUCT_SALE -> {
                val productSaleId =
                    params.productSaleId
                        ?: throw ValidationException("productSaleId is required for PRODUCT_SALE line type")
                RemittanceLineTable.productSaleId eq productSaleId
            }

            // #876 — the forward-compat sentinel is never valid input; the strict transport
            // rejects unknown strings before this runs, and this rejects a literal UNKNOWN.
            RemittanceLineType.UNKNOWN -> {
                throw ValidationException("Unknown remittance line type")
            }
        }

    private fun duplicateMessage(type: RemittanceLineType): String =
        entityLabel(type) + " already included in a remittance line"

    private fun entityLabel(type: RemittanceLineType): String =
        when (type) {
            RemittanceLineType.SESSION -> {
                "Session"
            }

            RemittanceLineType.PRODUCT_SALE -> {
                "Product sale"
            }

            // #876 — the forward-compat sentinel is never valid input.
            RemittanceLineType.UNKNOWN -> {
                throw ValidationException("Unknown remittance line type")
            }
        }

    private fun ResultRow.toRemittanceLine(): RemittanceLine =
        RemittanceLine(
            id = this[RemittanceLineTable.id],
            remittanceId = this[RemittanceLineTable.remittanceId],
            type = this[RemittanceLineTable.type],
            sessionId = this[RemittanceLineTable.sessionId],
            productSaleId = this[RemittanceLineTable.productSaleId],
            createdBy = this[RemittanceLineTable.createdBy],
            createdAt = this[RemittanceLineTable.createdAt],
            deletedBy = this[RemittanceLineTable.deletedBy],
            deletedAt = this[RemittanceLineTable.deletedAt],
            amount = this[RemittanceLineTable.amount],
        )
}
