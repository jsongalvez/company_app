package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.CapabilityTable
import com.companyb.companyapp.identity.RoleCapabilityTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Role catalog reads and `user_role` membership store operations (#344). Roles are
 * seeded bundles (V2) — never created at runtime — so there are no role CRUD mutators;
 * the only writes are membership rows, owned by the [com.companyb.companyapp.identity.UserService]
 * commands per ADR-0024.
 */
internal object RoleRepository {
    /** Seeded role with its capability codes, name ASC. */
    data class RoleWithCapabilities(
        val id: UUID,
        val name: String,
        val capabilities: List<String>,
    )

    fun findAllWithCapabilities(): List<RoleWithCapabilities> =
        transaction {
            val capabilityCodesByRole =
                RoleTable
                    .innerJoin(RoleCapabilityTable, { RoleTable.id }, { RoleCapabilityTable.roleId })
                    .innerJoin(CapabilityTable, { RoleCapabilityTable.capabilityId }, { CapabilityTable.id })
                    .selectAll()
                    .orderBy(CapabilityTable.code to SortOrder.ASC)
                    .groupBy({ it[RoleTable.id] }) { it[CapabilityTable.code] }
            RoleTable.selectAll().orderBy(RoleTable.name to SortOrder.ASC).map { row ->
                val roleId = row[RoleTable.id]
                RoleWithCapabilities(
                    id = roleId,
                    name = row[RoleTable.name],
                    capabilities = capabilityCodesByRole[roleId].orEmpty(),
                )
            }
        }.also { logger.info { "[FIND-ALL-ROLES] Fetched ${it.size} role(s)" } }

    /** In-transaction store lookup (ADR-0024) — resolves a seeded role by exact name. */
    fun findIdByNameInTransaction(name: String): UUID? =
        RoleTable
            .select(RoleTable.id)
            .where { RoleTable.name eq name }
            .singleOrNull()
            ?.get(RoleTable.id)

    /** Role names per user for the given ids; names ASC within each user. */
    fun findRoleNamesByUser(userIds: Collection<UUID>): Map<UUID, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return transaction {
            UserRoleTable
                .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
                .selectAll()
                .where { UserRoleTable.userId inList userIds }
                .orderBy(RoleTable.name to SortOrder.ASC)
                .groupBy({ it[UserRoleTable.userId] }, { it[RoleTable.name] })
        }.also { logger.info { "[FIND-ROLE-NAMES-BY-USER] Fetched roles for ${it.size} user(s)" } }
    }

    /** In-transaction store read of the target's current role names (before-state capture). */
    fun findRoleNamesForUserInTransaction(userId: UUID): List<String> =
        UserRoleTable
            .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
            .selectAll()
            .where { UserRoleTable.userId eq userId }
            .orderBy(RoleTable.name to SortOrder.ASC)
            .map { it[RoleTable.name] }

    /**
     * In-transaction full replace (ADR-0024): deletes all membership rows then inserts
     * the requested set. Idempotent — rewriting the same set yields equal state; the
     * command skips the audit row when before/after sets are equal.
     */
    fun replaceUserRolesInTransaction(
        userId: UUID,
        roleIds: List<UUID>,
    ) {
        UserRoleTable.deleteWhere { UserRoleTable.userId eq userId }
        roleIds.forEach { roleId ->
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    /** In-transaction membership insert (ADR-0024) — one row per assigned role. */
    fun assignRoleInTransaction(
        userId: UUID,
        roleId: UUID,
    ) {
        UserRoleTable.insert {
            it[UserRoleTable.userId] = userId
            it[UserRoleTable.roleId] = roleId
        }
    }
}
