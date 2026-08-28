package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
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

/**
 * #417: staff-role branch-scoped provisioning. Leg (c) of the
 * active_user_capabilities union derives each assigned role's branch-scoped
 * bundle at every branch holding an ACTIVE assignment (management codes
 * MANAGE_USERS/ASSIGN_DELEGATE never derive BRANCH-scoped). Covers every
 * seeded role's ACTIVE-assignment bundle plus the assignment state machine
 * (ended → revoked, reassignment → restored) on COORDINATOR, explicit-grant
 * precedence, INACTIVE exclusion, and multi-role/multi-branch unions.
 * Route-level reachability of the derived tuples is proven separately in
 * StaffRoleReachabilityAuthzTest.
 */
class StaffRoleBranchDerivationPostgresTest : BasePostgresTest() {
    private val coordinatorUser = TestFixtures.uuid()
    private val practitionerUser = TestFixtures.uuid()
    private val ownerUser = TestFixtures.uuid()
    private val accountantUser = TestFixtures.uuid()
    private val managerUser = TestFixtures.uuid()
    private val superuserUser = TestFixtures.uuid()
    private val multiRoleUser = TestFixtures.uuid()
    private val onboardingUser = TestFixtures.uuid()
    private val inactiveCoordinator = TestFixtures.uuid()
    private val endedCoordinator = TestFixtures.uuid()
    private val directGrantCoordinator = TestFixtures.uuid()
    private val multiBranchCoordinator = TestFixtures.uuid()

    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val unrelatedBranch = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        insertUsers()
        insertBranches()
        assignRolesAndAssignments()

