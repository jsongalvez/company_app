package com.companyb.companyapp.test

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.service.CapabilityService
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object DatabaseTestHelper {
    private var databaseReady = false

    fun ensureDatabase() {
        if (!databaseReady) {
            DatabaseConfig.runMigrations()
            DatabaseConfig.runExposed()
            databaseReady = true
        }
    }

    fun isDatabaseReady(): Boolean = databaseReady

    @Suppress("LongParameterList")
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
        transaction {
            val capId =
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq "MANAGE_USERS" }
                    .single()[CapabilityTable.id]

            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = 100
            }
        }
    }

    fun grantEditBranchData(
        userId: UUID,
        sourceId: UUID,
    ) {
        transaction {
            val capId =
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq "EDIT_BRANCH_DATA" }
                    .single()[CapabilityTable.id]

            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = 100
            }
        }
    }

    fun grantManageProducts(
        userId: UUID,
        sourceId: UUID,
    ) {
        transaction {
            val capId =
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq "MANAGE_PRODUCTS" }
                    .single()[CapabilityTable.id]

            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = 100
            }
        }
    }

    fun grantAssignDelegate(
        userId: UUID,
        sourceId: UUID,
    ) {
        transaction {
            val capId =
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq "ASSIGN_DELEGATE" }
                    .single()[CapabilityTable.id]

            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = 100
            }
        }
    }
}
