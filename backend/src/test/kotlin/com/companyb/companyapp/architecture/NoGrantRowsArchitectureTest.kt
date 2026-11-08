package com.companyb.companyapp.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rights come only from facts, so no code, seed or tool may read or write grant rows again. */
class NoGrantRowsArchitectureTest {
    @Test
    fun `nothing outside the migrations names the grant row tables`() {
        val roots = listOf("backend/src/main/kotlin", "backend/src/dev", "backend/src/test", "tools").map(::File)
        val hits =
            roots
                .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in SCANNED }.toList() }
                .filter { it.name != "NoGrantRowsArchitectureTest.kt" && it.name != "grants-to-facts.py" }
                .filter { file -> GRANT_ROWS.containsMatchIn(file.readText()) }
                .map { it.path }
                .sorted()
        assertEquals(emptyList(), hits, "a right is a role, an assignment, a relief day or a helper seat")
    }

    private companion object {
        val SCANNED = setOf("kt", "sql", "sh", "py", "mjs", "ts")
        val GRANT_ROWS =
            Regex("""\buser_capability\b|\bUserCapabilityTable\b|\bCapabilityTable\b|\bgrantCapability\(""")
    }
}
