package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.authorization.ReliefDayTable
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import com.companyb.companyapp.workforce.relief.MedicalMissionDelegateTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * People and the facts that give them rights: roles, memberships, relief days and helper seats.
 * Branch, session, client and commerce/finance rows live in their own fixture families.
 */
object IdentityFixtures {
    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertUser(
        id: UUID,
        username: String,
        passwordHash: String,
        email: String,
        displayName: String,
        status: UserStatus = UserStatus.ACTIVE,
    ) {
        transaction {
            AppUserTable.insert {
                it[AppUserTable.id] = id
                it[AppUserTable.username] = username
                it[AppUserTable.passwordHash] = passwordHash
                it[AppUserTable.status] = status
                it[AppUserTable.email] = email
                it[AppUserTable.displayName] = displayName
            }
        }
    }

    /** [userId] works [branchDayId] as relief, granted by [sourceId], until [validTo] when given. */
    fun reliefDay(
        userId: UUID,
        branchDayId: UUID,
        sourceId: UUID,
        validTo: OffsetDateTime? = null,
    ) {
        transaction {
            ReliefDayTable.insert {
                it[ReliefDayTable.userId] = userId
                it[ReliefDayTable.branchDayId] = branchDayId
                it[ReliefDayTable.sourceId] = sourceId
                it[ReliefDayTable.validTo] = validTo
            }
        }
    }

    /** Seats [userId] as a helper at [branchId] under [role]; returns the seat's id. */
    fun helperSeat(
        userId: UUID,
        branchId: UUID,
        role: DelegateRole = DelegateRole.PRACTITIONER,
        ended: Boolean = false,
    ): UUID =
        TestFixtures.uuid().also { id ->
            transaction {
                MedicalMissionDelegateTable.insert {
                    it[MedicalMissionDelegateTable.id] = id
                    it[MedicalMissionDelegateTable.targetUser] = userId
                    it[MedicalMissionDelegateTable.assignedBy] = userId
                    it[MedicalMissionDelegateTable.branchId] = branchId
                    it[MedicalMissionDelegateTable.role] = role.name
                    if (ended) it[MedicalMissionDelegateTable.endedAt] = OffsetDateTime.now().minusDays(1)
                }
            }
        }

    fun insertTestUser(
        id: UUID,
        prefix: String,
    ) {
        insertUser(
            id = id,
            username = "$prefix-${id.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "${id.toString().take(8)}@t.st",
            displayName = "Test $prefix",
        )
    }

    /** Gives [userId] the seeded role [roleName]; at a branch they are assigned to, that makes them a member. */
    fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        transaction {
            val roleId = RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    /** Gives [userId] [role] unless they hold it already. */
    fun hold(
        userId: UUID,
        role: Role,
    ) {
        transaction {
            val roleId = RoleTable.selectAll().where { RoleTable.name eq role.name }.single()[RoleTable.id]
            UserRoleTable.insertIgnore {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    /** Makes [userId] a member of [branchId] under [role]: the role, and an open assignment there. */
    fun member(
        userId: UUID,
        branchId: UUID,
        role: Role = Role.PRACTITIONER,
    ) {
        hold(userId, role)
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
                UserBranchAssignmentTable.insert {
                    it[UserBranchAssignmentTable.userId] = userId
                    it[UserBranchAssignmentTable.branchId] = branchId
                    it[UserBranchAssignmentTable.slot] = nextSlot(branchId)
                    it[UserBranchAssignmentTable.assignedBy] = userId
                }
            }
        }
    }

    private fun nextSlot(branchId: UUID): Short =
        UserBranchAssignmentTable
            .selectAll()
            .where { (UserBranchAssignmentTable.branchId eq branchId) and UserBranchAssignmentTable.endedAt.isNull() }
            .count()
            .toShort()

    /** Takes away every fact that gives [userId] rights: roles, assignments, relief days and helper seats. */
    fun revokeAll(userId: UUID) {
        transaction {
            UserRoleTable.deleteWhere { UserRoleTable.userId eq userId }
            UserBranchAssignmentTable.deleteWhere { UserBranchAssignmentTable.userId eq userId }
            ReliefDayTable.deleteWhere { ReliefDayTable.userId eq userId }
            MedicalMissionDelegateTable.deleteWhere { MedicalMissionDelegateTable.targetUser eq userId }
        }
    }
}
