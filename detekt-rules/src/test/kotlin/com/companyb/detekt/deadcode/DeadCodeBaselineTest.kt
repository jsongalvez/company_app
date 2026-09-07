package com.companyb.detekt.deadcode

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadCodeBaselineTest {
    private fun finding(key: String): DeadCodeFinding {
        val parts = key.split("|")
        return DeadCodeFinding(path = parts[0], kind = parts[1], signature = parts[2], line = 1)
    }

    @Test
    fun `recorded findings pass with no stale entries`() {
        val key = "a.kt|function|demo.live()"
        val diff = DeadCodeBaseline.diff(listOf(finding(key)), setOf(key))
        assertTrue(diff.isClean(), "expected clean diff, got $diff")
    }

    @Test
    fun `new unbaselined finding fails`() {
        val diff = DeadCodeBaseline.diff(listOf(finding("a.kt|function|demo.new()")), emptySet())
        assertFalse(diff.isClean())
        assertEquals(1, diff.unbaselined.size)
        assertTrue(diff.stale.isEmpty())
    }

    @Test
    fun `resolved finding left in baseline is stale and fails`() {
        val diff = DeadCodeBaseline.diff(emptyList(), setOf("a.kt|function|demo.gone()"))
        assertFalse(diff.isClean())
        assertEquals(listOf("a.kt|function|demo.gone()"), diff.stale)
    }

    @Test
    fun `baseline file round trip ignores comments and blanks`() {
        val dir = Files.createTempDirectory("deadcode-baseline-test").toFile()
        try {
            val file = File(dir, "baseline.txt")
            file.writeText("# comment\n\na.kt|function|demo.live()\n")
            assertEquals(setOf("a.kt|function|demo.live()"), DeadCodeBaseline.load(file))
            assertEquals(emptySet(), DeadCodeBaseline.load(File(dir, "missing.txt")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `format emits one sorted key per line`() {
        val text =
            DeadCodeBaseline.format(
                listOf(finding("b.kt|class|demo.B"), finding("a.kt|function|demo.a()")),
            )
        val keys = text.lines().filter { !it.startsWith("#") && it.isNotBlank() }
        assertEquals(listOf("a.kt|function|demo.a()", "b.kt|class|demo.B"), keys)
    }
}
