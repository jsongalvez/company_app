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
        val expectedBlocks = 5
        val source = mainSource(file)

        assertFalse(source.contains("auditFn"), "$file: auditFn coordination must not return (#321 deletion test)")
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
            val blocks = Regex("""\btransaction\s*[(\{]""").findAll(body).count()
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
        val nextFun = source.indexOf("\n    fun ", start + marker.length).takeIf { it >= 0 } ?: source.length
        return source.substring(start, nextFun)
    }
}
