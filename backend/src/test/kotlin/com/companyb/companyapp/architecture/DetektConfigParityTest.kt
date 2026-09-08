package com.companyb.companyapp.architecture

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Detekt configuration parity (#532, map #531 Phase A).
 *
 * Gradle merges `detekt.yml` + `detekt-anti-slop.yml`, but the IntelliJ
 * Detekt plugin loads `detekt.yml` alone — any shared rule with divergent
 * values (the `ReturnCount` max 2-vs-3 class) reports different results per
 * invocation. Every rule active in both files must therefore carry identical
 * effective values; the overlay only ADDS activations. This test pins the
 * drift set so a future edit to one file must update both.
 */
class DetektConfigParityTest {
    private val main = loadConfig("config/detekt/detekt.yml")
    private val overlay = loadConfig("config/detekt/detekt-anti-slop.yml")

    @Test
    fun `shared scalar policies agree across both configs`() {
        val driftSet =
            listOf(
                Triple("style", "ReturnCount", "max") to "2",
                Triple("style", "ReturnCount", "excludeGuardClauses") to "true",
                Triple("style", "ThrowsCount", "excludeGuardClauses") to "true",
                Triple("complexity", "CyclomaticComplexMethod", "ignoreSingleWhenExpression") to "true",
                Triple("complexity", "CyclomaticComplexMethod", "ignoreSimpleWhenEntries") to "true",
                Triple("style", "UnusedParameter", "allowedNames") to "'^$'",
                Triple("style", "UnusedPrivateMember", "allowedNames") to "'^$'",
                Triple("style", "UnusedPrivateProperty", "allowedNames") to "'^_$|^serialVersionUID$'",
                Triple("empty-blocks", "EmptyCatchBlock", "allowedExceptionNameRegex") to "'^$'",
                Triple("exceptions", "SwallowedException", "allowedExceptionNameRegex") to "'^$'",
                Triple("exceptions", "TooGenericExceptionCaught", "allowedExceptionNameRegex") to "'^$'",
                Triple("style", "MaxChainedCallsOnSameLine", "maxChainedCalls") to "4",
                Triple("complexity", "NamedArguments", "ignoreArgumentsMatchingNames") to "true",
            )
        for ((key, expected) in driftSet) {
            val (section, rule, prop) = key
            assertEquals(expected, main.value(section, rule, prop), "$section>$rule.$prop in detekt.yml")
            assertEquals(expected, overlay.value(section, rule, prop), "$section>$rule.$prop in overlay")
        }
    }

    @Test
    fun `swallowed exceptions stay strict in both configs`() {
        assertTrue(main.list("exceptions", "SwallowedException", "ignoredExceptionTypes").isEmpty())
        assertTrue(overlay.list("exceptions", "SwallowedException", "ignoredExceptionTypes").isEmpty())
    }

    @Test
    fun `forbidden markers agree and cover the slop vocabulary`() {
        val mainMarkers = main.entryValues("style", "ForbiddenComment", "comments")
        val overlayMarkers = overlay.entryValues("style", "ForbiddenComment", "comments")
        assertEquals(mainMarkers.sorted(), overlayMarkers.sorted(), "ForbiddenComment markers")
        val joined = mainMarkers.joinToString("\n")
        for (marker in listOf("TODO", "FIXME", "STOPSHIP", "TEMP", "PLACEHOLDER", "HACK", "XXX", "NOT")) {
            assertTrue(joined.contains(marker), "ForbiddenComment covers $marker")
        }
    }

    @Test
    fun `forbidden methods and class names agree`() {
        assertEquals("true", main.value("style", "ForbiddenMethodCall", "active"))
        assertEquals("true", overlay.value("style", "ForbiddenMethodCall", "active"))
        val mainMethods = main.entryValues("style", "ForbiddenMethodCall", "methods")
        val overlayMethods = overlay.entryValues("style", "ForbiddenMethodCall", "methods")
        assertEquals(mainMethods.sorted(), overlayMethods.sorted(), "ForbiddenMethodCall methods")
        assertTrue(mainMethods.contains("'kotlin.io.println'"), "console output stays forbidden")
        assertTrue(mainMethods.contains("'java.lang.Thread.sleep'"), "blocking sleep stays forbidden")
        val mainNames = main.list("naming", "ForbiddenClassName", "forbiddenName")
        val overlayNames = overlay.list("naming", "ForbiddenClassName", "forbiddenName")
        assertEquals(mainNames.sorted(), overlayNames.sorted(), "ForbiddenClassName names")
        assertTrue(mainNames.contains("'*Manager'"), "vague names stay forbidden")
    }

    private fun Map<String, Map<String, Map<String, Any>>>.value(
        section: String,
        rule: String,
        prop: String,
    ): Any? = this[section]?.get(rule)?.get(prop)

    private fun Map<String, Map<String, Map<String, Any>>>.list(
        section: String,
        rule: String,
        prop: String,
    ): List<String> = (value(section, rule, prop) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private fun Map<String, Map<String, Map<String, Any>>>.entryValues(
        section: String,
        rule: String,
        prop: String,
    ): List<String> =
        list(section, rule, prop)
            .filterNot { it.startsWith("reason:") }
            .map { if (it.startsWith("value:")) it.substringAfter("value:").trim() else it.trim() }
}

private const val FOUR_SPACES = 4

private fun loadConfig(path: String): Map<String, Map<String, Map<String, Any>>> {
    val reader = ConfigReader()
    for (raw in Paths.get(path).toFile().readLines()) {
        reader.feed(raw)
    }
    reader.flush()
    return reader.sections
}

private class ConfigReader {
    val sections = mutableMapOf<String, MutableMap<String, MutableMap<String, Any>>>()
    private var section = ""
    private var rule = ""
    private var listKey = ""
    private val pending = mutableListOf<String>()

    fun flush() {
        if (listKey.isNotEmpty() && pending.isNotEmpty()) {
            sections[section]?.get(rule)?.put(listKey, pending.toList())
            pending.clear()
        }
    }

    fun feed(raw: String) {
        if (raw.isBlank() || raw.trimStart().startsWith("#")) return
        val indent = raw.takeWhile { it == ' ' }.length
        val text = raw.trim()
        if (indent == 0 && text.endsWith(":")) {
            openSection(text.dropLast(1))
        } else if (indent == 2 && text.endsWith(":")) {
            openRule(text.dropLast(1))
        } else if (indent == 4 && rule.isNotEmpty()) {
            openProperty(text)
        } else {
            feedListLine(indent, text)
        }
    }

    private fun feedListLine(
        indent: Int,
        text: String,
    ) {
        if (listKey.isEmpty()) {
            flush()
            return
        }
        if (text.startsWith("- ")) {
            pending.add(text.drop(2).trim())
        } else if (isContinuation(indent, text)) {
            pending.add(text)
        } else {
            flush()
            listKey = ""
        }
    }

    private fun isContinuation(
        indent: Int,
        text: String,
    ): Boolean = indent > FOUR_SPACES && pending.isNotEmpty() && text.contains(":")

    private fun openSection(name: String) {
        flush()
        section = name
        sections.getOrPut(section) { mutableMapOf() }
        rule = ""
        listKey = ""
    }

    private fun openRule(name: String) {
        flush()
        rule = name
        sections[section]?.getOrPut(rule) { mutableMapOf() }
        listKey = ""
    }

    private fun openProperty(text: String) {
        flush()
        val key = text.substringBefore(":").trim()
        val rest = text.substringAfter(":", "").trim()
        if (rest.isEmpty()) {
            listKey = key
        } else {
            listKey = ""
            sections[section]?.get(rule)?.put(key, rest)
        }
    }
}
