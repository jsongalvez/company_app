package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object CapabilityRepository {
    // All operational permission checks MUST traverse the active_user_capabilities view.
    // Role memberships are never queried at runtime.
    private val EXISTS_SQL =
        """
        SELECT EXISTS (
            SELECT 1
            FROM active_user_capabilities auc
            JOIN capability c ON c.id = auc.capability_id
            WHERE auc.user_id = ?::uuid
              AND c.code = ?
              AND auc.context_type = ?::capability_context_type
              AND auc.context_id = ?::uuid
        )
        """.trimIndent()

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean =
        transaction {
            exec(
                EXISTS_SQL,
                args =
                    listOf(
                        TextColumnType() to userId.toString(),
                        TextColumnType() to capabilityCode,
                        TextColumnType() to contextType.name,
                        TextColumnType() to contextId.toString(),
                    ),
            ) { rs -> if (rs.next()) rs.getBoolean(1) else false } ?: false
        }.also { granted ->
            logger.info { "[HAS-CAPABILITY] $capabilityCode ($contextType) granted=$granted" }
        }
}
