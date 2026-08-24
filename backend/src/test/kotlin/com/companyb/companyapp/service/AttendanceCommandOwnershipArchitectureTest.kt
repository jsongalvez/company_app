package com.companyb.companyapp.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #321 ownership seam (ADR-0024). Cheap source-level assertions that the Attendance module keeps
 * its migrated shape: the clock-in/clock-out commands own their single transaction, the repository
 * is an in-transaction store with no `auditFn` coordination, operational-day semantics come only
 * from the Branch Day boundary (#318), and commission side effects stay inside the command
 * transaction. Mechanical enforcement for the whole backend arrives with #324; this pins the
 * second reference pattern until then.
 */
class AttendanceCommandOwnershipArchitectureTest {
    private fun mainSource(relative: String): String =
        File("backend/src/main/kotlin/com/companyb/companyapp/$relative").readText()

    @Test
    fun `attendance store has no nested write transactions or audit callbacks`() {
        // (file, allowed read-wrapper transaction blocks)
        val file = "service/attendance/AttendanceRepository.kt"
        val expectedBlocks = 7
        val source = mainSource(file)

        assertFalse(source.contains("auditFn"), "$file: auditFn coordination must not return (#321 deletion test)")
        // Raw count is safe here: repository KDoc never mentions `transaction (` (#412 verified).
        val blocks = Regex("""\btransaction\s*[(\{]""").findAll(source).count()
        assertEquals(expectedBlocks, blocks, "$file: read-only wrappers only")
        assertTrue(source.contains("InTransaction("), "$file: must expose in-transaction store operations")
    }

    @Test
    fun `each attendance mutation command owns exactly one transaction`() {
        val source = mainSource("service/attendance/AttendanceService.kt")

        assertFalse(source.contains("auditFn"), "commands call AuditLogRepository directly")
        listOf(
            "clockIn",
            "clockOut",
        ).forEach { command ->
            val body =
                commandBody(source, command)
                    ?: error("command $command not found in AttendanceService")
            val blocks = Regex("""\btransaction\s*[(\{]""").findAll(codeOnly(body)).count()
            assertEquals(1, blocks, "command $command must open exactly one transaction, found $blocks")
        }
    }

    @Test
    fun `attendance reaches branch day only through the feature boundary`() {
        val attendanceSources =
            File("backend/src/main/kotlin/com/companyb/companyapp/service/attendance")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.readText() }
                .toList()

        assertTrue(
            attendanceSources.any { it.contains("BranchDayService.") },
            "attendance must collaborate with the Branch Day boundary",
        )
        attendanceSources.forEach { source ->
            listOf(
                "BranchDayTable.update",
                "BranchDayTable.insert",
                "BranchDayTable.deleteWhere",
                "BranchDayTable.upsert",
            ).forEach { manipulation ->
                assertFalse(
                    source.contains(manipulation),
                    "attendance must not manipulate branch-day tables directly: $manipulation (#321)",
                )
            }
        }
    }

    @Test
    fun `attendance derives no operational dates independently`() {
        val attendanceSources =
            File("backend/src/main/kotlin/com/companyb/companyapp/service/attendance")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.readText() }
                .toList()

        attendanceSources.forEach { source ->
            listOf(
                "LocalDate.now",
                "ZoneId",
            ).forEach { derivation ->
                assertFalse(
                    source.contains(derivation),
                    "operational-date knowledge belongs to Branch Day (#318): " +
                        "$derivation must not appear in attendance",
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
        // Stop at any member fun declaration — bare, visibility-modified, or suspend (#412:
        // `\n    fun ` alone swallowed the private helpers declared below clockIn).
        val nextFun =
            Regex("""\n    ((?:private|internal|protected|public)\s+)?(?:suspend\s+)?fun\s""")
                .find(source, start + marker.length)
                ?.range
                ?.first ?: source.length
        return source.substring(start, nextFun)
    }

    /**
     * Strips line/block comments (Kotlin block comments nest) and string/char/triple-quoted
     * literals, replacing each skipped span with one space so doc prose can no longer satisfy
     * code-count assertions (#412: a KDoc sentence containing "transaction (" counted as a block).
     */
    private fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    out.append(' ')
                    i = source.indexOf('\n', i).takeIf { it >= 0 } ?: source.length
                }

                source.startsWith("/*", i) -> {
                    i = skipBlockComment(source, i, out)
                }

                source.startsWith("\"\"\"", i) -> {
                    out.append(' ')
                    i = source.indexOf("\"\"\"", i + 3).takeIf { it >= 0 }?.plus(3) ?: source.length
                }

                source[i] == '"' || source[i] == '\'' -> {
                    i = skipQuoted(source, i, out)
                }

                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Skips a (possibly nested) block comment starting at [start]; appends one space to [out]. */
    private fun skipBlockComment(
        source: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        var depth = 1
        var i = start + 2
        while (i < source.length && depth > 0) {
            when {
                source.startsWith("*/", i) -> {
                    depth--
                    i += 2
                }

                source.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }

                else -> {
                    i++
                }
            }
        }
        out.append(' ')
        return i
    }

    /** Skips a quoted literal ('...' or "...") opening at [start]; appends one space to [out]. */
    private fun skipQuoted(
        source: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        var i = start + 1
        while (i < source.length && source[i] != source[start]) {
            if (source[i] == '\\') i++
            i++
        }
        out.append(' ')
        return (i + 1).coerceAtMost(source.length)
    }
}
