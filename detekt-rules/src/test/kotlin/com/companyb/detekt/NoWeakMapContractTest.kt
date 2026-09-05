package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoWeakMapContractTest {
    private val rule = NoWeakMapContract(Config.empty)

    @Test
    fun `public fun with Map String Any param fails`() {
        val findings = rule.lint("fun foo(x: Map<String, Any>) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with Map String Any return fails`() {
        val findings = rule.lint("fun foo(): Map<String, Any?> {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with MutableMap String Any param fails`() {
        val findings = rule.lint("fun foo(x: MutableMap<String, Any>) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with MutableMap String Any nullable fails`() {
        val findings = rule.lint("fun foo(x: MutableMap<String, Any?>) {}")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `public fun with Map String String passes`() {
        val findings = rule.lint("fun foo(x: Map<String, String>) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `public fun with Map Int Any passes non-string key`() {
        val findings = rule.lint("fun foo(x: Map<Int, Any>) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `internal fun with Map String Any passes`() {
        val findings = rule.lint("internal fun foo(x: Map<String, Any>) {}")
        assertTrue(findings.isEmpty(), "expected no findings, got $findings")
    }

    @Test
    fun `interface method with Map String Any nullable fails`() {
        val findings = rule.lint("interface Foo { fun bar(): Map<String, Any?> }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }

    @Test
    fun `override fun with Map String Any passes - signature dictated by supertype`() {
        val findings =
            rule.lint(
                "open class B { open fun foo(): Map<String, Any> = emptyMap() }\n" +
                    "class C : B() { override fun foo(): Map<String, Any> = emptyMap() }",
            )
        assertEquals(1, findings.size, "only the base declaration should fire, got $findings")
        val firedLine =
            findings
                .single()
                .entity.location.source.line
        assertEquals(1, firedLine)
    }

    @Test
    fun `nested map in generic is not caught in first tranche`() {
        val findings = rule.lint("fun foo(): List<Map<String, Any>> {}")
        assertTrue(findings.isEmpty(), "expected no findings for nested map, got $findings")
    }

    @Test
    fun `public property in interface with Map String Any fails`() {
        val findings = rule.lint("interface Foo { val x: Map<String, Any> }")
        assertEquals(1, findings.size, "expected one finding, got $findings")
    }
}
