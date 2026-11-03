package com.companyb.companyapp.authorization

import java.io.File
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

    private fun render(): String {
        val codes =
            Act.entries
                .map { it.code }
                .distinct()
                .sorted()
        val acts =
            Act.entries.joinToString(
                "\n",
            ) { "    ${it.name}: { code: \"${it.code}\", reach: \"${it.reach}\" }," }
        return buildString {
            appendLine("// Rendered from Act by backend WebAccessTableTest. Do not edit.")
            appendLine("export const CODES = [")
            codes.forEach { appendLine("    \"$it\",") }
            appendLine("] as const;")
            appendLine("export type Code = (typeof CODES)[number];")
            appendLine("export type Reach = ${Reach.entries.joinToString(" | ") { "\"$it\"" }};")
            appendLine("export const ACTS = {")
            appendLine(acts)
            appendLine("} as const satisfies Record<string, { code: Code; reach: Reach }>;")
        }
    }
}
