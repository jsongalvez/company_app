package com.companyb.companyapp.remittance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * #320 ownership seam (ADR-0024), narrowed #609.
 *
 * Transaction-count inventories, command-body parsing, declaration-presence
 * pins and the branch-day write ban retired #609: required-command ownership
 * is pinned once in `SemanticOwnershipArchitectureTest` via shared PSI
 * machinery (comment/string-proof, new read wrappers never alter a budget),
 * store nesting via the InTransaction check, audit coordination via the
 * retired-auditFn gate, and cross-feature writes via the generic table-write
 * ownership rule. Behavior atomicity stays authoritative in the remittance
 * Postgres suites.
 *
 * What remains here has no generic equivalent: #603 forbids remittance from
 * constructing branch-day audit payloads — the Branch Day transition writes
 * its own audit on the shared command transaction while remittance supplies
 * only actor/reason.
 */
class RemittanceCommandOwnershipArchitectureTest {
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
}
