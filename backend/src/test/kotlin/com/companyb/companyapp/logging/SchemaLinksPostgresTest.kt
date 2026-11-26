package com.companyb.companyapp.logging

import com.companyb.companyapp.testsupport.database.BasePostgresTest
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

/** [SchemaLinks] says what the migrated schema says: the tables under each view, and where a delete cascades. */
class SchemaLinksPostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private fun rows(sql: String): List<Pair<String, String>> =
        transaction {
            exec(sql) { rs ->
                generateSequence { if (rs.next()) rs.getString(1) to rs.getString(2) else null }.toList()
            }.orEmpty()
        }

    private fun closure(edges: List<Pair<String, String>>): Map<String, Set<String>> {
        val direct = edges.groupBy({ it.first }, { it.second })

        fun reach(from: String): Set<String> = direct[from].orEmpty().flatMap { setOf(it) + reach(it) }.toSet()
        return direct.keys.associateWith(::reach)
    }

    @Test
    fun `each view lists the tables it reads`() {
        val edges =
            rows(
                """
                SELECT DISTINCT v.relname, t.relname
                FROM pg_depend d
                JOIN pg_rewrite r ON r.oid = d.objid
                JOIN pg_class v ON v.oid = r.ev_class
                JOIN pg_class t ON t.oid = d.refobjid
                WHERE d.classid = 'pg_rewrite'::regclass AND d.refclassid = 'pg_class'::regclass
                  AND v.relkind IN ('v', 'm') AND t.oid <> v.oid
                  AND v.relnamespace = current_schema()::regnamespace
                """.trimIndent(),
            )
        val views = edges.map { it.first }.toSet()
        val tables = closure(edges).mapValues { (_, under) -> under - views }
        assertEquals(tables, SchemaLinks.VIEW_TABLES, "list each view's tables in SchemaLinks.VIEW_TABLES")
    }

    @Test
    fun `each delete that changes rows in another table is listed`() {
        val edges =
            rows(
                """
                SELECT DISTINCT p.relname, c.relname
                FROM pg_constraint k
                JOIN pg_class c ON c.oid = k.conrelid
                JOIN pg_class p ON p.oid = k.confrelid
                WHERE k.contype = 'f' AND k.confdeltype IN ('c', 'n', 'd')
                  AND k.connamespace = current_schema()::regnamespace
                """.trimIndent(),
            )
        assertEquals(closure(edges), SchemaLinks.DELETE_CASCADES, "list the table in SchemaLinks.DELETE_CASCADES")
    }

    @Test
    fun `no update cascades to another table`() {
        val edges =
            rows(
                """
                SELECT p.relname, c.relname
                FROM pg_constraint k
                JOIN pg_class c ON c.oid = k.conrelid
                JOIN pg_class p ON p.oid = k.confrelid
                WHERE k.contype = 'f' AND k.confupdtype IN ('c', 'n', 'd')
                  AND k.connamespace = current_schema()::regnamespace
                """.trimIndent(),
            )
        assertEquals(emptyList(), edges, "RequestQueryTally counts cascades on delete only")
    }
}
