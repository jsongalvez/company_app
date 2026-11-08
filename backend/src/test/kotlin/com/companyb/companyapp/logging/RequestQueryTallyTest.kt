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
