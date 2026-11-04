package com.companyb.companyapp.observability

import com.companyb.companyapp.testsupport.database.BasePostgresTest
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedbackTriagePostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private val user = "0b6e2a1c-5d4f-4e3a-9b8c-7d6e5f4a3b2c"
    private val report = "1c2d3e4f-0000-4000-8000-000000000001"
    private val agentReport = "1c2d3e4f-0000-4000-8000-000000000002"

    private fun sql(statement: String) = transaction { exec(statement) }

    private fun count(query: String): Int =
        transaction {
            exec(query) { rs ->
                rs.next()
                rs.getInt(1)
            }
        } ?: -1

    private fun seedReports() {
        sql(
            "INSERT INTO app_user (id, username, password_hash, status, display_name, email) " +
                "VALUES ('$user', 'triage-u', 'x', 'ACTIVE', 'Triage User', 'triage@example.test')",
        )
        sql(
            "INSERT INTO user_feedback (id, user_id, kind, message) " +
                "VALUES ('$report', '$user', 'PROBLEM', 'Got 400 error')",
        )
        sql("INSERT INTO agent_feedback (id, agent, message) VALUES ('$agentReport', 'Worker', 'verify.sh up hangs')")
    }

    private fun saveVerdict(
        column: String,
        id: String,
    ) = sql(
        "INSERT INTO feedback_triage ($column, verdict, reason, classifier) " +
            "VALUES ('$id', 'AUTO_FIX', 'describes a defect', 'rules') ON CONFLICT DO NOTHING",
    )

    @Test
    fun `a report keeps its first verdict when triage runs again`() {
        seedReports()
        repeat(2) { saveVerdict("feedback_id", report) }
        repeat(2) { saveVerdict("agent_feedback_id", agentReport) }
        assertEquals(2, count("SELECT count(*) FROM feedback_triage"))
    }

    @Test
    fun `a verdict names exactly one report`() {
        seedReports()
        assertFailsWith<Exception> {
            sql("INSERT INTO feedback_triage (verdict, reason, classifier) VALUES ('AUTO_FIX', 'r', 'rules')")
        }
        assertFailsWith<Exception> {
            sql(
                "INSERT INTO feedback_triage (feedback_id, agent_feedback_id, verdict, reason, classifier) " +
                    "VALUES ('$report', '$agentReport', 'AUTO_FIX', 'r', 'rules')",
            )
        }
    }

    @Test
    fun `only the three verdicts are stored`() {
        seedReports()
        assertFailsWith<Exception> {
            sql(
                "INSERT INTO feedback_triage (feedback_id, verdict, reason, classifier) " +
                    "VALUES ('$report', 'DONE', 'r', 'rules')",
            )
        }
    }

    @Test
    fun `triage leaves the report's status alone and goes with the report`() {
        seedReports()
        saveVerdict("feedback_id", report)
        assertEquals(1, count("SELECT count(*) FROM user_feedback WHERE status = 'NEW'"))
        sql("DELETE FROM user_feedback WHERE id = '$report'")
        assertEquals(0, count("SELECT count(*) FROM feedback_triage WHERE feedback_id IS NOT NULL"))
    }
}
