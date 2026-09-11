package com.companyb.companyapp.seeding

import com.companyb.companyapp.authorization.CapabilityRepository
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.UserCapabilityTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserCreateParams
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.session.SessionBaseRateRepository
import com.companyb.companyapp.session.SessionBaseRateTable
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
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

// #568 — MagicNumber scope: src/dev is analyzed under production rules.
private const val SESSION_RATE_HORIZON_YEARS = 10L

// #880 — the fixture branch rate card seeded alongside any principal that ensures the
// branch, so scoped-only and relief-only seeds never leave a rate-less branch behind.
private val DEV_SESSION_BASE_RATES: Map<SessionType, BigDecimal> =
    mapOf(
        SessionType.REGULAR to BigDecimal("2500.00"),
        SessionType.PROVINCIAL_FIRST to BigDecimal("3500.00"),
        SessionType.SECOND_SESSION to BigDecimal("2000.00"),
        SessionType.SUBSEQUENT to BigDecimal("1500.00"),
    )

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
        config: DevFixtureConfig,
        runInTransaction: (() -> Unit) -> Unit = { block -> transaction { block() } },
    ) {
        credentials(config.globalUsername, config.globalPassword)?.let { global ->
            runInTransaction { seedGlobalUser(global) }
        }
        credentials(config.scopedUsername, config.scopedPassword)?.let { scoped ->
            runInTransaction { seedScopedUser(scoped) }
        }
        credentials(config.reliefUsername, config.reliefPassword)?.let { relief ->
            runInTransaction { seedReliefUser(relief) }
        }
    }

    private fun credentials(
        username: String?,
        password: String?,
    ): Credentials? {
        val name = username?.takeIf { it.isNotBlank() }
        val pass = password?.takeIf { it.isNotBlank() }
        return if (name != null && pass != null) Credentials(name, pass) else null
    }

    private fun seedGlobalUser(credentials: Credentials) {
        // #880 — reconcile-or-skip per entity: a rerun after a partial seed heals the
        // missing rows instead of early-returning on the existing user row.
        val userId = ensureUser(credentials)

        val ownerRoleId =
            RoleTable
                .selectAll()
                .where { RoleTable.name eq DEV_USER_ROLE_NAME }
                .single()[RoleTable.id]
        UserRoleTable.insertIgnore {
            it[UserRoleTable.userId] = userId
            it[UserRoleTable.roleId] = ownerRoleId
        }

        for (code in DEV_CAPABILITIES) {
            ensureCapability(userId, code, CapabilityContextType.GLOBAL, CapabilityService.GLOBAL_CONTEXT_ID)
        }

        ensureFixtureBranch()
        for (code in DEV_FIXTURE_BRANCH_CAPABILITIES) {
            ensureCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }
        ensureAssignment(userId, DEV_FIXTURE_BRANCH_ID, DEV_USER_SLOT)
        ensureSessionBaseRates(userId)

        logger.info {
            "[DEV-SEED] Ensured dev user '${credentials.username}' with" +
                " $DEV_USER_ROLE_NAME role and ${DEV_CAPABILITIES.size} capabilities"
        }
    }

    private fun seedScopedUser(credentials: Credentials) {
        // #880 — same reconcile pattern as the global path, plus base rates: a
        // scoped-only seed must leave session create working on the fixture branch.
        val userId = ensureUser(credentials)
        ensureFixtureBranch()
        for (code in DEV_FIXTURE_BRANCH_CAPABILITIES) {
            ensureCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }
        ensureAssignment(userId, DEV_FIXTURE_BRANCH_ID, SCOPED_USER_SLOT)
        ensureSessionBaseRates(userId)

        logger.info {
            "[DEV-SEED] Ensured branch-scoped dev user '${credentials.username}' with" +
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
        // #880 — reconcile-or-skip like the other paths (still deliberately no home
        // assignment), plus base rates since this path also ensures the branch.
        val userId = ensureUser(credentials)
        ensureFixtureBranch()
        for (code in RELIEF_USER_CAPABILITIES) {
            ensureCapability(userId, code, CapabilityContextType.BRANCH, DEV_FIXTURE_BRANCH_ID)
        }
        ensureSessionBaseRates(userId)

        logger.info {
            "[DEV-SEED] Ensured branch-scoped relief dev user '${credentials.username}' with" +
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

    // #880 — an existing user row proves nothing about the rest of the principal
    // shape, so reuse it and let each ensure step below fill its own gap.
    private fun ensureUser(credentials: Credentials): UUID =
        UserRepository.findByUsername(credentials.username)?.let { UUID.fromString(it.id) }
            ?: createUser(credentials)

    // #880 — fills gaps only: a missing grant row is inserted, but an explicitly
    // revoked grant is never resurrected and existing rows are never duplicated
    // (user_capability carries no uniqueness guard, so the check precedes the insert).
    private fun ensureCapability(
        userId: UUID,
        code: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ) {
        val capabilityId =
            CapabilityRepository.findIdByCode(code)
                ?: error("Capability '$code' not found in database")
        val exists =
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.userId eq userId) and
                        (UserCapabilityTable.capabilityId eq capabilityId) and
                        (UserCapabilityTable.contextType eq contextType) and
                        (UserCapabilityTable.contextId eq contextId)
                }.empty()
                .not()
        if (exists) return
        UserCapabilityTable.insert {
            it[UserCapabilityTable.userId] = userId
            it[UserCapabilityTable.capabilityId] = capabilityId
            it[UserCapabilityTable.contextType] = contextType
            it[UserCapabilityTable.contextId] = contextId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
            it[UserCapabilityTable.sourceId] = contextId
        }
    }

    // #880 — one active assignment per user per branch (partial unique index): an
    // ended assignment stays history, a missing one is recreated via insertIgnore.
    private fun ensureAssignment(
        userId: UUID,
        branchId: UUID,
        slot: Short,
    ) {
        val exists =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.branchId eq branchId) and
                        UserBranchAssignmentTable.endedAt.isNull()
                }.empty()
                .not()
        if (exists) return
        UserBranchAssignmentTable.insertIgnore {
            it[UserBranchAssignmentTable.id] = UUID.randomUUID()
            it[UserBranchAssignmentTable.userId] = userId
            it[UserBranchAssignmentTable.branchId] = branchId
            it[UserBranchAssignmentTable.slot] = slot
            it[UserBranchAssignmentTable.assignedBy] = userId
            // insertIgnore never emits the now() default — set the DB clock explicitly.
            it[UserBranchAssignmentTable.assignedAt] = CurrentTimestampWithTimeZone
        }
    }

    // #880 — session create 400s without an active rate per type, so every path that
    // ensures the fixture branch tops up only the uncovered types (the overlap
    // exclusion forbids blind re-insertion on rerun).
    private fun ensureSessionBaseRates(userId: UUID) {
        val coveredTypes =
            SessionBaseRateRepository
                .findActiveByBranchInTransaction(DEV_FIXTURE_BRANCH_ID)
                .map { it.sessionType }
                .toSet()
        val missing = DEV_SESSION_BASE_RATES.filterKeys { it !in coveredTypes }
        if (missing.isEmpty()) return
        val effectiveFrom: OffsetDateTime =
            RoleTable.select(CurrentTimestampWithTimeZone).first()[CurrentTimestampWithTimeZone]
        val effectiveUntil = effectiveFrom.plusYears(SESSION_RATE_HORIZON_YEARS)
        for ((sessionType, rate) in missing) {
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.setBy] = userId
                it[SessionBaseRateTable.branchId] = DEV_FIXTURE_BRANCH_ID
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = rate
                it[SessionBaseRateTable.effectiveFrom] = effectiveFrom
                it[SessionBaseRateTable.effectiveUntil] = effectiveUntil
            }
        }
    }
}
