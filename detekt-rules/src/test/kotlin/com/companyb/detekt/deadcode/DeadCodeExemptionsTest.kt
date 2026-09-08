package com.companyb.detekt.deadcode

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadCodeExemptionsTest {
    private fun finding(key: String): DeadCodeFinding {
        val parts = key.split("|")
        return DeadCodeFinding(path = parts[0], kind = parts[1], signature = parts[2], line = 1)
    }

    @Test
    fun `recorded findings pass with no stale entries`() {
        val key = "a.kt|function|demo.live()"
        val diff = DeadCodeExemptions.diff(listOf(finding(key)), setOf(key))
        assertTrue(diff.isClean(), "expected clean diff, got $diff")
    }

    @Test
    fun `new unexempted finding fails`() {
        val diff = DeadCodeExemptions.diff(listOf(finding("a.kt|function|demo.new()")), emptySet())
        assertFalse(diff.isClean())
        assertEquals(1, diff.unexempted.size)
        assertTrue(diff.stale.isEmpty())
    }

    @Test
    fun `resolved finding left in exemptions is stale and fails`() {
        val diff = DeadCodeExemptions.diff(emptyList(), setOf("a.kt|function|demo.gone()"))
        assertFalse(diff.isClean())
        assertEquals(listOf("a.kt|function|demo.gone()"), diff.stale)
    }

    @Test
    fun `exemptions file round trip ignores comments and blanks`() {
        val dir = Files.createTempDirectory("deadcode-exemptions-test").toFile()
        try {
            val file = File(dir, "entry-points.txt")
            file.writeText("# comment\n\na.kt|function|demo.live()\n")
            assertEquals(setOf("a.kt|function|demo.live()"), DeadCodeExemptions.load(file))
            assertEquals(emptySet(), DeadCodeExemptions.load(File(dir, "missing.txt")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