        DatabaseTestHelper.grantCapability(
            userId = directGrantCoordinator,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, directGrantCoordinator)
    }

    private fun insertUsers() {
        listOf(
            coordinatorUser to "coord",
            practitionerUser to "pract",
            ownerUser to "owner",
            accountantUser to "acct",
            managerUser to "mgr",
            superuserUser to "super",
            multiRoleUser to "multirole",
            onboardingUser to "onbrd",
            endedCoordinator to "ended-coord",
            directGrantCoordinator to "direct-coord",
            multiBranchCoordinator to "multi-coord",
        ).forEach { (id, prefix) ->
            DatabaseTestHelper.insertTestUser(id, prefix)
            trackOwned(AppUserTable, AppUserTable.id, id)
        }
        DatabaseTestHelper.insertUser(
            id = inactiveCoordinator,
            username = "inactive-${inactiveCoordinator.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "inactive-${inactiveCoordinator.toString().take(8)}@t.st",
            displayName = "Test Inactive Coordinator",
            status = UserStatus.INACTIVE,
        )
        trackOwned(AppUserTable, AppUserTable.id, inactiveCoordinator)
    }

    private fun insertBranches() {
        listOf(
            branchA to "Derivation Branch A",
            branchB to "Derivation Branch B",
            unrelatedBranch to "Unrelated Branch",
        ).forEach { (id, name) ->
            DatabaseTestHelper.insertTestBranch(id, name)
            trackOwned(BranchTable, BranchTable.id, id)
        }
    }

    private fun assignRolesAndAssignments() {
        assignRole(coordinatorUser, "COORDINATOR")
        assignRole(practitionerUser, "PRACTITIONER")
        assignRole(ownerUser, "OWNER")
        assignRole(accountantUser, "ACCOUNTANT")
        assignRole(managerUser, "MANAGER")
        assignRole(superuserUser, "SUPERUSER")
        assignRole(multiRoleUser, "COORDINATOR")
        assignRole(multiRoleUser, "PRACTITIONER")
        assignRole(onboardingUser, "ONBOARDING")
        assignRole(inactiveCoordinator, "COORDINATOR")
        assignRole(endedCoordinator, "COORDINATOR")
        assignRole(directGrantCoordinator, "COORDINATOR")
        assignRole(multiBranchCoordinator, "COORDINATOR")

        insertAssignment(coordinatorUser, branchA)
        insertAssignment(practitionerUser, branchA)
        insertAssignment(ownerUser, branchA)
        insertAssignment(accountantUser, branchA)
        insertAssignment(managerUser, branchA)
        insertAssignment(superuserUser, branchA)
        insertAssignment(multiRoleUser, branchA)
        insertAssignment(onboardingUser, branchA)
        insertAssignment(inactiveCoordinator, branchA)
        insertAssignment(endedCoordinator, branchA, ended = true)
        insertAssignment(directGrantCoordinator, branchA)
        insertAssignment(multiBranchCoordinator, branchA)
        insertAssignment(multiBranchCoordinator, branchB)
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
        trackOwned(UserRoleTable, UserRoleTable.userId, userId)
    }

    private fun insertAssignment(
        userId: UUID,
        branchId: UUID,
        ended: Boolean = false,
        slot: Short = 1,
    ) {
        val id =
            DatabaseTestHelper.insertTestAssignment(
                userId = userId,
                branchId = branchId,
                slot = slot,
                assignedBy = userId,
                ended = ended,
            )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, id)
    }

    private fun hasBranch(
        userId: UUID,
        code: String,
        branchId: UUID = branchA,
    ): Boolean =
        CapabilityService.hasCapability(
            userId = userId,
            capabilityCode = code,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
        )

    private fun derivedCodesAtBranch(userId: UUID): Set<String> =
        CapabilityService
            .getCapabilitiesForUser(userId)
            .filter { it.contextType == CapabilityContextType.BRANCH && it.sourceType == CapabilitySourceType.ROLE }
            .map { it.capabilityCode }
            .toSet()

    // ── Derivation matrix: role × ACTIVE assignment ────────────────────────

    @Test
    fun `COORDINATOR assignment derives full coordinator bundle at the branch`() {
        assertEquals(
            setOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS,
            ),
            derivedCodesAtBranch(coordinatorUser),
            "COORDINATOR bundle must derive BRANCH-scoped from the ACTIVE assignment alone",
        )
    }

    @Test
    fun `PRACTITIONER assignment derives read and edit at the branch only`() {
        assertEquals(
            setOf(CapabilityCodes.VIEW_BRANCH_DATA, CapabilityCodes.EDIT_BRANCH_DATA),
            derivedCodesAtBranch(practitionerUser),
        )
    }

    @Test
    fun `OWNER assignment derives home-branch operational codes and GLOBAL read`() {
        assertTrue(hasBranch(ownerUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertTrue(hasBranch(ownerUser, CapabilityCodes.EDIT_BRANCH_DATA))
        assertTrue(hasBranch(ownerUser, CapabilityCodes.ASSIGN_COMPENSATION))
        assertTrue(hasBranch(ownerUser, CapabilityCodes.MANAGE_PRODUCTS))
        assertFalse(hasBranch(ownerUser, CapabilityCodes.SUBMIT_REMITTANCE), "not in the OWNER bundle")
        assertFalse(hasBranch(ownerUser, CapabilityCodes.MANAGE_USERS), "management codes never derive BRANCH-scoped")

        assertTrue(
            CapabilityService.hasCapability(
                ownerUser,
                CapabilityCodes.MANAGE_USERS,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            ),
            "GLOBAL management derivation must be unchanged",
        )
        assertTrue(
            CapabilityService.hasCapability(
                ownerUser,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            ),
            "OWNER must derive GLOBAL VIEW_BRANCH_DATA for all-branches read",
        )
    }

    @Test
    fun `ACCOUNTANT keeps GLOBAL read and gains no write codes`() {
        assertTrue(
            CapabilityService.hasCapability(
                accountantUser,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            ),
        )
        assertTrue(hasBranch(accountantUser, CapabilityCodes.VIEW_BRANCH_DATA))
        assertFalse(hasBranch(accountantUser, CapabilityCodes.EDIT_BRANCH_DATA))
    }

    @Test
    fun `MANAGER assignment derives the coordinator superset without the alerts code`() {
        assertEquals(
            setOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
            ),
            derivedCodesAtBranch(managerUser),
            "MANAGER is a COORDINATOR superset per V2 but holds no RECEIVE_NEXT_APPOINTMENT_ALERTS (V5)",
        )
        assertTrue(
            CapabilityService.hasCapability(
                managerUser,
                CapabilityCodes.MANAGE_USERS,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            ),
            "MANAGER keeps the leg-(b) GLOBAL management derivation",
        )
        assertFalse(hasBranch(managerUser, CapabilityCodes.MANAGE_USERS), "management codes never derive BRANCH-scoped")
    }

    @Test
    fun `SUPERUSER assignment derives its full non-management bundle at the branch`() {
        assertEquals(
            setOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
            ),
            derivedCodesAtBranch(superuserUser),
            "SUPERUSER holds every V2-era code; RECEIVE_NEXT_APPOINTMENT_ALERTS stays Coordinator-only (V5) and " +
                "MANAGE_USERS/ASSIGN_DELEGATE are excluded from leg (c)",
        )
    }

    @Test
    fun `multiple roles on one user union into one row per tuple`() {
        val codes = derivedCodesAtBranch(multiRoleUser)
        assertEquals(
            setOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                // Coordinator-only additions over the PRACTITIONER pair:
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS,
            ),
            codes,
            "COORDINATOR ∪ PRACTITIONER bundles derive from one assignment with no duplicate rows",
        )
        val viewRows =
            CapabilityService
                .getCapabilitiesForUser(multiRoleUser)
                .filter {
                    it.capabilityCode == CapabilityCodes.VIEW_BRANCH_DATA &&
                        it.contextType == CapabilityContextType.BRANCH &&
                        it.sourceType == CapabilitySourceType.ROLE
                }
        assertEquals(1, viewRows.size, "DISTINCT ON must collapse the shared code to a single derived row")
    }

    @Test
    fun `ONBOARDING assignment still derives nothing`() {
        assertTrue(CapabilityService.getCapabilitiesForUser(onboardingUser).isEmpty())
    }

    // ── Assignment-state windows ────────────────────────────────────────────

    @Test
    fun `ended assignment revokes and reassignment restores`() {
        assertFalse(
            hasBranch(endedCoordinator, CapabilityCodes.VIEW_BRANCH_DATA),
            "ended assignment must revoke branch access",
        )

        insertAssignment(endedCoordinator, branchA, slot = 2)
        assertTrue(
            hasBranch(endedCoordinator, CapabilityCodes.VIEW_BRANCH_DATA),
            "fresh ACTIVE assignment must restore branch access",
        )
    }

    @Test
    fun `INACTIVE user is excluded even with an open assignment`() {
        assertFalse(hasBranch(inactiveCoordinator, CapabilityCodes.VIEW_BRANCH_DATA))
        assertTrue(CapabilityService.getCapabilitiesForUser(inactiveCoordinator).isEmpty())
    }

    @Test
    fun `assignment grants nothing at unrelated branches`() {
        assertFalse(hasBranch(coordinatorUser, CapabilityCodes.EDIT_BRANCH_DATA, unrelatedBranch))
        assertTrue(
            hasBranch(multiBranchCoordinator, CapabilityCodes.EDIT_BRANCH_DATA, branchB),
            "second assignment covers its own branch",
        )
        assertFalse(hasBranch(multiBranchCoordinator, CapabilityCodes.EDIT_BRANCH_DATA, unrelatedBranch))
    }

    // ── Precedence ─────────────────────────────────────────────────────────

    @Test
    fun `direct grant outranks the role-derived row on the same tuple`() {
        val editRows =
            CapabilityService
                .getCapabilitiesForUser(directGrantCoordinator)
                .filter {
                    it.capabilityCode == CapabilityCodes.EDIT_BRANCH_DATA &&
                        it.contextType == CapabilityContextType.BRANCH
                }
        assertEquals(1, editRows.size, "view must dedup direct + derived rows on one tuple")
        assertEquals(
            CapabilitySourceType.SYSTEM,
            editRows.single().sourceType,
            "direct grant (priority 100) must win over derived (5)",
        )
    }
}
