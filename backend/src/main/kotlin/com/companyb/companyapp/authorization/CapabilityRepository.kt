package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.observability.traced
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal object CapabilityRepository {
    fun findIdByCode(code: String): UUID? =
        traced("CapabilityRepository.findIdByCode") {
            transaction {
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq code }
                    .singleOrNull()
                    ?.let { it[CapabilityTable.id] }
            }
        }

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = holdsIn(userId, capabilityCode, listOf(contextType to contextId))

    /** One row of [ActiveUserCapabilitiesView], with its code. */
    private data class Grant(
        val code: String,
        val contextType: CapabilityContextType,
        val contextId: UUID,
        val sourceType: CapabilitySourceType,
    )

    private class Grants(
        val rows: List<Grant>,
    )

    private data class GrantsKey(
        val userId: UUID,
    ) : RequestQueryTally.ReadKey

    /** The tables [ActiveUserCapabilitiesView] reads; CapabilityReadOncePostgresTest checks them against Postgres. */
    internal val VIEW_READS =
        setOf(
            "app_user",
            "branch",
            "capability",
            "role",
            "role_capability",
            "user_branch_assignment",
            "user_capability",
            "user_role",
        )

    /**
     * Every active grant of [userId], read once per request: planning the view costs more than
     * running it (about 3ms of 4, checked with EXPLAIN ANALYZE), and a request used to ask it
     * four to six times. The copy is dropped by a write to any table in [VIEW_READS].
     */
    private fun grantsOf(userId: UUID): List<Grant> =
        traced("CapabilityRepository.grantsOf") {
            RequestQueryTally
                .untilWrite(GrantsKey(userId), Grants::class, VIEW_READS) {
                    Grants(
                        transaction {
                            ActiveUserCapabilitiesView
                                .innerJoin(
                                    CapabilityTable,
                                    { ActiveUserCapabilitiesView.capabilityId },
                                    { CapabilityTable.id },
                                ).selectAll()
                                .where { ActiveUserCapabilitiesView.userId eq userId }
                                .map { row ->
                                    Grant(
                                        code = row[CapabilityTable.code],
                                        contextType = row[ActiveUserCapabilitiesView.contextType],
                                        contextId = row[ActiveUserCapabilitiesView.contextId],
                                        sourceType = row[ActiveUserCapabilitiesView.sourceType],
                                    )
                                }
                        },
                    )
                }.rows
        }

    fun findCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        grantsOf(userId)
            .map {
                UserCapabilityResponse(
                    capabilityCode = it.code,
                    contextType = it.contextType,
                    contextId = it.contextId.toString(),
                    sourceType = it.sourceType,
                )
            }.also { logger.info { "[FIND-CAPABILITIES] Fetched ${it.size} capabilities for user $userId" } }

    /**
     * Distinct branch context ids where [userId] holds any active grant — the
     * audit read window (D6, union pattern). GLOBAL grants contribute
     * nothing here; [ActiveUserCapabilitiesView] already excludes inactive
     * users and out-of-window grants.
     */
    fun findBranchWindow(userId: UUID): List<UUID> =
        contextsOf(userId, CapabilityContextType.BRANCH)
            .also { logger.info { "[BRANCH-WINDOW] $userId window=${it.size} branches" } }

    /** The branch days [userId] holds any grant for (relief), past, present or future. */
    fun findDayWindow(userId: UUID): List<UUID> = contextsOf(userId, CapabilityContextType.BRANCH_DAY)

    private fun contextsOf(
        userId: UUID,
        type: CapabilityContextType,
    ): List<UUID> = grantsOf(userId).filter { it.contextType == type }.map { it.contextId }.distinct()

    /** "Holds [capabilityCode] in any of [contexts]". The view applies the grant window. */
    fun holdsIn(
        userId: UUID,
        capabilityCode: String,
        contexts: List<Pair<CapabilityContextType, UUID>>,
    ): Boolean {
        if (contexts.isEmpty()) return false
        return grantsOf(userId)
            .any { it.code == capabilityCode && (it.contextType to it.contextId) in contexts }
            .also { granted -> logger.debug { "[HOLDS-IN] $capabilityCode $contexts granted=$granted" } }
    }

    /**
     * True when [userId] holds [capabilityCode] at any context (any
     * contextType/contextId) — the "any EDIT_BRANCH_DATA holder" policy.
     */
    fun hasCapabilityAnyContext(
        userId: UUID,
        capabilityCode: String,
    ): Boolean =
        grantsOf(userId).any { it.code == capabilityCode }.also { granted ->
            logger.debug { "[HAS-CAPABILITY-ANY] $capabilityCode granted=$granted" }
        }
}
