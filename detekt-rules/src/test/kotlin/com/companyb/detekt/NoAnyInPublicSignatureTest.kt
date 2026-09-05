package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoAnyInPublicSignatureTest {
    private val rule = NoAnyInPublicSignature(Config.empty)

    @Test
    fun `public fun with Any param fails`() {
        val findings = rule.lint("fun foo(x: Any) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with Any return fails`() {
        val findings = rule.lint("fun foo(): Any {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with nullable Any param fails`() {
        val findings = rule.lint("fun foo(x: Any?) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with String param passes`() {
        val findings = rule.lint("fun foo(x: String) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `internal fun with Any param passes`() {
        val findings = rule.lint("internal fun foo(x: Any) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `private fun with Any param passes`() {
        val findings = rule.lint("private fun foo(x: Any) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `interface method with Any param fails`() {
        val findings = rule.lint("interface Foo { fun bar(x: Any) }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `interface method with String param passes`() {
        val findings = rule.lint("interface Foo { fun bar(x: String) }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `override fun with Any param passes - signature dictated by supertype`() {
        val findings =
            rule.lint(
                "open class B { open fun foo(x: Any) {} }\nclass C : B() { override fun foo(x: Any) {} }",
            )
        assertEquals(1, findings.size, "only the base declaration should fire, got $findings")
        val firedLine =
            findings
                .single()
                .entity.location.source.line
        assertEquals(1, firedLine)
    }

    @Test
    fun `nested Any in generic is not caught in first tranche`() {
        val findings = rule.lint("fun foo(x: List<Any>) {}")
        assertTrue(findings.isEmpty(), "expected no findings for nested Any, got $findings")
    }

    @Test
    fun `typealias to Any is caught`() {
        val code = "typealias MyAny = Any\nfun foo(x: MyAny) {}"
        val findings = rule.lint(code)
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public property in interface with Any type fails`() {
        val findings = rule.lint("interface Foo { val x: Any }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `interface property with String type passes`() {
        val findings = rule.lint("interface Foo { val x: String }")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }
}
