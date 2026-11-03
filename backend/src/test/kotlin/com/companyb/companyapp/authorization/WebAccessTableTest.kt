package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The web reads who may do what from `web/src/lib/access.gen.ts`, which is rendered here from [Act].
 * A code or act changed on the server fails this test until the file is rewritten
 * (`WRITE_WEB_ACCESS=1`), and the rewrite fails web typecheck wherever the web names a code that is gone.
 */
class WebAccessTableTest {
    @Test
    fun `the web's access table matches the server's`() {
        val file = File("web/src/lib/access.gen.ts")
        val expected = render()
        if (System.getenv("WRITE_WEB_ACCESS") == "1") file.writeText(expected)
        assertEquals(
            expected,
            if (file.exists()) file.readText() else "",
            "${file.path} is stale: run WRITE_WEB_ACCESS=1 ./gradlew :backend:test --tests '*WebAccessTableTest'",
        )
    }

    private val branch = UUID(0, 1)
    private val otherBranch = UUID(0, 2)
    private val day = UUID(0, 3)
    private val otherDay = UUID(0, 4)
    private val label =
        mapOf(
            branch to "b1",
            otherBranch to "b2",
            day to "d1",
            otherDay to "d2",
            CapabilityService.GLOBAL_CONTEXT_ID to "all",
        )

    private fun reachCases(): List<String> {
        val grants =
            listOf(
                CapabilityContextType.GLOBAL to CapabilityService.GLOBAL_CONTEXT_ID,
                CapabilityContextType.BRANCH to branch,
                CapabilityContextType.BRANCH to otherBranch,
                CapabilityContextType.BRANCH_DAY to day,
                CapabilityContextType.BRANCH_DAY to otherDay,
            )
        val places =
            listOf(
                "everywhere" to Place.Everywhere,
                "b1" to Place.Branch(branch),
                "b1/d1" to Place.Day(day, branch),
            )
        return Reach.entries.flatMap { reach ->
            grants.flatMap { grant ->
                places.map { (name, place) ->
                    val counts = Access.contextsFor(reach, place)?.contains(grant) ?: true
                    "    [\"$reach\", \"${grant.first}:${label.getValue(grant.second)}\", \"$name\", $counts],"
                }
            }
        }
    }

    private fun render(): String {
        val codes =
            Act.entries
                .map { it.code }
                .distinct()
                .sorted()
        val acts =
            Act.entries.joinToString(
                "\n",
            ) { "    ${it.name}: { code: \"${it.code}\", reach: \"${it.reach}\", read: \"${it.whenReading.name}\" }," }
        return buildString {
            appendLine("// Rendered from Act by backend WebAccessTableTest. Do not edit.")
            appendLine("export const CODES = [")
            codes.forEach { appendLine("    \"$it\",") }
            appendLine("] as const;")
            appendLine("export type Code = (typeof CODES)[number];")
            appendLine("export type Reach = ${Reach.entries.joinToString(" | ") { "\"$it\"" }};")
            appendLine("export const ACTS = {")
            appendLine(acts)
            appendLine("} as const satisfies Record<string, { code: Code; reach: Reach; read: string }>;")
            appendLine("export type Act = keyof typeof ACTS;")
            appendLine("/** [reach, grant row, place, counts]: what Access.contextsFor decides. */")
            appendLine("export const REACH_CASES: readonly (readonly [Reach, string, string, boolean])[] = [")
            reachCases().forEach(::appendLine)
            appendLine("];")
        }
    }
}
