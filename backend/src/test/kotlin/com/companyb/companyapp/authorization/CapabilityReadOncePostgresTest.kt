package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A request reads a user's grants once, and a write in the request makes the next check read again. */
class CapabilityReadOncePostgresTest : BasePostgresTest() {
    private val user = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranch = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(user, "read-once")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Read Once $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranch, "Read Once Other $otherBranch")
        grant(branchId)
    }

    @Test
    fun `checks in one request read the grants once`() {
        val queries =
            inRequest {
                assertTrue(edits(branchId))
                assertFalse(edits(otherBranch))
                assertTrue(CapabilityRepository.hasCapabilityAnyContext(user, CapabilityCodes.EDIT_BRANCH_DATA))
                CapabilityRepository.findBranchWindow(user)
                RequestQueryTally.snapshot().queries
            }
        assertEquals(1, queries)
    }

    @Test
    fun `a write to a table the grants don't read keeps them`() {
        val queries =
            inRequest {
                assertTrue(edits(branchId))
                transaction {
                    exec(
                        "INSERT INTO branch_day (id, branch_id, \"date\") " +
                            "VALUES (gen_random_uuid(), '$branchId', DATE '2026-01-02') ON CONFLICT DO NOTHING",
                    )
                }
                assertTrue(edits(branchId))
                RequestQueryTally.snapshot().queries
            }
        assertEquals(2, queries)
    }

    @Test
    fun `the branch window names each branch once`() {
        assertEquals(listOf(branchId), CapabilityRepository.findBranchWindow(user))
    }

    @Test
    fun `a grant written in the request is seen by the next check`() {
        inRequest {
            assertFalse(edits(otherBranch))
            grant(otherBranch)
            assertTrue(edits(otherBranch))
        }
    }

    @Test
    fun `a grant rolled back in the request is gone for the next check`() {
        inRequest {
            transaction {
                grant(otherBranch)
                assertTrue(edits(otherBranch))
                rollback()
            }
            assertFalse(edits(otherBranch))
        }
    }

    private fun edits(branch: java.util.UUID) =
        CapabilityRepository.hasCapability(user, CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH, branch)

    private fun grant(branch: java.util.UUID) =
        IdentityFixtures.grantCapability(
            userId = user,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branch,
            sourceId = sourceId,
        )

    private fun <T> inRequest(block: () -> T): T {
        Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()))
        try {
            RequestQueryTally.startRequest()
            return block()
        } finally {
            RequestQueryTally.endRequest()
            Database.connect(TestDatabaseLifecycle.requireTestDataSource())
        }
    }
}
