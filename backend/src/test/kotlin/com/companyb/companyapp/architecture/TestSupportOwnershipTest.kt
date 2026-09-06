package com.companyb.companyapp.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test-support ownership pins for map #533 (#552).
 *
 * The lifecycle helper must stay fixture-independent (no feature tables,
 * stores or services); each fixture family owns its tables; cross-feature
 * lock barriers live under integration ownership.
 */
class TestSupportOwnershipTest {
    private fun testSource(relativePath: String): String {
        val file = File("backend/src/test/kotlin/com/companyb/companyapp/$relativePath")
        assertTrue(file.isFile, "test source missing: $relativePath")
        return file.readText()
    }

    @Test
    fun `lifecycle helper imports no feature tables stores or services`() {
        val source = testSource("testsupport/database/TestDatabaseLifecycle.kt")
        val forbidden =
            source
                .lines()
                .filter { it.startsWith("import com.companyb.companyapp.") }
                .filter { line ->
                    FORBIDDEN_LIFECYCLE_IMPORTS.any { line.startsWith("import $it") }
                }
        assertTrue(
            forbidden.isEmpty(),
            "lifecycle helper must not import feature state:\n${forbidden.joinToString("\n")}",
        )
    }

    @Test
    fun `each fixture family owns its tables`() {
        val identity = testSource("testsupport/fixtures/IdentityFixtures.kt")
        assertTrue("AppUserTable" in identity, "identity fixtures must own user rows")
        assertTrue("UserCapabilityTable" in identity, "identity fixtures must own grant rows")
        assertTrue("BranchTable" !in identity, "branch rows belong to the branch/workforce family")

        val branchWorkforce = testSource("testsupport/fixtures/BranchWorkforceFixtures.kt")
        assertTrue("BranchTable" in branchWorkforce, "branch/workforce fixtures must own branch rows")
        assertTrue("BranchDayTable" in branchWorkforce, "branch/workforce fixtures must own branch-day rows")
        assertTrue("AttendanceTable" in branchWorkforce, "branch/workforce fixtures must own attendance rows")
        assertTrue("SessionTable" !in branchWorkforce, "session rows belong to the session/client family")

        val sessionClient = testSource("testsupport/fixtures/SessionClientFixtures.kt")
        assertTrue("SessionTable" in sessionClient, "session/client fixtures must own session rows")
        assertTrue("ClientTable" in sessionClient, "session/client fixtures must own client rows")
        assertTrue("NotificationTable" in sessionClient, "session/client fixtures must own notification rows")

        val commerceFinance = testSource("testsupport/fixtures/CommerceFinanceFixtures.kt")
        assertTrue("ProductTable" in commerceFinance, "commerce/finance fixtures must own product rows")
        assertTrue("ExpenseTable" in commerceFinance, "commerce/finance fixtures must own expense rows")
        assertTrue("CompensationTable" in commerceFinance, "commerce/finance fixtures must own compensation rows")
    }

    @Test
    fun `lock barrier lives under integration ownership`() {
        val barrier = File("backend/src/test/kotlin/com/companyb/companyapp/integration/LockBarrier.kt")
        assertTrue(barrier.isFile, "LockBarrier must live under integration ownership")
        assertTrue(
            !File("backend/src/test/kotlin/com/companyb/companyapp/test/LockBarrier.kt").exists(),
            "LockBarrier must not remain in the legacy test bucket",
        )
        assertTrue(
            !File("backend/src/test/kotlin/com/companyb/companyapp/test/DatabaseTestHelper.kt").exists(),
            "legacy mixed helper must be gone",
        )
    }

    private companion object {
        val FORBIDDEN_LIFECYCLE_IMPORTS =
            listOf(
                "com.companyb.companyapp.identity.",
                "com.companyb.companyapp.branch.",
                "com.companyb.companyapp.branchday.",
                "com.companyb.companyapp.service.",
                "com.companyb.companyapp.repository.",
                "com.companyb.companyapp.audit.",
                "com.companyb.companyapp.client.",
                "com.companyb.companyapp.session.",
                "com.companyb.companyapp.commerce.",
                "com.companyb.companyapp.finance.",
                "com.companyb.companyapp.commission.",
                "com.companyb.companyapp.remittance.",
                "com.companyb.companyapp.reporting.",
                "com.companyb.companyapp.notification.",
                "com.companyb.companyapp.authorization.",
                "com.companyb.companyapp.workforce.",
            )
    }
}
