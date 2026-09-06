package com.companyb.companyapp.observability

/**
 * #475 — optional GitHub issue delivery for incident packets. Absent until the
 * operator sets both values (same opt-in shape as [SmtpConfig]: unconfigured
 * keeps the server-log relay, so no secret is required to run or test).
 */
data class GithubIssueConfig(
    val token: String,
    val repository: String,
) {
    companion object {
        const val TOKEN_ENV = "GITHUB_TOKEN"
        const val REPOSITORY_ENV = "GITHUB_REPOSITORY"

        private val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

        /** Returns null for absent or malformed configuration. */
        fun fromEnvironment(environment: Map<String, String?>): GithubIssueConfig? {
            val token = environment[TOKEN_ENV]?.takeIf { it.isNotBlank() } ?: return null
            val repository = environment[REPOSITORY_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!REPOSITORY_PATTERN.matches(repository)) return null
            return GithubIssueConfig(token, repository)
        }
    }
}
