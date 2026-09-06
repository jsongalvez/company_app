package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Identity and capability fixtures for map #533 (#552).
 *
 * Owns user rows and capability grants only. Branch, session, client and
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

    fun grantManageUsers(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantEditBranchData(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantManageProducts(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantVoidSession(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VOID_SESSION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantAssignCompensation(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantAssignDelegate(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.ASSIGN_DELEGATE,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantSubmitRemittance(
        userId: UUID,
        sourceId: UUID,
        branchId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun grantCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
        sourceId: UUID,
        priority: Int = GrantPriorities.DIRECT_GRANT.toInt(),
        validFrom: OffsetDateTime? = null,
        validTo: OffsetDateTime? = null,
    ) {
        val capId =
            CapabilityRepository.findIdByCode(capabilityCode)
                ?: error("Capability code not found: $capabilityCode")
        transaction {
            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = contextType
                it[UserCapabilityTable.contextId] = contextId
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = priority.toShort()
                if (validFrom != null) it[UserCapabilityTable.validFrom] = validFrom
                if (validTo != null) it[UserCapabilityTable.validTo] = validTo
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

    fun revokeAllCapabilities(userId: UUID) {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
        }
    }
}
