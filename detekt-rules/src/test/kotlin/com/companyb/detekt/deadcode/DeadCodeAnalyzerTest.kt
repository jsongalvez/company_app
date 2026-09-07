package com.companyb.detekt.deadcode

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Fixture-backed proof for #530: the real single-compilation engine reports an
// unused public declaration, passes a referenced one, and treats implicit
// entry points (override/companion/enum/synthesized/enum entries) as live.
class DeadCodeAnalyzerTest {
    @Test
    fun `unused public declarations fail while referenced ones pass`() {
        val result =
            analyze(
                mapOf(
                    "api.kt" to API_FIXTURE,
                    "use.kt" to USE_FIXTURE,
                ),
            )
        assertTrue(result.failedFiles.isEmpty(), "fixtures must parse, failed=${result.failedFiles}")
        val keys = result.findings.map { "${it.path}|${it.kind}|${it.signature}" }
        val expected =
            listOf(
                Triple("api.kt", "function", "unusedFunction("),
                Triple("api.kt", "class", "UnusedClass"),
                Triple("api.kt", "property", "unusedProperty"),
                Triple("api.kt", "object", "UnusedObject"),
                Triple("api.kt", "typealias", "UnusedAlias"),
                Triple("api.kt", "function", "unusedInternal("),
                Triple("api.kt", "constructor", "WithSecondaries"),
            )
        for ((path, kind, fragment) in expected) {
            val hit = keys.any { it.startsWith("$path|$kind|") && it.contains(fragment) }
            assertTrue(hit, "missing finding for $path $kind $fragment in $keys")
        }
        assertEquals(expected.size, keys.size, "unexpected findings: $keys")
    }

    @Test
    fun `override companion enum data and secondary constructor delegation stay live`() {
        val result = analyze(mapOf("live.kt" to LIVE_FIXTURE))
        assertTrue(result.failedFiles.isEmpty(), "fixtures must parse, failed=${result.failedFiles}")
        assertTrue(result.findings.isEmpty(), "expected no findings, got ${result.findings}")
    }

    @Test
    fun `dead sealed hierarchy reports parent and heirs together`() {
        val result = analyze(mapOf("platform.kt" to PLATFORM_DEAD_FIXTURE))
        assertTrue(result.failedFiles.isEmpty(), "fixtures must parse, failed=${result.failedFiles}")
        val keys = result.findings.map { "${it.path}|${it.kind}|${it.signature}" }
        assertEquals(3, keys.size, "parent plus both heirs report, got $keys")
        assertTrue(keys.any { it.contains("Outcome") && it.contains("Win") }, "Win reports: $keys")
        assertTrue(keys.any { it.contains("Outcome") && it.contains("Lose") }, "Lose reports: $keys")
    }

    @Test
    fun `live sealed hierarchy with exhaustive when stays silent`() {
        val result = analyze(mapOf("sealed.kt" to PLATFORM_LIVE_FIXTURE))
        assertTrue(result.failedFiles.isEmpty(), "fixtures must parse, failed=${result.failedFiles}")
        assertTrue(result.findings.isEmpty(), "expected no findings, got ${result.findings}")
    }

    @Test
    fun `unused enum with constructor args reports the enum`() {
        val result =
            analyze(
                mapOf(
                    "e.kt" to
                        "package demo\n" +
                        "enum class Dead(val label: String) { A(label = \"a\"), B(label = \"b\") }\n",
                ),
            )
        assertTrue(result.failedFiles.isEmpty(), "fixtures must parse, failed=${result.failedFiles}")
        val keys = result.findings.map { "${it.path}|${it.kind}|${it.signature}" }
        assertEquals(1, keys.size, "only the enum reports, got $keys")
        assertTrue(keys.single().contains("Dead"), "enum reports: $keys")
    }

    @Test
    fun `syntax error file is excluded and reported`() {
        val result =
            analyze(
                mapOf(
                    "good.kt" to "package demo\nfun live(): Int = 1\nfun dead(): Int = 2\n",
                    "broken.kt" to "package demo\nfun broken( = {\n",
                ),
            )
        assertEquals(listOf("broken.kt"), result.failedFiles, "broken file must be listed")
        val keys = result.findings.map { "${it.path}|${it.kind}|${it.signature}" }
        assertTrue(keys.none { it.startsWith("broken.kt") }, "broken file contributes no findings: $keys")
    }

