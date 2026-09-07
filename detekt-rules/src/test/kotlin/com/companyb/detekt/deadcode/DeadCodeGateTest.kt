package com.companyb.detekt.deadcode

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// End-to-end proof for #530 through the real gate path CI uses: the driver
// main, a file baseline, and exit codes. unused-public fails, referenced
// passes, recorded history passes, new debt blocks.
class DeadCodeGateTest {
    @Test
    fun `unbaselined unused declaration fails the gate`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\nfun fresh(): Int = 2\n")) { dir ->
            val baseline = File(dir, "baseline.txt").also { it.writeText("") }
            val (code, out) = runGate(dir, baseline)
            assertEquals(1, code, "expected gate failure, output:\n$out")
            assertTrue(out.contains("NEW") && out.contains("fresh"), "output names the finding:\n$out")
            assertFalse(out.contains("used("), "referenced declaration must not fail:\n$out")
        }
    }

    @Test
    fun `baselined history passes and regenerated baseline matches`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\nfun old(): Int = 2\n")) { dir ->
            // use.kt must exist before generation: it references used().
            File(dir, "use.kt").writeText("package demo\nfun main() { val x: Int = used() }\n")
            val baseline = File(dir, "baseline.txt")
            val generated = File(dir, "generated.txt")
            val writeCode =
                DeadCodeMain.run(
                    gateArgs(dir, baseline) + arrayOf("--write-baseline", generated.absolutePath),
                )
            assertEquals(0, writeCode)
            baseline.writeText(generated.readText())
            val (code, out) = runGate(dir, baseline)
            assertEquals(0, code, "expected gate pass, output:\n$out")
            assertTrue(out.contains("PASSED"), "output confirms pass:\n$out")
        }
    }

    @Test
    fun `exempt finding passes and stale exemption fails`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\nfun bridged(): Int = 2\n")) { dir ->
            File(dir, "use.kt").writeText("package demo\nfun main() { val x: Int = used() }\n")
            val baseline = File(dir, "baseline.txt").also { it.writeText("") }
            val exempt = File(dir, "entry-points.txt")
            val key =
                DeadCodeAnalyzer
                    .analyze(dir, testFiles(dir), emptyList(), emptyList())
                    .findings
                    .single { it.signature.contains("bridged") }
                    .key()
            exempt.writeText("$key # platform: verified caller in excluded mains\n")
            val (code, out) = runGate(dir, baseline, exempt)
            assertEquals(0, code, "expected gate pass, output:\n$out")
            File(dir, "a.kt").writeText("package demo\nfun used(): Int = 1\n")
            val (staleCode, staleOut) = runGate(dir, baseline, exempt)
            assertEquals(1, staleCode, "expected stale failure, output:\n$staleOut")
            assertTrue(staleOut.contains("STALE"), "output names the stale exemption:\n$staleOut")
        }
    }

    private fun testFiles(dir: File): List<File> =
        dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sorted()
            .toList()

    @Test
    fun `deleted declaration left in baseline is stale and fails`() {
        withProject(mapOf("a.kt" to "package demo\nfun used(): Int = 1\n")) { dir ->
            val baseline = File(dir, "baseline.txt").also { it.writeText("a.kt|function|demo.used()\n") }
            val (code, out) = runGate(dir, baseline)
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

    private fun gateArgs(
        dir: File,
        baseline: File,
    ): Array<String> =
        arrayOf(
            "--root",
            dir.absolutePath,
            "--candidate",
            dir.absolutePath,
            "--baseline",
            baseline.absolutePath,
        )

    private fun runGate(
        dir: File,
        baseline: File,
        exempt: File? = null,
    ): Pair<Int, String> {
        val captured = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(captured))
        try {
            // use.kt references used() so only fresh()/old() can report.
            File(dir, "use.kt").writeText("package demo\nfun main() { val x: Int = used() }\n")
            val entryArgs = exempt?.let { arrayOf("--entry-points", it.absolutePath) } ?: emptyArray()
            val args = gateArgs(dir, baseline) + entryArgs
            val code = DeadCodeMain.run(args)
            return code to captured.toString()
        } finally {
            System.setOut(previous)
        }
    }
}
