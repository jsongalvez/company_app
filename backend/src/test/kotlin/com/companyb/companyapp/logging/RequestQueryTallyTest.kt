package com.companyb.companyapp.logging

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestQueryTallyTest {
    private data class Key(
        val name: String,
    ) : RequestQueryTally.ReadKey

    private class Value(
        val n: Int,
    )

    @AfterTest
    fun end() = RequestQueryTally.endRequest()

    @Test
    fun `a request lists the tables its writes name, and none for reads`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("SELECT * FROM branch WHERE id = ?", 1)
        RequestQueryTally.record("INSERT INTO \"session\" (id, branch_id) VALUES (?, ?)", 1)
        RequestQueryTally.record("UPDATE app_user SET status=? WHERE id = ?", 1)
        RequestQueryTally.record("DELETE FROM relief_day WHERE id = ?", 1)
        assertEquals(setOf("session", "app_user", "relief_day"), RequestQueryTally.changed())
    }

    @Test
    fun `a write whose text names no table is listed as unnamed`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("(batch)", 1)
        RequestQueryTally.record("WITH gone AS (DELETE FROM shift RETURNING id) SELECT count(*) FROM gone", 1)
        assertEquals(setOf(RequestQueryTally.UNNAMED_WRITE), RequestQueryTally.changed())
    }

    @Test
    fun `a SET changes a setting, not a table`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("SET LOCAL pg_trgm.similarity_threshold = 0.2", 1)
        assertEquals(emptySet(), RequestQueryTally.changed())
    }

    @Test
    fun `outside a request nothing is listed`() {
        RequestQueryTally.record("UPDATE app_user SET status=?", 1)
        assertEquals(emptySet(), RequestQueryTally.changed())
    }

    @Test
    fun `deleting a report also changes the triage row its foreign key cascades to`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("DELETE FROM user_feedback WHERE id = ?", 1)
        RequestQueryTally.record("DELETE FROM agent_feedback WHERE id = ?", 1)
        assertEquals(setOf("user_feedback", "agent_feedback", "feedback_triage"), RequestQueryTally.changed())
    }

    @Test
    fun `an update of a report changes only the report`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("UPDATE user_feedback SET status=? WHERE id = ?", 1)
        assertEquals(setOf("user_feedback"), RequestQueryTally.changed())
    }

    @Test
    fun `a request lists the tables its statements read`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record(
            "SELECT branch.id, \"session\".id FROM branch INNER JOIN branch_day bd ON bd.branch_id = branch.id " +
                "LEFT JOIN \"session\" ON \"session\".branch_day_id = bd.id WHERE branch.name = 'from x' " +
                "AND EXTRACT(YEAR FROM bd.date) = ? AND bd.note IS DISTINCT FROM ?",
            1,
        )
        RequestQueryTally.record("SELECT a.id FROM app_user a, user_role r WHERE r.user_id = a.id", 1)
        RequestQueryTally.record("SELECT 1", 1)
        RequestQueryTally.record("SET LOCAL pg_trgm.similarity_threshold = 0.2", 1)
        assertEquals(setOf("branch", "branch_day", "session", "app_user", "user_role"), RequestQueryTally.reads())
    }

    @Test
    fun `a read of a view lists the tables under it`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("SELECT * FROM daily_sales_summary WHERE branch_day_id = ?", 1)
        assertEquals(
            setOf(
                "branch_day",
                "session",
                "session_void",
                "compensation",
                "expense",
                "product_sale",
                "commission_split",
            ),
            RequestQueryTally.reads(),
        )
    }

    @Test
    fun `a write reads its own table and the tables in its subqueries`() {
        RequestQueryTally.startRequest()
        RequestQueryTally.record("UPDATE app_user SET status=? WHERE id IN (SELECT user_id FROM user_role)", 1)
        RequestQueryTally.record("WITH gone AS (DELETE FROM shift RETURNING id) SELECT count(*) FROM gone", 1)
        assertEquals(setOf("app_user", "user_role", "shift"), RequestQueryTally.reads())
    }

    @Test
    fun `a read whose tables the text does not name is listed as unnamed`() {
        for (sql in listOf("(batch)", "SELECT * FROM unnest(?)", "CALL refresh_totals()")) {
            RequestQueryTally.startRequest()
            RequestQueryTally.record(sql, 1)
            assertEquals(setOf(RequestQueryTally.UNNAMED_READ), RequestQueryTally.reads(), sql)
        }
    }

    @Test
    fun `a kept read lasts until a write to a table it read`() {
        RequestQueryTally.startRequest()
        var loads = 0

        fun read() = RequestQueryTally.untilWrite(Key("me"), Value::class, setOf("app_user")) { Value(++loads) }.n
        read()
        RequestQueryTally.record("UPDATE branch SET name=?", 1)
        assertEquals(1, read(), "a write to another table keeps the read")
        RequestQueryTally.record("UPDATE app_user SET status=?", 1)
        assertEquals(2, read(), "a write to a table it read drops it")
        RequestQueryTally.record("(batch)", 1)
        assertEquals(3, read(), "an unnamed write drops it")
    }
}
