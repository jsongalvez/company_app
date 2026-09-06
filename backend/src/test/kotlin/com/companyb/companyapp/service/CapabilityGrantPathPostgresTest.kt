package com.companyb.companyapp.service
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * #132: role→capability view derivation (V15+V16). Verifies the
 * active_user_capabilities union at the service layer:
 * role-derived GLOBAL grants, the derivation whitelist, INACTIVE exclusion,
 * direct-grant regression, dedup between direct and derived rows.
 */
class CapabilityGrantPathPostgresTest : BasePostgresTest() {
    private val ownerUser = TestFixtures.uuid()
    private val superuserUser = TestFixtures.uuid()
    private val accountantUser = TestFixtures.uuid()
    private val coordinatorUser = TestFixtures.uuid()
    private val managerUser = TestFixtures.uuid()
    private val inactiveOwnerUser = TestFixtures.uuid()
    private val noRoleUser = TestFixtures.uuid()
    private val directUser = TestFixtures.uuid()
    private val dedupUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        listOf(
            ownerUser to "owner",
            superuserUser to "superuser",
            accountantUser to "accountant",
            coordinatorUser to "coordinator",
            managerUser to "manager",
            noRoleUser to "no-role",
            directUser to "direct",
            dedupUser to "dedup",
        ).forEach { (id, prefix) ->
            DatabaseTestHelper.insertTestUser(id, prefix)
        }
        DatabaseTestHelper.insertUser(
            id = inactiveOwnerUser,
            username = "inactive-owner-${inactiveOwnerUser.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "inactive-owner-${inactiveOwnerUser.toString().take(8)}@t.st",
            displayName = "Test Inactive Owner",
            status = UserStatus.INACTIVE,
        )

        DatabaseTestHelper.insertTestBranch(branchId, "Grant Path Branch")

        assignRole(ownerUser, "OWNER")
        assignRole(superuserUser, "SUPERUSER")
        assignRole(accountantUser, "ACCOUNTANT")
        assignRole(coordinatorUser, "COORDINATOR")
        assignRole(managerUser, "MANAGER")
        assignRole(inactiveOwnerUser, "OWNER")
        assignRole(dedupUser, "OWNER")

