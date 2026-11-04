package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class FindNeverWritesArchitectureTest {
    @Test
    fun `find functions never write a row or create a day`() {
        val offenders =
            BackendArchitectureOwners.discover().flatMap { (path, source) ->
                findFunctionsThatWrite(BackendArchitectureOwners.parseKt(source, path)).map { "$path: $it" }
            }
        assertEquals(
            emptyList(),
            offenders,
            "a find* function only reads; name it resolveOrCreate/record/... if it writes",
        )
    }

    @Test
    fun `fixture - a find that inserts or resolves-or-creates is caught`() {
        val source =
            """
            object Days {
                fun findDay(id: Int) = Table.insert { }
                fun findToday(b: Int) = BranchDayService.resolveOrCreate(b)
                fun findClean(id: Int) = Table.selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("findDay calls insert", "findToday calls resolveOrCreate"),
            findFunctionsThatWrite(BackendArchitectureOwners.parseKt(source)),
        )
    }

    private fun findFunctionsThatWrite(file: KtFile): List<String> =
        PsiTreeUtil
            .findChildrenOfType(file, KtNamedFunction::class.java)
            .filter { it.name?.startsWith("find") == true }
            .flatMap { fn ->
                PsiTreeUtil
                    .findChildrenOfType(fn, KtCallExpression::class.java)
                    .mapNotNull { it.calleeExpression?.text }
                    .filter { it in WRITES || it.endsWith("OrCreate") }
                    .distinct()
                    .map { "${fn.name} calls $it" }
            }

    private companion object {
        val WRITES =
            setOf(
                "insert",
                "insertIgnore",
                "insertAndGetId",
                "batchInsert",
                "upsert",
                "update",
                "deleteWhere",
                "deleteAll",
                "getToday",
            )
    }
}
