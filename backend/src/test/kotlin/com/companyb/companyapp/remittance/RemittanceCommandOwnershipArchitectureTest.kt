package com.companyb.companyapp.remittance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #320 ownership seam (ADR-0024). Cheap source-level assertions that the Remittance module keeps
 * its migrated shape: commands own the single transaction, repositories are in-transaction stores
 * with no `auditFn` coordination, Branch Day is reached only through its feature boundary, and
 * persistence `*Table` imports never leak into the public command surface. Mechanical enforcement
 * for the whole backend arrives with #324; this pins the reference pattern until then.
 */
class RemittanceCommandOwnershipArchitectureTest {
    private fun mainSource(relative: String): String =
        File("backend/src/main/kotlin/com/companyb/companyapp/$relative").readText()

    @Test
    fun `remittance stores have no nested write transactions or audit callbacks`() {
        // (file, allowed read-wrapper transaction blocks)
        listOf(
            "remittance/RemittanceRepository.kt" to 7,
            "remittance/RemittanceLineRepository.kt" to 2,
            "remittance/RemittanceDayBreakdownRepository.kt" to 1,
            "remittance/RemittanceFinancialSnapshotRepository.kt" to 1,
        ).forEach { (file, expectedBlocks) ->
            val source = mainSource(file)

            assertFalse(source.contains("auditFn"), "$file: auditFn coordination must not return (#320 deletion test)")
            val blocks = Regex("""\btransaction\s*[(\{]""").findAll(source).count()
            assertEquals(expectedBlocks, blocks, "$file: read-only wrappers only")
            assertTrue(source.contains("InTransaction("), "$file: must expose in-transaction store operations")
        }
    }

    @Test
    fun `each remittance mutation command owns exactly one transaction`() {
        val source = mainSource("remittance/RemittanceService.kt")

        assertFalse(source.contains("auditFn"), "commands call RemittanceAudit/AuditLog directly")
        assertFalse(
            Regex("""import .*repository\.model\.\w+Table""").containsMatchIn(source),
            "public command surface must not import persistence Table objects (#320)",
        )
        listOf(
            "submit",
            "createDraft",
            "undoAt",
            "updateHeader",
            "addLine",
            "removeLine",
            "addDayBreakdown",
            "removeDayBreakdown",
        ).forEach { command ->
            val body =
                commandBody(source, command)
                    ?: error("command $command not found in RemittanceService")
            val blocks = Regex("""\btransaction\s*[(\{]""").findAll(body).count()
            assertEquals(1, blocks, "command $command must open exactly one transaction, found $blocks")
        }
    }

    @Test
    fun `remittance reaches branch day only through the feature boundary`() {
        val remittanceSources =
            File("backend/src/main/kotlin/com/companyb/companyapp/remittance")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.readText() }
                .toList()

        assertTrue(
            remittanceSources.any { it.contains("BranchDayService.") },
            "remittance must collaborate with the Branch Day boundary",
        )
        remittanceSources.forEach { source ->
            // Picker/list reads may query branch_day; status manipulation may not (#320).
            listOf(
                "BranchDayTable.update",
                "BranchDayTable.insert",
                "BranchDayTable.deleteWhere",
                "BranchDayTable.upsert",
            ).forEach { manipulation ->
                assertFalse(
                    source.contains(manipulation),
                    "remittance must not manipulate branch-day tables directly: $manipulation (#320)",
                )
            }
        }
    }

    @Test
    fun `remittance cannot construct branch-day audit payloads`() {
        val remittanceSources =
            File("backend/src/main/kotlin/com/companyb/companyapp/remittance")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.name to it.readText() }
                .toList()

        remittanceSources.forEach { (name, source) ->
            // #603 — the Branch Day transition writes its own audit; remittance supplies only
            // actor/reason through the BranchDayService boundary. Picker/report reads stay allowed.
            // BranchDayAudit pins the collaborating seam shut; the alias and raw-literal entries
            // close the corresponding evasion shapes (the quoted literal excludes the legitimate
            // "branch_day_id" FK column).
            listOf(
                "BranchDayTable.tableName",
                "BranchDayTable::auditFields",
                "BranchDayTable.auditFields",
                "BranchDayTable as",
                "BranchDayAudit",
                "branchDayUpdated",
                "\"branch_day\"",
            ).forEach { payload ->
                assertFalse(
                    source.contains(payload),
                    "$name: remittance must not construct branch-day audit payloads: $payload (#603)",
                )
            }
        }
    }

    private fun commandBody(
        source: String,
        command: String,
    ): String? {
        val marker = "fun $command("
        val start = source.indexOf(marker).takeIf { it >= 0 } ?: return null
        val nextFun = source.indexOf("\n    fun ", start + marker.length).takeIf { it >= 0 } ?: source.length
        return source.substring(start, nextFun)
    }
}
