package com.companyb.companyapp.observability

import com.companyb.companyapp.dto.FeedbackRequest
import com.companyb.companyapp.observability.GithubIssueConfig
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubIssueSenderTest {
    @BeforeTest
    fun reset() {
        IncidentRegistry.resetForTest()
    }

    private fun packet() =
        IncidentService
            .fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("trace-${UUID.randomUUID()}", "GET /api/clients", "9", 1200),
                IncidentSender { },
            ).packet

    @Test
    fun `rendered issue carries the packet with the triage label`() {
        val filed = packet()
        val issue = GithubIssueSender(GithubIssueConfig("token", "owner/repo")).renderIssue(filed)

        assertEquals(listOf("needs-triage"), issue.labels)
        assertTrue(issue.title.startsWith("Incident "))
        assertTrue(issue.title.contains("GET /api/clients"))
        assertTrue(issue.body.contains("traceId: ${filed.traceId}"))
        assertTrue(issue.body.contains("GET /api/clients"))
        assertTrue(issue.body.contains("pool:"))
        assertFalse(issue.body.contains("token"))
    }

    @Test
    fun `rendered body never leaks the raw caller id`() {
        val caller = UUID.randomUUID()
        val filed =
            IncidentService.fileUserReport(
                caller,
                FeedbackRequest("trace-${UUID.randomUUID()}", "GET /api/x", "9", null),
                IncidentSender { },
            )

        val body = GithubIssueSender(GithubIssueConfig("token", "owner/repo")).renderIssue(filed.packet).body

        assertFalse(body.contains(caller.toString()))
        assertTrue(body.contains(filed.packet.reporter))
    }

    @Test
    fun `config parses only complete well-formed pairs`() {
        assertNull(GithubIssueConfig.fromEnvironment(emptyMap()))
        assertNull(
            GithubIssueConfig.fromEnvironment(
                mapOf(
                    GithubIssueConfig.TOKEN_ENV to "token",
                    GithubIssueConfig.REPOSITORY_ENV to "not-a-pair",
                ),
            ),
        )
        val config =
            GithubIssueConfig.fromEnvironment(
                mapOf(
                    GithubIssueConfig.TOKEN_ENV to "token",
                    GithubIssueConfig.REPOSITORY_ENV to "owner/repo",
                ),
            )
        assertEquals("owner/repo", config?.repository)
    }
}
