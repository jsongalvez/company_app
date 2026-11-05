package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every Owner and Manager works each medical mission and provincial tour as staff, with no
 * assignment: the same rights there as a member's at their home branch. Everyone else works one
 * only as a helper.
 */
class MissionStaffPostgresTest : BasePostgresTest() {
    private val owner = TestFixtures.uuid()
    private val manager = TestFixtures.uuid()
    private val coordinator = TestFixtures.uuid()
    private val practitioner = TestFixtures.uuid()
    private val inactiveOwner = TestFixtures.uuid()
    private val homeOwner = TestFixtures.uuid()
    private val homeManager = TestFixtures.uuid()

    private val mission = TestFixtures.uuid()
    private val tour = TestFixtures.uuid()
    private val clinic = TestFixtures.uuid()

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(mission, branchType = BranchType.MEDICAL_MISSION)
        BranchWorkforceFixtures.insertTestBranch(tour, branchType = BranchType.PROVINCIAL_TOUR)
        BranchWorkforceFixtures.insertTestBranch(clinic)
        listOf(
            owner to "OWNER",
            manager to "MANAGER",
            coordinator to "COORDINATOR",
            practitioner to "PRACTITIONER",
            homeOwner to "OWNER",
            homeManager to "MANAGER",
        ).forEach { (id, role) ->
            IdentityFixtures.insertTestUser(id, role.lowercase())
            IdentityFixtures.assignRole(id, role)
        }
        IdentityFixtures.insertUser(
            id = inactiveOwner,
            username = "inactive-owner-${inactiveOwner.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "inactive-owner-${inactiveOwner.toString().take(8)}@t.st",
            displayName = "Inactive Owner",
            status = UserStatus.INACTIVE,
        )
        IdentityFixtures.assignRole(inactiveOwner, "OWNER")
        listOf(homeOwner, homeManager).forEach {
            BranchWorkforceFixtures.insertTestAssignment(userId = it, branchId = clinic, slot = 1, assignedBy = it)
        }
    }

    private fun codesAt(
        userId: UUID,
        branchId: UUID,
    ): Set<String> =
        CapabilityService
            .getCapabilitiesForUser(userId)
            .filter { it.contextType == CapabilityContextType.BRANCH && it.contextId == branchId.toString() }
            .map { it.capabilityCode }
            .toSet()

    @Test
    fun `an Owner and a Manager work every mission and tour as a member works their home branch`() {
        listOf(mission, tour).forEach { branch ->
            assertEquals(codesAt(homeOwner, clinic), codesAt(owner, branch), "Owner at $branch")
            assertEquals(codesAt(homeManager, clinic), codesAt(manager, branch), "Manager at $branch")
            listOf(owner, manager).forEach {
                assertTrue(Access.allows(it, Act.WORK_DAY, Place.today(branch)), "clock in and record")
                assertTrue(Access.allows(it, Act.SEE_COMMISSION, Place.today(branch)), "shares commission")
                assertTrue(Access.allows(it, Act.VOID, Place.today(branch)), "voids")
            }
        }
    }

    @Test
    fun `a clinic still needs an assignment`() {
        listOf(owner, manager).forEach {
            assertFalse(Access.allows(it, Act.WORK_DAY, Place.today(clinic)))
        }
    }

    @Test
    fun `everyone else works a mission or tour only as a helper`() {
        listOf(coordinator, practitioner, inactiveOwner).forEach { who ->
            listOf(mission, tour).forEach { branch ->
                assertFalse(Access.allows(who, Act.WORK_DAY, Place.today(branch)), "$who at $branch")
            }
        }
    }
}
