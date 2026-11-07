package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.AuthorizationGrants
import com.companyb.companyapp.authorization.GrantReliefCapabilityParams
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.authorization.Rule
import com.companyb.companyapp.authorization.Standing
import com.companyb.companyapp.authorization.UserCapabilityTable
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import com.companyb.companyapp.workforce.relief.MedicalMissionDelegateRepository
import com.companyb.companyapp.workforce.relief.MedicalMissionDelegateTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Gives a test person what the policy table needs for an action, so a test asks for a right and not for
 * the rows behind it. A rule change then moves the table and its pinned matrix, not every test that
 * happens to need the right. Tests about who may do what name roles and standings themselves.
 */
object AccessFixtures {
    /**
     * Makes [Act] allowed for [userId] at [place] with the least-privileged role the table allows it to:
     * standing at the place's branch as a member when a rule asks for that, else by role alone, else
     * as a member of a branch of their own.
     */
    fun allow(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
    ) {
        val branch = place.branchId
        val rule =
            act.rules.firstOrNull { branch != null && it is Rule.Here && Standing.MEMBER in it.standings }
                ?: act.rules.firstOrNull { it is Rule.Everywhere }
                ?: act.rules.first { it is Rule.Somewhere }
        hold(userId, leastPrivileged(rule.roles))
        when (rule) {
            is Rule.Everywhere -> Unit
            is Rule.Here -> member(userId, checkNotNull(branch))
            is Rule.Somewhere -> member(userId, branch ?: ownBranch())
        }
    }

    /**
     * Makes [userId] relief on [dayId] through the relief writer, as a [role]
     * (relief works under the person's roles).
     */
    fun relief(
        userId: UUID,
        dayId: UUID,
        role: Role = Role.PRACTITIONER,
        window: ClosedRange<OffsetDateTime>? = null,
    ) {
        hold(userId, role)
        val sourceId = TestFixtures.uuid()
        transaction {
            AuthorizationGrants.grantReliefCapabilityInTransaction(
                GrantReliefCapabilityParams(userId, dayId, sourceId, validTo = null),
            )
            if (window != null) {
                UserCapabilityTable.update({ UserCapabilityTable.sourceId eq sourceId }) {
                    it[validFrom] = window.start
                    it[validTo] = window.endInclusive
                }
            }
        }
    }

    /**
     * Adds [userId] to the mission or tour [branchId] as a helper under [role],
     * as an Owner or Manager would. Returns the helper id.
     */
    fun helper(
        userId: UUID,
        branchId: UUID,
        role: DelegateRole = DelegateRole.PRACTITIONER,
    ): UUID {
        val id = TestFixtures.uuid()
        transaction {
            MedicalMissionDelegateRepository.assignInTransaction(
                id,
                userId,
                assignedBy = userId,
                branchId = branchId,
                role = role,
            )
            AuthorizationGrants.grantDelegateCapabilityInTransaction(userId, branchId, id, role)
        }
        return id
    }

    /** Ends the helper [helperId]. */
    fun endHelper(helperId: UUID) {
        transaction {
            MedicalMissionDelegateRepository.revokeInTransaction(helperId)
            AuthorizationGrants.closeDelegateCapabilityInTransaction(helperId)
        }
    }

    /** Takes away every fact that gives [userId] access: roles, assignments, relief days and helper places. */
    fun revokeAll(userId: UUID) {
        transaction {
            UserRoleTable.deleteWhere { UserRoleTable.userId eq userId }
            UserBranchAssignmentTable.deleteWhere { UserBranchAssignmentTable.userId eq userId }
            MedicalMissionDelegateTable.deleteWhere { MedicalMissionDelegateTable.targetUser eq userId }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
        }
    }

    /** Gives [userId] the role [role] unless they hold it. */
    fun hold(
        userId: UUID,
        role: Role,
    ) {
        transaction {
            val roleId = RoleTable.selectAll().where { RoleTable.name eq role.name }.single()[RoleTable.id]
            val held =
                UserRoleTable
                    .selectAll()
                    .where { (UserRoleTable.userId eq userId) and (UserRoleTable.roleId eq roleId) }
                    .any()
            if (!held) {
                UserRoleTable.insert {
                    it[UserRoleTable.userId] = userId
                    it[UserRoleTable.roleId] = roleId
                }
            }
        }
    }

    /** Assigns [userId] to [branchId] unless they are assigned there already. */
    fun member(
        userId: UUID,
        branchId: UUID,
    ) {
        transaction {
            val assigned =
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            (UserBranchAssignmentTable.branchId eq branchId) and
                            UserBranchAssignmentTable.endedAt.isNull()
                    }.any()
            if (!assigned) {
                BranchWorkforceFixtures.insertTestAssignment(
                    userId = userId,
                    branchId = branchId,
                    slot = 0,
                    assignedBy = userId,
                )
            }
        }
    }

    private fun ownBranch(): UUID = TestFixtures.uuid().also { BranchWorkforceFixtures.insertTestBranch(it) }

    private val actsPerRole: Map<Role, Int> =
        Role.entries.associateWith { role -> Act.entries.count { act -> act.rules.any { role in it.roles } } }

    private fun leastPrivileged(roles: Set<Role>): Role =
        roles.minWith(
            compareBy({
                actsPerRole.getValue(it)
            }, { it.ordinal }),
        )
}
