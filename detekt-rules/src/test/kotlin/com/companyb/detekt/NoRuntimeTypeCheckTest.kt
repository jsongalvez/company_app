package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoRuntimeTypeCheckTest {
    private val rule = NoRuntimeTypeCheck(Config.empty)

    @Test
    fun `is Any fails`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is Any) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `not-is Any fails`() {
        val findings = rule.lint("fun foo(x: Any) { if (x !is Any) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `is nullable Any fails`() {
        val findings = rule.lint("fun foo(x: Any?) { if (x is Any?) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `is Map fails`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is Map<String, String>) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `is star-projected Map fails`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is Map<*, *>) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `is MutableMap fails`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is MutableMap<String, Any>) {} }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `as Any fails`() {
        val findings = rule.lint("fun foo(x: String) { val y = x as Any }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `as-query Any fails`() {
        val findings = rule.lint("fun foo(x: String) { val y = x as? Any }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `instance class literal fails`() {
        val findings = rule.lint("fun foo(x: Any) { val c = x::class }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `this class literal fails`() {
        val findings = rule.lint("class Foo { fun bar() = this::class }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `call-result class literal fails`() {
        val findings = rule.lint("fun foo(): Any = 1\nfun bar() { val c = foo()::class }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `javaClass access fails`() {
        val findings = rule.lint("fun foo(x: Any) { val c = x.javaClass }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `safe javaClass access fails`() {
        val findings = rule.lint("fun foo(x: Any?) { val c = x?.javaClass }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `is String passes`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is String) {} }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `not-is sealed member passes`() {
        val findings =
            rule.lint(
                "sealed interface Ui { data object Loading : Ui }\n" +
                    "fun foo(x: Ui) { if (x !is Ui.Loading) {} }",
            )
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `is List passes out of scope in first tranche`() {
        val findings = rule.lint("fun foo(x: Any) { if (x is List<String>) {} }")
        assertTrue(findings.isEmpty(), "expected no findings for List in first tranche, got $findings")
    }

    @Test
    fun `type class literal passes`() {
        val findings = rule.lint("class Foo\nfun foo() { val c = Foo::class }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `nested type class literal passes`() {
        val findings = rule.lint("fun foo() { val c = Route.Login::class }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `type class java passes`() {
        val findings = rule.lint("fun foo() { val c = Foo::class.java }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `as String passes`() {
        val findings = rule.lint("fun foo(x: Any) { val y = x as String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `as-query String passes`() {
        val findings = rule.lint("fun foo(x: Any) { val y = x as? String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }
}
