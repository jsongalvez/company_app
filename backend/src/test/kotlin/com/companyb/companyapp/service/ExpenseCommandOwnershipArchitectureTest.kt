package com.companyb.companyapp.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #319 proof-path ownership seam (ADR-0024). Cheap source-level assertions that the Expense
 * command path keeps its shape: commands own the single transaction, repository mutators are
 * in-transaction store operations with no `auditFn` coordination, and no nested write
 * transaction remains. Mechanical enforcement for the whole backend arrives with #324; this
 * pins the reference pattern until then.
 */
class ExpenseCommandOwnershipArchitectureTest {
    private fun mainSource(relative: String): String =
        File("backend/src/main/kotlin/com/companyb/companyapp/$relative").readText()

    @Test
    fun `expense repository has no nested write transactions or audit callbacks`() {
        val source = mainSource("repository/ExpenseRepository.kt")

        assertFalse(source.contains("auditFn"), "auditFn coordination must not return (#319 deletion test)")
        // Only the two read helpers (findById, findByBranchDayId) may keep convenience wrappers.
        val transactionBlocks = Regex("""\btransaction\s*\{""").findAll(source).count()
        assertEquals(2, transactionBlocks, "read-only wrappers only; found $transactionBlocks transaction blocks")
        listOf(
            "createInTransaction",
            "updateInTransaction",
            "softDeleteInTransaction",
            "restoreInTransaction",
        ).forEach { mutator ->
            assertTrue(source.contains("fun $mutator("), "$mutator must exist")
        }
    }

    @Test
    fun `each expense mutation command owns exactly one transaction`() {
        val source = mainSource("service/ExpenseService.kt")

        assertFalse(source.contains("auditFn"), "commands call AuditLog directly")
        listOf("create", "update", "softDelete", "restore").forEach { command ->
            val body =
                commandBody(source, command)
                    ?: error("command $command not found in ExpenseService")
            val blocks = Regex("""\btransaction\s*\{""").findAll(body).count()
            assertEquals(1, blocks, "command $command must open exactly one transaction, found $blocks")
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
