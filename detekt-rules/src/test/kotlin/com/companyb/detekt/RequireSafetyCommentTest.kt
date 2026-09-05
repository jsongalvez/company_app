package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequireSafetyCommentTest {
    private val rule = RequireSafetyComment(Config.empty)

    @Test
    fun `bare as fails`() {
        val findings = rule.lint("fun foo(x: Any) = x as String")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `as with preceding SAFETY comment passes`() {
        val findings = rule.lint("// SAFETY: narrowed boundary check #467\nfun foo(x: Any) = x as String")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `as with same-line SAFETY comment passes`() {
        val findings = rule.lint("fun foo(x: Any) = x as String // SAFETY: test #467")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `safe cast passes without comment`() {
        val findings = rule.lint("fun foo(x: Any) = x as? String")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `no cast passes`() {
        val findings = rule.lint("fun foo(x: String) = x.length")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `multiline cast with SAFETY before as passes`() {
        val code = "fun foo(x: Any): String = bar(\nx,\n// SAFETY: test #467\n) as String"
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }
}
