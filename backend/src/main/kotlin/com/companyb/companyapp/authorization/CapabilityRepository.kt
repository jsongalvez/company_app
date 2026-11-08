package com.companyb.companyapp.authorization

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import com.companyb.companyapp.workforce.relief.MedicalMissionDelegateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal object CapabilityRepository {
    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean = holdsIn(userId, capabilityCode, listOf(contextType to contextId))

    private class Grants(
        val rows: List<Grant>,
    )

    private data class GrantsKey(
        val userId: UUID,
    ) : RequestQueryTally.ReadKey

    /** Every grant of [userId] under [Policy], read once per request and dropped by a write to [VIEW_READS]. */
    private fun grantsOf(userId: UUID): List<Grant> =
        traced("CapabilityRepository.grantsOf") {
            RequestQueryTally
                .untilWrite(GrantsKey(userId), Grants::class, VIEW_READS) {
                    Grants(Policy.grants(transaction { factsOf(userId) }))
                }.rows
        }

    private val reachedUnassigned = {
        Role.entries
            .filter { it.worksUnassignedAt.isNotEmpty() }
            .map { (RoleTable.name eq it.name) and (BranchTable.branchType inList it.worksUnassignedAt) }
            .reduce { a, b -> a or b }
    }

    /** A person's roles, the branches they reach unassigned, their open assignments, relief days and helper seats. */
    private val FACTS =
        AppUserTable
            .join(UserRoleTable, JoinType.LEFT, AppUserTable.id, UserRoleTable.userId)
            .join(RoleTable, JoinType.LEFT, UserRoleTable.roleId, RoleTable.id)
            .join(BranchTable, JoinType.LEFT, additionalConstraint = reachedUnassigned)
            .join(
                UserBranchAssignmentTable,
                JoinType.LEFT,
                AppUserTable.id,
                UserBranchAssignmentTable.userId,
                additionalConstraint = { UserBranchAssignmentTable.endedAt.isNull() },
            ).join(
                ReliefDayTable,
                JoinType.LEFT,
                AppUserTable.id,
                ReliefDayTable.userId,
                additionalConstraint = { reliefInForce() },
            ).join(
                MedicalMissionDelegateTable,
                JoinType.LEFT,
                AppUserTable.id,
                MedicalMissionDelegateTable.targetUser,
                additionalConstraint = { MedicalMissionDelegateTable.endedAt.isNull() },
            )

    /** The tables a person's grants come from: a write to one drops the request's copy. */
    internal val VIEW_READS: Set<String> = FACTS.columns.map { it.table.tableName }.toSet()

    private fun reliefInForce(): Op<Boolean> =
        ReliefDayTable.validTo.isNull() or (ReliefDayTable.validTo greaterEq CurrentTimestampWithTimeZone)

    /**
     * What [Policy] needs about [userId], in one statement: their roles, the branches those roles
     * work at unassigned, their open assignments, their relief days in force and their open helper seats.
     * The left joins multiply into a handful of rows, which costs less than a statement each.
     */
    private fun factsOf(userId: UUID): Facts {
        val rows =
            FACTS
                .select(
                    AppUserTable.status,
                    RoleTable.name,
                    BranchTable.id,
                    UserBranchAssignmentTable.branchId,
                    ReliefDayTable.branchDayId,
                    MedicalMissionDelegateTable.branchId,
                    MedicalMissionDelegateTable.role,
                ).where { AppUserTable.id eq userId }
                .toList()
        val unassigned = mutableMapOf<UUID, MutableSet<Role>>()
        rows.forEach { row ->
            val role = row.getOrNull(RoleTable.name)?.let(Role::parse) ?: return@forEach
            row.getOrNull(BranchTable.id)?.let { unassigned.getOrPut(it) { mutableSetOf() } += role }
        }
        return Facts(
            active = rows.firstOrNull()?.get(AppUserTable.status) == UserStatus.ACTIVE,
            roles = rows.mapNotNull { row -> row.getOrNull(RoleTable.name)?.let(Role::parse) }.toSet(),
            homes = rows.mapNotNull { it.getOrNull(UserBranchAssignmentTable.branchId) }.toSet(),
            unassignedBranches = unassigned,
            reliefDays = rows.mapNotNull { it.getOrNull(ReliefDayTable.branchDayId) }.toSet(),
            helperSeats =
                rows
                    .mapNotNull { row ->
                        val branch = row.getOrNull(MedicalMissionDelegateTable.branchId) ?: return@mapNotNull null
                        branch to DelegateRole.valueOf(row[MedicalMissionDelegateTable.role])
                    }.groupBy({ it.first }, { it.second })
                    .mapValues { it.value.toSet() },
        )
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
     * nothing here; inactive users and grants out of force hold nothing.
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

    /**
     * Active people whose relief day [branchDayId] is in force,
     * only [userId] when given. Runs on the caller's transaction.
     */
    fun reliefHoldersInTransaction(
        branchDayId: UUID,
        userId: UUID? = null,
    ): Set<UUID> =
        ReliefDayTable
            .innerJoin(AppUserTable, { ReliefDayTable.userId }, { AppUserTable.id })
            .select(ReliefDayTable.userId)
            .where {
                val onDay =
                    (ReliefDayTable.branchDayId eq branchDayId) and
                        (AppUserTable.status eq UserStatus.ACTIVE) and
                        reliefInForce()
                if (userId == null) onDay else onDay and (ReliefDayTable.userId eq userId)
            }.withDistinct()
            .map { it[ReliefDayTable.userId] }
            .toSet()

    /**
     * For each of [branchIds], the active people assigned there who get next-appointment alerts:
     * by a role in [Policy.ALERTED_ROLES], or by a helper seat there under a role in [Policy.ALERTED_HELPERS].
     * Runs on the caller's transaction.
     */
    fun alertedByBranchInTransaction(branchIds: Collection<UUID>): Map<UUID, List<UUID>> {
        val byRole =
            exists(
                UserRoleTable
                    .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
                    .select(UserRoleTable.userId)
                    .where {
                        (UserRoleTable.userId eq UserBranchAssignmentTable.userId) and
                            (RoleTable.name inList Policy.ALERTED_ROLES.map { it.name })
                    },
            )
        val bySeat =
            exists(
                MedicalMissionDelegateTable
                    .select(MedicalMissionDelegateTable.targetUser)
                    .where {
                        (MedicalMissionDelegateTable.targetUser eq UserBranchAssignmentTable.userId) and
                            (MedicalMissionDelegateTable.branchId eq UserBranchAssignmentTable.branchId) and
                            (MedicalMissionDelegateTable.role inList Policy.ALERTED_HELPERS.map { it.name }) and
                            MedicalMissionDelegateTable.endedAt.isNull()
                    },
            )
        return UserBranchAssignmentTable
            .innerJoin(AppUserTable, { UserBranchAssignmentTable.userId }, { AppUserTable.id })
            .select(UserBranchAssignmentTable.userId, UserBranchAssignmentTable.branchId)
            .where {
                (UserBranchAssignmentTable.branchId inList branchIds) and
                    UserBranchAssignmentTable.endedAt.isNull() and
                    (AppUserTable.status eq UserStatus.ACTIVE) and
                    (byRole or bySeat)
            }.withDistinct()
            .map { it[UserBranchAssignmentTable.branchId] to it[UserBranchAssignmentTable.userId] }
            .groupBy({ it.first }, { it.second })
    }
}