    private fun analyze(files: Map<String, String>): DeadCodeResult {
        val dir = Files.createTempDirectory("deadcode-test").toFile()
        try {
            val written =
                files.map { (name, content) ->
                    File(dir, name).also { it.writeText(content) }
                }
            return DeadCodeAnalyzer.analyze(dir, written, emptyList(), testClasspath())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun testClasspath(): List<File> =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { it.endsWith(".jar") }
            .map { File(it) }
            .filter { it.name.startsWith("kotlin-stdlib-") && !it.name.contains("sources") && it.exists() }

    companion object {
        private const val API_FIXTURE =
            "package demo\n" +
                "fun usedFunction(): Int = 1\n" +
                "fun unusedFunction(): Int = 2\n" +
                "class UsedClass { fun greet(): String = \"hi\" }\n" +
                "class UnusedClass\n" +
                "val usedProperty: Int = 3\n" +
                "val unusedProperty: Int = 4\n" +
                "object UsedObject { val answer: Int = 42 }\n" +
                "object UnusedObject\n" +
                "typealias UsedAlias = Int\n" +
                "typealias UnusedAlias = String\n" +
                "internal fun unusedInternal(): Int = 5\n" +
                "private fun hiddenPrivate(): Int = 6\n" +
                "class WithSecondaries { constructor(x: Int); constructor(x: Int, y: Int) : this(x) }\n"

        private const val USE_FIXTURE =
            "package demo\n" +
                "fun main() {\n" +
                "    val a: Int = usedFunction()\n" +
                "    val b: UsedClass = UsedClass()\n" +
                "    val c: String = UsedClass().greet()\n" +
                "    val d: Int = usedProperty\n" +
                "    val e: Int = UsedObject.answer\n" +
                "    val f: UsedAlias = 1\n" +
                "    val g: WithSecondaries = WithSecondaries(1)\n" +
                "}\n"

        private const val LIVE_FIXTURE =
            "package demo\n" +
                "open class Base { open fun act(): Int = 1 }\n" +
                "class Child : Base() { override fun act(): Int = 2 }\n" +
                "data class UsedData(val x: Int)\n" +
                "enum class UsedEnum(val label: String) { A(\"a\"), B(\"b\") }\n" +
                "class WithCompanion { companion object { fun make(): WithCompanion = WithCompanion() } }\n" +
                "class Pair2(val a: Int, val b: Int) {\n" +
                "    operator fun component1(): Int = a\n" +
                "    operator fun component2(): Int = b\n" +
                "}\n" +
                "class Counter {\n" +
                "    private var n: Int = 0\n" +
                "    operator fun iterator(): Counter = this\n" +
                "    operator fun hasNext(): Boolean = n < 3\n" +
                "    operator fun next(): Int = n++\n" +
                "}\n" +
                "fun main() {\n" +
                "    val a: Int = Base().act()\n" +
                "    val b: Int = Child().act()\n" +
                "    val c: UsedData = UsedData(1).copy(x = 2)\n" +
                "    val d: UsedEnum = UsedEnum.A\n" +
                "    val e: WithCompanion = WithCompanion.make()\n" +
                "    val (first, second) = Pair2(1, 2)\n" +
                "    val sum: Int = first + second\n" +
                "    var total = 0\n" +
                "    for (i in Counter()) { total += i }\n" +
                "}\n"

        private const val PLATFORM_DEAD_FIXTURE =
            "package demo\n" +
                "expect fun platformName(): String\n" +
                "actual fun platformName(): String = \"test\"\n" +
                "sealed interface Outcome { data object Win : Outcome; data object Lose : Outcome }\n"

        private const val PLATFORM_LIVE_FIXTURE =
            "package demo\n" +
                "sealed interface Shape { data object Circle : Shape; data object Square : Shape }\n" +
                "fun sides(shape: Shape): Int =\n" +
                "    when (shape) {\n" +
                "        is Shape.Circle -> 1\n" +
                "        is Shape.Square -> 4\n" +
                "    }\n" +
                "fun main() { val total: Int = sides(Shape.Circle) }\n"
    }
}
