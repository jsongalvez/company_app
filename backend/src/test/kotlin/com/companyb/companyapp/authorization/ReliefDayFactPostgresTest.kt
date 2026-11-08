package com.companyb.companyapp.authorization

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Relief is a `relief_day` fact; `user_capability` can no longer hold a day. */
class ReliefDayFactPostgresTest : BasePostgresTest() {
    private val person = TestFixtures.uuid()
    private val branch = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(person, "relief-fact")
        BranchWorkforceFixtures.insertTestBranch(branch, "Relief fact branch")
    }

    @Test
    fun `granting relief writes one relief day and no grant row`() {
        val day = BranchDayService.resolveOrCreate(branch, TestFixtures.today).id
        transaction {
            AuthorizationGrants.grantReliefCapabilityInTransaction(
                GrantReliefCapabilityParams(person, day, TestFixtures.uuid(), validTo = null),
            )
        }
        transaction {
            assertEquals(
                listOf(person to day),
                ReliefDayTable.selectAll().map {
                    it[ReliefDayTable.userId] to
                        it[ReliefDayTable.branchDayId]
                },
            )
            assertEquals(0, UserCapabilityTable.selectAll().count())
        }
        assertEquals(setOf(person), CapabilityService.reliefHolders(day))
    }

    @Test
    fun `a grant row for a day is refused`() {
        val day = BranchDayService.resolveOrCreate(branch, TestFixtures.today).id
        val edit = checkNotNull(CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA))
        assertFailsWith<ExposedSQLException> {
            transaction {
                UserCapabilityTable.insert {
                    it[userId] = person
                    it[capabilityId] = edit
                    it[contextType] = CapabilityContextType.BRANCH_DAY
                    it[contextId] = day
                    it[sourceType] = CapabilitySourceType.RELIEF_ACCESS
                    it[sourceId] = person
                }
            }
        }
    }
}
