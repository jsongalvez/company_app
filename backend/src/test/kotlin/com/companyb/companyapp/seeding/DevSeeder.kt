package com.companyb.companyapp.seeding

import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.UserCreateParams
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.CapabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

private const val DEV_USER_ROLE_NAME = "OWNER"
private const val DEV_USER_EMAIL_DOMAIN = "@example.com"
private val DEV_FIXTURE_BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-000000000001")

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
    @Suppress("LongMethod", "ReturnCount")
    fun seed(
        config: AppConfig,
        runInTransaction: (() -> Unit) -> Unit = { block -> transaction { block() } },
    ) {
        val username = config.testUsername?.takeIf { it.isNotBlank() } ?: return
        val password = config.testPassword?.takeIf { it.isNotBlank() } ?: return

        runInTransaction {
            if (UserRepository.findByUsername(username) != null) return@runInTransaction

            val passwordHash = Password.create(password)
            val userId =
                UserRepository.createUser(
                    UserCreateParams(
                        username = username,
                        passwordHash = passwordHash,
                        email = "$username$DEV_USER_EMAIL_DOMAIN",
                        displayName = "Dev $username",
                    ),
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

            BranchTable.insert {
                it[id] = DEV_FIXTURE_BRANCH_ID
                it[name] = "K6 Fixture Branch"
                it[branchType] = BranchType.CLINIC
            }
            for (code in listOf(
                "VIEW_BRANCH_DATA",
                "EDIT_BRANCH_DATA",
                "EDIT_PAST_DAY",
                "VOID_SESSION",
                "SUBMIT_REMITTANCE",
                "ASSIGN_COMPENSATION",
                "MANAGE_PRODUCTS",
            )) {
                val capabilityId = CapabilityRepository.findIdByCode(code) ?: error("Capability '$code' not found")
                UserCapabilityTable.insert {
                    it[UserCapabilityTable.userId] = userId
                    it[UserCapabilityTable.capabilityId] = capabilityId
                    it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH
                    it[UserCapabilityTable.contextId] = DEV_FIXTURE_BRANCH_ID
                    it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                    it[UserCapabilityTable.sourceId] = DEV_FIXTURE_BRANCH_ID
                }
            }
            val effectiveFrom: OffsetDateTime =
                RoleTable.select(CurrentTimestampWithTimeZone).first()[CurrentTimestampWithTimeZone]
            val effectiveUntil = effectiveFrom.plusYears(10)
            for ((sessionType, rate) in listOf(
                SessionType.REGULAR to "2500.00",
                SessionType.PROVINCIAL_FIRST to "3500.00",
                SessionType.SECOND_SESSION to "2000.00",
                SessionType.SUBSEQUENT to "1500.00",
            )) {
                SessionBaseRateTable.insert {
                    it[SessionBaseRateTable.setBy] = userId
                    it[SessionBaseRateTable.branchId] = DEV_FIXTURE_BRANCH_ID
                    it[SessionBaseRateTable.sessionType] = sessionType
                    it[SessionBaseRateTable.rate] = BigDecimal(rate)
                    it[SessionBaseRateTable.effectiveFrom] = effectiveFrom
                    it[SessionBaseRateTable.effectiveUntil] = effectiveUntil
                }
            }

            logger.info {
                "[DEV-SEED] Created dev user '$username' with" +
                    " $DEV_USER_ROLE_NAME role and ${DEV_CAPABILITIES.size} capabilities"
            }
        }
    }
}
