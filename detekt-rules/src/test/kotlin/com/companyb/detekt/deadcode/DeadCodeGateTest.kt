package com.companyb.detekt.deadcode

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// End-to-end proof through the real gate path CI uses: the driver main, an
// exemptions file, and exit codes. Zero-debt (map #529 Phase C): an unexempted
// unused declaration fails, a referenced one passes, an exempted platform
// bridge passes, a stale exemption fails — with no baseline and no
// grandfathering path.
class DeadCodeGateTest {
    @Test
    fun `unexempted unused declaration fails the gate`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\nfun fresh(): Int = 2\n")) { dir ->
            val (code, out) = runGate(dir, null)
            assertEquals(1, code, "expected gate failure, output:\n$out")
            assertTrue(out.contains("NEW") && out.contains("fresh"), "output names the finding:\n$out")
            assertFalse(out.contains("used("), "referenced declaration must not fail:\n$out")
        }
    }

    @Test
    fun `exempt finding passes and stale exemption fails`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\nfun bridged(): Int = 2\n")) { dir ->
            File(dir, "use.kt").writeText("package demo\nfun main() { val x: Int = used() }\n")
            val exempt = File(dir, "entry-points.txt")
            val key =
                DeadCodeAnalyzer
                    .analyze(dir, testFiles(dir), emptyList(), emptyList())
                    .findings
                    .single { it.signature.contains("bridged") }
                    .key()
            exempt.writeText("$key # platform: verified caller in excluded mains\n")
            val (code, out) = runGate(dir, exempt)
            assertEquals(0, code, "expected gate pass, output:\n$out")
            File(dir, "a.kt").writeText("package demo\nfun used(): Int = 1\n")
            val (staleCode, staleOut) = runGate(dir, exempt)
            assertEquals(1, staleCode, "expected stale failure, output:\n$staleOut")
            assertTrue(staleOut.contains("STALE"), "output names the stale exemption:\n$staleOut")
        }
    }

    @Test
    fun `retired write-baseline flag is rejected without grandfathering`() {
        withProject(mapOf("a.kt" to "package demo\nfun fresh(): Int = 2\n")) { dir ->
            val captured = ByteArrayOutputStream()
            val previous = System.out
            System.setOut(PrintStream(captured))
            try {
                val code =
                    DeadCodeMain.run(
                        gateArgs(dir) + arrayOf("--write-baseline", File(dir, "grandfathered.txt").absolutePath),
                    )
                assertEquals(2, code, "expected tool-error rejection, output:\n$captured")
            } finally {
                System.setOut(previous)
            }
            assertFalse(File(dir, "grandfathered.txt").exists(), "no grandfather file may be written")
        }
    }

    private fun testFiles(dir: File): List<File> =
        dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sorted()
            .toList()

    @Test
    fun `deleted declaration left in exemptions is stale and fails`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\n")) { dir ->
            val exempt = File(dir, "entry-points.txt").also { it.writeText("a.kt|function|demo.used()\n") }
            val (code, out) = runGate(dir, exempt)
            assertEquals(1, code, "expected stale failure, output:\n$out")
            assertTrue(out.contains("STALE"), "output names the stale entry:\n$out")
        }
    }

    private fun withProject(
        files: Map<String, String>,
        body: (File) -> Unit,
    ) {
        val dir = Files.createTempDirectory("deadcode-gate-test").toFile()
        try {
            for ((name, content) in files) {
                File(dir, name).writeText(content)
            }
            body(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun gateArgs(dir: File): Array<String> =
        arrayOf(
            "--root",
            dir.absolutePath,
            "--candidate",
            dir.absolutePath,
        )

    private fun runGate(
        dir: File,
        exempt: File?,
    ): Pair<Int, String> {
        val captured = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(captured))
        try {
            // use.kt references used() so only fresh()/bridged() can report.
            File(dir, "use.kt").writeText("package demo\nfun main() { val x: Int = used() }\n")
            val entryArgs = exempt?.let { arrayOf("--entry-points", it.absolutePath) } ?: emptyArray()
            val args = gateArgs(dir) + entryArgs
            val code = DeadCodeMain.run(args)
            return code to captured.toString()
        } finally {
            System.setOut(previous)
        }
    }
}
