package com.companyb.companyapp.seeding

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserCreateParams
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

private const val DEV_USER_ROLE_NAME = "OWNER"
private const val DEV_USER_EMAIL_DOMAIN = "@example.com"
private const val SCOPED_USER_SLOT: Short = 1

// #575 — the dev owner clocks in at the fixture branch (BranchSelect lists assigned branches;
// capabilities alone render an empty list with no clock-in path), so the global seed carries
// a senior home assignment there. Slot is per user per branch — no collision with the scoped seat.
private const val DEV_USER_SLOT: Short = 1

internal val DEV_FIXTURE_BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-000000000001")

private val DEV_CAPABILITIES =
    listOf(
        "VIEW_BRANCH_DATA",
        "EDIT_BRANCH_DATA",
        "EDIT_PAST_DAY",
        "VOID_SESSION",
        "SUBMIT_REMITTANCE",
        "ASSIGN_COMPENSATION",
        "MANAGE_PRODUCTS",
        "MANAGE_CATALOG",
        "MANAGE_USERS",
        "ASSIGN_DELEGATE",
    )

private val DEV_FIXTURE_BRANCH_CAPABILITIES =
    listOf(
        "VIEW_BRANCH_DATA",
        "EDIT_BRANCH_DATA",
        "EDIT_PAST_DAY",
        "VOID_SESSION",
        "SUBMIT_REMITTANCE",
        "ASSIGN_COMPENSATION",
        "MANAGE_PRODUCTS",
    )

// #413 — the relief requester seat: exactly EDIT_BRANCH_DATA at the fixture branch.
private val RELIEF_USER_CAPABILITIES =
    listOf(
        "EDIT_BRANCH_DATA",
    )

private data class Credentials(
    val username: String,
    val password: String,
)

object DevSeeder {
    fun seed(
        config: AppConfig,
        runInTransaction: (() -> Unit) -> Unit = { block -> transaction { block() } },
    ) {
        credentials(config.testUsername, config.testPassword)?.let { global ->
            runInTransaction { seedGlobalUser(global) }
        }
        credentials(config.scopedTestUsername, config.scopedTestPassword)?.let { scoped ->
            runInTransaction { seedScopedUser(scoped) }
        }
        credentials(config.reliefTestUsername, config.reliefTestPassword)?.let { relief ->
            runInTransaction { seedReliefUser(relief) }
        }
    }

    private fun credentials(
        username: String?,
        password: String?,
    ): Credentials? {
        val name = username?.takeIf { it.isNotBlank() } ?: return null
        val pass = password?.takeIf { it.isNotBlank() } ?: return null
        return Credentials(name, pass)
    }

    private fun seedGlobalUser(credentials: Credentials) {
        if (UserRepository.findByUsername(credentials.username) != null) return

        val userId = createUser(credentials)

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
            insertCapability(userId, code, CapabilityContextType.GLOBAL, CapabilityService.GLOBAL_CONTEXT_ID)
        }

        ensureFixtureBranch()
        for (code in DEV_FIXTURE_BRANCH_CAPABILITIES) {
            insertCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }
        UserBranchAssignmentTable.insert {
            it[id] = UUID.randomUUID()
            it[UserBranchAssignmentTable.userId] = userId
            it[branchId] = DEV_FIXTURE_BRANCH_ID
            it[slot] = DEV_USER_SLOT
            it[assignedBy] = userId
        }
        seedSessionBaseRates(userId)

        logger.info {
            "[DEV-SEED] Created dev user '${credentials.username}' with" +
                " $DEV_USER_ROLE_NAME role and ${DEV_CAPABILITIES.size} capabilities"
        }
    }

    private fun seedScopedUser(credentials: Credentials) {
        if (UserRepository.findByUsername(credentials.username) != null) return

        val userId = createUser(credentials)
        ensureFixtureBranch()
        for (code in DEV_FIXTURE_BRANCH_CAPABILITIES) {
            insertCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }
        UserBranchAssignmentTable.insert {
            it[id] = UUID.randomUUID()
            it[UserBranchAssignmentTable.userId] = userId
            it[branchId] = DEV_FIXTURE_BRANCH_ID
            it[slot] = SCOPED_USER_SLOT
            it[assignedBy] = userId
        }

        logger.info {
            "[DEV-SEED] Created branch-scoped dev user '${credentials.username}' with" +
                " ${DEV_FIXTURE_BRANCH_CAPABILITIES.size} capabilities at the fixture branch"
        }
    }

    /**
     * #413 — the relief requester seat: holds BRANCH-scoped EDIT_BRANCH_DATA at the
     * fixture branch but deliberately NO home assignment. requestReliefAccess excludes
     * assigned users ("relief duty does not apply") while grant/deny/invite authority
     * requires an active assignment, so no two-principal cast can reach the relief
     * flows — this user requests relief and accepts invites; the scoped principal
     * grants and revokes.
     */
    private fun seedReliefUser(credentials: Credentials) {
        if (UserRepository.findByUsername(credentials.username) != null) return

        val userId = createUser(credentials)
        ensureFixtureBranch()
        for (code in RELIEF_USER_CAPABILITIES) {
            insertCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }

        logger.info {
            "[DEV-SEED] Created branch-scoped relief dev user '${credentials.username}' with" +
                " ${RELIEF_USER_CAPABILITIES.size} capability and no home assignment"
        }
    }

    private fun ensureFixtureBranch() {
        BranchTable.insertIgnore {
            it[id] = DEV_FIXTURE_BRANCH_ID
            it[name] = "K6 Fixture Branch"
            it[branchType] = BranchType.CLINIC
        }
    }

    private fun createUser(credentials: Credentials): UUID =
        UserRepository.createUserInTransaction(
            UserCreateParams(
                username = credentials.username,
                passwordHash = Password.create(credentials.password),
                email = "${credentials.username}$DEV_USER_EMAIL_DOMAIN",
                displayName = "Dev ${credentials.username}",
            ),
        )

    private fun insertCapability(
        userId: UUID,
        code: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ) {
        val capabilityId =
            CapabilityRepository.findIdByCode(code)
                ?: error("Capability '$code' not found in database")
        UserCapabilityTable.insert {
            it[UserCapabilityTable.userId] = userId
            it[UserCapabilityTable.capabilityId] = capabilityId
            it[UserCapabilityTable.contextType] = contextType
            it[UserCapabilityTable.contextId] = contextId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
            it[UserCapabilityTable.sourceId] = contextId
        }
    }

    private fun seedSessionBaseRates(userId: UUID) {
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
    }
}
