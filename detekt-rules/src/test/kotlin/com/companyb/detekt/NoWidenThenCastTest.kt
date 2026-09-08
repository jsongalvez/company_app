package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoWidenThenCastTest {
    private val rule = NoWidenThenCast(Config.empty)

    @Test
    fun `val widened to Any then as fails`() {
        val findings = rule.lint("fun foo() { val x: Any = \"hi\"\nval y = x as String }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `val widened to nullable Any then as query fails`() {
        val findings = rule.lint("fun foo() { val x: Any? = null\nval y = x as? String }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `narrow val cast passes`() {
        val findings = rule.lint("fun foo() { val x: String = \"hi\"\nval y = x as String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `widened val without cast passes`() {
        val findings = rule.lint("fun foo() { val x: Any = \"hi\"\nprintln(x) }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `var widened to Any then as passes val only`() {
        val findings = rule.lint("fun foo() { var x: Any = \"hi\"\nval y = x as String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `unrelated cast passes`() {
        val findings = rule.lint("fun foo(y: Any) { val x: Any = \"hi\"\nval z = y as String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `nested local fun widen and cast reports once`() {
        val findings =
            rule.lint("fun outer() { fun inner() { val x: Any = \"hi\"\nval y = x as String } }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `outer widened val cast inside nested fun stays out of scope`() {
        val findings = rule.lint("fun outer() { val x: Any = \"hi\"\nfun inner() = x as String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `shadowed lambda param passes`() {
        val findings =
            rule.lint("fun foo() { val x: Any = \"hi\"\nval ys = listOf(\"a\").map { x -> x as String } }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `inner val shadows outer widened passes`() {
        val findings =
            rule.lint("fun foo() { val x: Any = \"hi\"\nrun { val x: String = \"hi\"\nval y = x as String } }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }
}
