package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuppressRequiresTicketTest {
    private val rule = SuppressRequiresTicket(Config.empty)

    @Test
    fun `bare Suppress on declaration fails`() {
        val findings = rule.lint("@Suppress(\"ThrowsCount\")\nfun overloaded() {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `ticket on same line passes`() {
        val findings = rule.lint("@Suppress(\"ThrowsCount\") // #465\nfun overloaded() {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `ticket on preceding line passes`() {
        val findings = rule.lint("// justification, ref #466\n@Suppress(\"ThrowsCount\")\nfun overloaded() {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `bare file Suppress fails`() {
        val findings = rule.lint("@file:Suppress(\"UNCHECKED_CAST\")\npackage foo")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `ticketed file Suppress passes`() {
        val findings = rule.lint("@file:Suppress(\"UNCHECKED_CAST\") // #465\npackage foo")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `bare multi-id Suppress reports every id`() {
        val findings = rule.lint("@Suppress(\"ThrowsCount\", \"MagicNumber\")\nfun overloaded() {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
        assertTrue(findings.single().message.contains("ThrowsCount"))
        assertTrue(findings.single().message.contains("MagicNumber"))
    }

    @Test
    fun `compiler-id Suppress without ticket fails`() {
        val findings = rule.lint("@Suppress(\"UNUSED_PARAMETER\")\nfun ignored(unused: Int) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `kdoc ticket on preceding line passes`() {
        val findings = rule.lint("/** Store op (#323). */\n@Suppress(\"ThrowsCount\")\nfun overloaded() {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `empty grandfather covers nothing`() {
        // #600 burn-down complete (8/8): the grandfather file holds no entries, so every
        // bare @Suppress needs a #<ticket> — nothing is covered by path anymore.
        val path = "backend/src/main/kotlin/com/companyb/companyapp/session/SessionRepository.kt"
        assertEquals(listOf("ThrowsCount"), rule.uncoveredIds(path, listOf("ThrowsCount")))
    }

    @Test
    fun `same file new id is not covered`() {
        val path = "backend/src/main/kotlin/com/companyb/companyapp/session/SessionRepository.kt"
        assertEquals(
            listOf("ThrowsCount", "MagicNumber"),
            rule.uncoveredIds(path, listOf("ThrowsCount", "MagicNumber")),
        )
    }

    @Test
    fun `unknown file is not covered`() {
        assertEquals(listOf("ThrowsCount"), rule.uncoveredIds("other/Foo.kt", listOf("ThrowsCount")))
    }
}
