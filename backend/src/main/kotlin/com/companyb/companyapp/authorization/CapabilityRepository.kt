package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal object CapabilityRepository {
    fun findIdByCode(code: String): UUID? =
        transaction {
            CapabilityTable
                .selectAll()
                .where { CapabilityTable.code eq code }
                .singleOrNull()
                ?.let { it[CapabilityTable.id] }
        }

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = holdsIn(userId, capabilityCode, listOf(contextType to contextId))

    fun findCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where { ActiveUserCapabilitiesView.userId eq userId }
                .map { row ->
                    UserCapabilityResponse(
                        capabilityCode = row[CapabilityTable.code],
                        contextType = row[ActiveUserCapabilitiesView.contextType],
                        contextId = row[ActiveUserCapabilitiesView.contextId].toString(),
                        sourceType = row[ActiveUserCapabilitiesView.sourceType],
                    )
                }
        }.also { logger.info { "[FIND-CAPABILITIES] Fetched ${it.size} capabilities for user $userId" } }

    /**
     * Distinct branch context ids where [userId] holds any active grant — the
     * audit read window (D6, union pattern). GLOBAL grants contribute
     * nothing here; [ActiveUserCapabilitiesView] already excludes inactive
     * users and out-of-window grants.
     */
    fun findBranchWindow(userId: UUID): List<UUID> =
        transaction {
            ActiveUserCapabilitiesView
                .selectAll()
                .where {
                    (ActiveUserCapabilitiesView.userId eq userId) and
                        (ActiveUserCapabilitiesView.contextType eq CapabilityContextType.BRANCH)
                }.withDistinct()
                .map { it[ActiveUserCapabilitiesView.contextId] }
        }.also { logger.info { "[BRANCH-WINDOW] $userId window=${it.size} branches" } }

    /** One read for "holds [capabilityCode] in any of [contexts]". The view applies the grant window. */
    fun holdsIn(
        userId: UUID,
        capabilityCode: String,
        contexts: List<Pair<CapabilityContextType, UUID>>,
    ): Boolean {
        if (contexts.isEmpty()) return false
        return transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where {
                    val inAnyContext =
                        contexts
                            .map { (type, id) ->
                                (ActiveUserCapabilitiesView.contextType eq type) and
                                    (ActiveUserCapabilitiesView.contextId eq id)
                            }.reduce { left, right -> left or right }
                    (ActiveUserCapabilitiesView.userId eq userId) and (CapabilityTable.code eq capabilityCode) and
                        inAnyContext
                }.empty()
                .not()
        }.also { granted -> logger.debug { "[HOLDS-IN] $capabilityCode $contexts granted=$granted" } }
    }

    /**
     * True when [userId] holds [capabilityCode] at any context (any
     * contextType/contextId) — the "any EDIT_BRANCH_DATA holder" policy.
     */
    fun hasCapabilityAnyContext(
        userId: UUID,
        capabilityCode: String,
    ): Boolean =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where {
                    (ActiveUserCapabilitiesView.userId eq userId) and
                        (CapabilityTable.code eq capabilityCode)
                }.empty()
                .not()
        }.also { granted ->
            logger.debug { "[HAS-CAPABILITY-ANY] $capabilityCode granted=$granted" }
        }
}
