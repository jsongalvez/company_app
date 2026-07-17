package com.companyb.companyapp.seeding

import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.CapabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = KotlinLogging.logger {}

private const val DEV_USER_ROLE_NAME = "OWNER"
private const val DEV_USER_EMAIL_DOMAIN = "@example.com"

private val DEV_CAPABILITIES =
    listOf(
        "VIEW_BRANCH_DATA",
        "EDIT_BRANCH_DATA",
        "EDIT_PAST_DAY",
        "VOID_SESSION",
        "SUBMIT_REMITTANCE",
        "ASSIGN_COMPENSATION",
        "MANAGE_PRODUCTS",
        "MANAGE_USERS",
        "ASSIGN_DELEGATE",
    )

object DevSeeder {
    @Suppress("ReturnCount")
    fun seed(
        config: AppConfig,
        runInTransaction: (() -> Unit) -> Unit = { block -> transaction { block() } },
    ) {
        if (!config.seedDevUser) return

        val username = config.testUsername?.takeIf { it.isNotBlank() } ?: return
        val password = config.testPassword?.takeIf { it.isNotBlank() } ?: return

        runInTransaction {
            if (UserRepository.findByUsername(username) != null) return@runInTransaction

            val passwordHash = Password.create(password)
            val userId =
                UserRepository.createUser(
                    username,
                    passwordHash,
                    "$username$DEV_USER_EMAIL_DOMAIN",
                    "Dev $username",
                )

            val ownerRoleId =
                RoleTable
                    .selectAll()
                    .where { RoleTable.name eq DEV_USER_ROLE_NAME }
                    .single()[RoleTable.id]
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = ownerRoleId
            }

            for (code in DEV_CAPABILITIES) {
                val capabilityId =
                    CapabilityRepository.findIdByCode(code)
                        ?: error("Capability '$code' not found in database")
                UserCapabilityTable.insert {
                    it[UserCapabilityTable.userId] = userId
                    it[UserCapabilityTable.capabilityId] = capabilityId
                    it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                    it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                    it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                    it[UserCapabilityTable.sourceId] = CapabilityService.GLOBAL_CONTEXT_ID
                }
            }

            logger.info {
                "[DEV-SEED] Created dev user '$username' with" +
                    " $DEV_USER_ROLE_NAME role and ${DEV_CAPABILITIES.size} capabilities"
            }
        }
    }
}
