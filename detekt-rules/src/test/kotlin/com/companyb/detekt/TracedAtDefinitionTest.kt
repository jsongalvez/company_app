package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracedAtDefinitionTest {
    private val rule = TracedAtDefinition(Config.empty)

    @Test
    fun `an unspanned function that opens a transaction fails`() {
        val findings = rule.lint("object Repo { fun load(): Int = transaction { 1 } }")
        assertEquals(1, findings.size, "got $findings")
        assertTrue("traced(\"Repo.load\")" in findings.single().message)
    }

    @Test
    fun `an unspanned function that sends an HTTP request fails`() {
        val code = "class Sender { fun post() { client.send(request, HttpResponse.BodyHandlers.discarding()) } }"
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `a function wrapped in traced with its own name passes`() {
        val code =
            """
            object Repo {
                fun load(): Int = traced("Repo.load") { transaction { 1 } }
                fun save(x: Int) { traced("Repo.save") { transaction { x } } }
                fun find(x: Int): Int { return traced("Repo.find") { transaction { x } } }
                companion object { fun top() = traced("Repo.top") { transaction { 2 } } }
            }
            fun loose() = traced("Test.loose") { transaction { 3 } }
            """.trimIndent()
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "got $findings")
    }

    @Test
    fun `a span named after another function fails`() {
        val findings = rule.lint("object Repo { fun load(): Int = traced(\"Repo.other\") { transaction { 1 } } }")
        assertEquals(1, findings.size)
    }

    @Test
    fun `guard clauses may stand before the traced call`() {
        val code =
            """
            object Repo {
                fun load(ids: List<Int>, x: Int?): Int {
                    if (ids.isEmpty()) return 0
                    val y = x ?: throw IllegalStateException()
                    require(y > 0)
                    return traced("Repo.load") { transaction { y } }
                }
            }
            """.trimIndent()
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "got $findings")
    }

    @Test
    fun `a body that does more than the traced call fails`() {
        val code = "object Repo { fun load(): Int { val a = 1; return traced(\"Repo.load\") { transaction { a } } } }"
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `a route handler lambda needs no span of its own`() {
        val code =
            """
            object Routes {
                fun register(config: JavalinConfig) {
                    config.get("/x", Gate.SignedIn("why")) { ctx -> transaction { ctx } }
                }
            }
            """.trimIndent()
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "got $findings")
    }

    @Test
    fun `one finding per function however many calls it makes`() {
        val code = "object Repo { fun two() { transaction { 1 }; transaction { 2 } } }"
        assertEquals(1, rule.lint(code).size)
    }
}
