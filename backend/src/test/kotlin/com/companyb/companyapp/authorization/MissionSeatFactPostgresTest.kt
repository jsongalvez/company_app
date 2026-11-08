package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A helper seat is a `medical_mission_delegate` fact; `user_capability` can no longer hold one. */
class MissionSeatFactPostgresTest : BasePostgresTest() {
    private val person = TestFixtures.uuid()
    private val mission = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(person, "helper-fact")
        BranchWorkforceFixtures.insertTestBranch(mission, "Helper fact mission", BranchType.MEDICAL_MISSION)
    }

    private fun codesAtMission(): Set<String> =
        CapabilityService
            .getCapabilitiesForUser(person)
            .filter { it.contextType == CapabilityContextType.BRANCH && it.contextId == mission.toString() }
            .onEach { assertEquals(CapabilitySourceType.MEDICAL_MISSION_DELEGATE, it.sourceType) }
            .map { it.capabilityCode }
            .toSet()

    @Test
    fun `a practitioner seat gives a member's rights at its branch`() {
        IdentityFixtures.helperSeat(person, mission, DelegateRole.PRACTITIONER)
        assertEquals(Policy.MEMBER, codesAtMission())
    }

    @Test
    fun `a coordinator seat adds the coordinator's branch rights`() {
        IdentityFixtures.helperSeat(person, mission, DelegateRole.COORDINATOR)
        assertEquals(
            Policy.MEMBER +
                setOf(
                    CapabilityCodes.EDIT_PAST_DAY,
                    CapabilityCodes.SUBMIT_REMITTANCE,
                    CapabilityCodes.ASSIGN_COMPENSATION,
                    CapabilityCodes.MANAGE_PRODUCTS,
                    CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS,
                ),
            codesAtMission(),
        )
    }

    @Test
    fun `an ended seat gives nothing`() {
        IdentityFixtures.helperSeat(person, mission, DelegateRole.COORDINATOR, ended = true)
        assertEquals(emptySet(), codesAtMission())
    }

    @Test
    fun `a coordinator seat gets the branch's alerts and a practitioner seat does not`() {
        val practitioner = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(practitioner, "helper-fact-practitioner")
        IdentityFixtures.helperSeat(person, mission, DelegateRole.COORDINATOR)
        IdentityFixtures.helperSeat(practitioner, mission, DelegateRole.PRACTITIONER)
        listOf(person, practitioner).forEachIndexed { slot, who ->
            BranchWorkforceFixtures.insertTestAssignment(
                userId = who,
                branchId = mission,
                slot = (slot + 1).toShort(),
                assignedBy = who,
            )
        }
        assertEquals(
            mapOf(mission to listOf(person)),
            transaction { CapabilityService.alertedByBranchInTransaction(listOf(mission)) },
        )
    }

    @Test
    fun `a helper grant row is refused`() {
        val edit = checkNotNull(CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA))
        assertFailsWith<ExposedSQLException> {
            transaction {
                UserCapabilityTable.insert {
                    it[userId] = person
                    it[capabilityId] = edit
                    it[contextType] = CapabilityContextType.BRANCH
                    it[contextId] = mission
                    it[sourceType] = CapabilitySourceType.MEDICAL_MISSION_DELEGATE
                    it[sourceId] = person
                }
            }
        }
    }
}