        DatabaseTestHelper.grantCapability(
            userId = directUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = dedupUser,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId =
            transaction {
                RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    private fun has(
        userId: UUID,
        code: String,
    ): Boolean =
        CapabilityService.hasCapability(
            userId = userId,
            capabilityCode = code,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

    // ── Role-derived GLOBAL grants ─────────────────────────────────────────

    @Test
    fun `OWNER role derives the management GLOBAL bundle plus all-branches read`() {
        assertTrue(has(ownerUser, CapabilityCodes.MANAGE_USERS), "OWNER should derive MANAGE_USERS GLOBAL")
        assertTrue(has(ownerUser, CapabilityCodes.ASSIGN_DELEGATE), "OWNER should derive ASSIGN_DELEGATE GLOBAL")
        assertTrue(
            has(ownerUser, CapabilityCodes.ASSIGN_COMPENSATION),
            "OWNER should derive ASSIGN_COMPENSATION GLOBAL",
        )
        assertTrue(
            has(ownerUser, CapabilityCodes.VIEW_BRANCH_DATA),
            "OWNER should derive GLOBAL VIEW_BRANCH_DATA for all-branches read",
        )
        assertTrue(
            has(ownerUser, CapabilityCodes.MANAGE_CATALOG),
            "OWNER should derive GLOBAL MANAGE_CATALOG for catalog management (#436)",
        )
    }

    @Test
    fun `OWNER role does not derive global write grants`() {
        assertFalse(has(ownerUser, CapabilityCodes.EDIT_BRANCH_DATA), "OWNER must not derive GLOBAL EDIT_BRANCH_DATA")
        assertFalse(has(ownerUser, CapabilityCodes.MANAGE_PRODUCTS), "OWNER must not derive GLOBAL MANAGE_PRODUCTS")
        assertFalse(has(ownerUser, CapabilityCodes.SUBMIT_REMITTANCE), "OWNER must not derive GLOBAL SUBMIT_REMITTANCE")
    }

    @Test
    fun `SUPERUSER role derives the management bundle plus all-branches read`() {
        assertTrue(has(superuserUser, CapabilityCodes.MANAGE_USERS))
        assertTrue(has(superuserUser, CapabilityCodes.ASSIGN_DELEGATE))
        assertTrue(has(superuserUser, CapabilityCodes.ASSIGN_COMPENSATION))
        assertTrue(
            has(superuserUser, CapabilityCodes.VIEW_BRANCH_DATA),
            "SUPERUSER full access includes GLOBAL VIEW_BRANCH_DATA",
        )
        assertTrue(
            has(superuserUser, CapabilityCodes.MANAGE_CATALOG),
            "SUPERUSER full access includes GLOBAL MANAGE_CATALOG (#436)",
        )
    }

    @Test
    fun `ACCOUNTANT role derives all-branches read only`() {
        assertTrue(
            has(accountantUser, CapabilityCodes.VIEW_BRANCH_DATA),
            "ACCOUNTANT all-branches read intent (#105 F1)",
        )
        assertFalse(has(accountantUser, CapabilityCodes.MANAGE_USERS))
        assertFalse(has(accountantUser, CapabilityCodes.MANAGE_CATALOG))
        assertFalse(has(accountantUser, CapabilityCodes.ASSIGN_COMPENSATION))
        assertFalse(has(accountantUser, CapabilityCodes.EDIT_BRANCH_DATA))
    }

    @Test
    fun `COORDINATOR role derives only catalog GLOBAL`() {
        assertTrue(
            has(coordinatorUser, CapabilityCodes.MANAGE_CATALOG),
            "COORDINATOR should derive GLOBAL MANAGE_CATALOG for catalog management (#436)",
        )
        assertFalse(
            has(coordinatorUser, CapabilityCodes.ASSIGN_COMPENSATION),
            "COORDINATOR ASSIGN_COMPENSATION is branch-scoped (V2) — must not over-grant",
        )
        assertFalse(has(coordinatorUser, CapabilityCodes.MANAGE_USERS))
        assertFalse(has(coordinatorUser, CapabilityCodes.VIEW_BRANCH_DATA))
    }

    @Test
    fun `MANAGER role derives catalog GLOBAL as Coordinator superset`() {
        assertTrue(
            has(managerUser, CapabilityCodes.MANAGE_CATALOG),
            "MANAGER should derive GLOBAL MANAGE_CATALOG like Coordinators (#436)",
        )
    }

    // ── Exclusion + regression ─────────────────────────────────────────────

    @Test
    fun `INACTIVE user gets no derived grants`() {
        assertFalse(
            has(inactiveOwnerUser, CapabilityCodes.MANAGE_USERS),
            "INACTIVE users must be excluded from derived rows",
        )
        assertFalse(has(inactiveOwnerUser, CapabilityCodes.ASSIGN_DELEGATE))
        assertFalse(has(inactiveOwnerUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertTrue(
            CapabilityService.getCapabilitiesForUser(inactiveOwnerUser).isEmpty(),
            "INACTIVE user must have zero capabilities",
        )
    }

    @Test
    fun `zero-role user gets nothing`() {
        assertFalse(has(noRoleUser, CapabilityCodes.MANAGE_USERS))
        assertTrue(CapabilityService.getCapabilitiesForUser(noRoleUser).isEmpty())
    }

    @Test
    fun `direct user_capability rows still work`() {
        assertTrue(
            CapabilityService.hasCapability(
                userId = directUser,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH,
                contextId = branchId,
            ),
            "direct BRANCH grants must be unaffected by the union",
        )
        assertFalse(has(directUser, CapabilityCodes.MANAGE_USERS))
    }

    // ── Dedup between direct and derived rows ──────────────────────────────

    @Test
    fun `direct grant beats role-derived row on the same tuple`() {
        assertTrue(has(dedupUser, CapabilityCodes.MANAGE_USERS))
        val manageUsers =
            CapabilityService
                .getCapabilitiesForUser(dedupUser)
                .filter { it.capabilityCode == CapabilityCodes.MANAGE_USERS }
        assertEquals(1, manageUsers.size, "view must dedup direct + derived rows")
        assertEquals(
            "SYSTEM",
            manageUsers.single().sourceType.name,
            "direct grant (priority 100) must win over derived (5)",
        )
    }

    @Test
    fun `derived rows are reported with ROLE source and GLOBAL context`() {
        val caps = CapabilityService.getCapabilitiesForUser(ownerUser)
        val derived = caps.filter { it.sourceType == com.companyb.companyapp.domain.CapabilitySourceType.ROLE }
        assertEquals(
            5,
            derived.size,
            "OWNER derives management GLOBAL capabilities plus catalog plus all-branches VIEW_BRANCH_DATA",
        )
        derived.forEach {
            assertEquals(com.companyb.companyapp.domain.CapabilityContextType.GLOBAL, it.contextType)
            assertEquals(CapabilityService.GLOBAL_CONTEXT_ID.toString(), it.contextId)
        }
        assertEquals(
            setOf(
                CapabilityCodes.MANAGE_USERS,
                CapabilityCodes.ASSIGN_DELEGATE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_CATALOG,
                CapabilityCodes.VIEW_BRANCH_DATA,
            ),
            derived.map { it.capabilityCode }.toSet(),
        )
    }

    @Test
    fun `any-context and branch-window helpers respect the union`() {
        assertTrue(CapabilityService.hasCapabilityAnyContext(ownerUser, CapabilityCodes.MANAGE_USERS))
        assertTrue(CapabilityService.hasCapabilityAnyContext(ownerUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertTrue(CapabilityService.hasCapabilityAnyContext(accountantUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertFalse(CapabilityService.hasCapabilityAnyContext(noRoleUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertTrue(
            CapabilityService.findBranchWindow(ownerUser).isEmpty(),
            "derived GLOBAL grants contribute nothing to the BRANCH read window",
        )
    }

    @Test
    fun `hasCapability stays fast with the union view`() {
        val (_, duration) =
            measureTimedValue {
                has(ownerUser, CapabilityCodes.MANAGE_USERS)
            }
        assertTrue(
            duration < 500.milliseconds,
            "Performance regression: hasCapability (union view) took $duration, expected < 500ms",
        )
    }
}
