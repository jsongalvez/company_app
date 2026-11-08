package com.companyb.companyapp.seeding

import com.companyb.companyapp.branch.BranchTable
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

private const val DEV_SUPERUSER_USERNAME = "dev"
private const val DEV_SUPERUSER_PASSWORD = "dev-dev-pass"
private const val DEV_USER_EMAIL_DOMAIN = "@example.com"
private const val SCOPED_USER_SLOT: Short = 1

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
            runInTransaction {
                seedGlobalUser(global)
                seedDeveloper()
            }
        }
        credentials(config.scopedUsername, config.scopedPassword)?.let { scoped ->
            runInTransaction { seedScopedUser(scoped) }
        }
        credentials(config.reliefUsername, config.reliefPassword)?.let { relief ->
            runInTransaction { seedReliefUser(relief) }
        }
    }

    /** Local-only developer account (SUPERUSER) for trying "View as". */
    private fun seedDeveloper() {
        ensureRole(ensureUser(Credentials(DEV_SUPERUSER_USERNAME, DEV_SUPERUSER_PASSWORD)), "SUPERUSER")
        logger.info { "[DEV-SEED] Ensured developer '$DEV_SUPERUSER_USERNAME' with SUPERUSER role" }
    }

    private fun credentials(
        username: String?,
        password: String?,
    ): Credentials? {
        val name = username?.takeIf { it.isNotBlank() }
        val pass = password?.takeIf { it.isNotBlank() }
        return if (name != null && pass != null) Credentials(name, pass) else null
    }

    /** The dev owner: an Owner, and a Coordinator member of the fixture branch, so they also send its remittances. */
    private fun seedGlobalUser(credentials: Credentials) {
        val userId = ensureUser(credentials)
        ensureRole(userId, "OWNER")
        ensureRole(userId, "COORDINATOR")
        ensureFixtureBranch()
        ensureAssignment(userId, DEV_FIXTURE_BRANCH_ID, DEV_USER_SLOT)
        ensureSessionBaseRates(userId)
        logger.info { "[DEV-SEED] Ensured dev user '${credentials.username}' as Owner and fixture-branch Coordinator" }
    }

    /** The dev staff: a Coordinator member of the fixture branch, who grants and revokes its relief. */
    private fun seedScopedUser(credentials: Credentials) {
        val userId = ensureUser(credentials)
        ensureRole(userId, "COORDINATOR")
        ensureFixtureBranch()
        ensureAssignment(userId, DEV_FIXTURE_BRANCH_ID, SCOPED_USER_SLOT)
        ensureSessionBaseRates(userId)
        logger.info { "[DEV-SEED] Ensured dev staff '${credentials.username}' as fixture-branch Coordinator" }
    }

    /**
     * The relief worker: a Practitioner with no home assignment. Someone assigned to a branch cannot
     * request relief there, and the fixture branch's invite is addressed to this person.
     */
    private fun seedReliefUser(credentials: Credentials) {
        val userId = ensureUser(credentials)
        ensureRole(userId, "PRACTITIONER")
        ensureFixtureBranch()
        ensureSessionBaseRates(userId)
        logger.info { "[DEV-SEED] Ensured relief worker '${credentials.username}' as a Practitioner with no branch" }
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

    private fun ensureRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId = RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
        UserRoleTable.insertIgnore {
            it[UserRoleTable.userId] = userId
            it[UserRoleTable.roleId] = roleId
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
