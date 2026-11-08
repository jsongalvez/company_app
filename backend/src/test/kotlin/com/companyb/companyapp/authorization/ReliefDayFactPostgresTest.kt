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

/** Relief is a `relief_day` fact, read by the policy. */
class ReliefDayFactPostgresTest : BasePostgresTest() {
    private val person = TestFixtures.uuid()
    private val branch = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(person, "relief-fact")
        BranchWorkforceFixtures.insertTestBranch(branch, "Relief fact branch")
    }

    @Test
    fun `granting relief writes one relief day`() {
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
        }
        assertEquals(setOf(person), CapabilityService.reliefHolders(day))
    }
}
