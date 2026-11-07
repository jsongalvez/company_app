package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.authorization.CapabilityRepository
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.GrantPriorities
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Identity fixtures.
 *
 * Owns user rows and roles only; access goes through [AccessFixtures]. Branch, session, client and
 * commerce/finance rows live in their own fixture families.
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
}
