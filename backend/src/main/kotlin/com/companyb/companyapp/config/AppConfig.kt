package com.companyb.companyapp.config

import com.companyb.companyapp.identity.SmtpConfig
import io.github.cdimascio.dotenv.dotenv

data class AppConfig(
    val appPort: Int,
    val dbHost: String,
    val dbPort: String,
    val dbName: String,
    val dbUser: String,
    val dbPassword: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val authDummyPassword: String,
    val testUsername: String?,
    val testPassword: String?,
    val scopedTestUsername: String?,
    val scopedTestPassword: String?,
    val reliefTestUsername: String?,
    val reliefTestPassword: String?,
    val smtp: SmtpConfig? = null,
    val githubIssue: GithubIssueConfig? = null,
) {
    companion object {
        fun parse(): AppConfig {
            val env =
                dotenv {
                    ignoreIfMissing = true
                }
            return AppConfig(
                appPort =
                    env["APP_PORT"]?.toIntOrNull()
                        ?: error("APP_PORT must be a valid integer"),
                dbHost = env["DB_HOST"] ?: error("DB_HOST must be set"),
                dbPort = env["DB_PORT"] ?: error("DB_PORT must be set"),
                dbName = env["POSTGRES_DB"] ?: error("POSTGRES_DB must be set"),
                dbUser = env["POSTGRES_USER"] ?: error("POSTGRES_USER must be set"),
                dbPassword = env["POSTGRES_PASSWORD"] ?: error("POSTGRES_PASSWORD must be set"),
                jwtSecret = env["JWT_SECRET"] ?: error("JWT_SECRET must be set"),
                jwtIssuer = env["JWT_ISSUER"] ?: error("JWT_ISSUER must be set"),
                jwtAudience = env["JWT_AUDIENCE"] ?: error("JWT_AUDIENCE must be set"),
                authDummyPassword =
                    env["AUTH_DUMMY_PASSWORD"]
                        ?: error("AUTH_DUMMY_PASSWORD must be set"),
                testUsername = env["TEST_USERNAME"]?.takeIf { it.isNotBlank() },
                testPassword = env["TEST_PASSWORD"]?.takeIf { it.isNotBlank() },
                scopedTestUsername = env["SCOPED_USERNAME"]?.takeIf { it.isNotBlank() },
                scopedTestPassword = env["SCOPED_PASSWORD"]?.takeIf { it.isNotBlank() },
                reliefTestUsername = env["RELIEF_USERNAME"]?.takeIf { it.isNotBlank() },
                reliefTestPassword = env["RELIEF_PASSWORD"]?.takeIf { it.isNotBlank() },
                smtp =
                    SmtpConfig.fromEnvironment(
                        mapOf(
                            SmtpConfig.HOST_ENV to env[SmtpConfig.HOST_ENV],
                            SmtpConfig.PORT_ENV to env[SmtpConfig.PORT_ENV],
                            SmtpConfig.USERNAME_ENV to env[SmtpConfig.USERNAME_ENV],
                            SmtpConfig.PASSWORD_ENV to env[SmtpConfig.PASSWORD_ENV],
                            SmtpConfig.FROM_ENV to env[SmtpConfig.FROM_ENV],
                        ),
                    ),
                githubIssue =
                    GithubIssueConfig.fromEnvironment(
                        mapOf(
                            GithubIssueConfig.TOKEN_ENV to env[GithubIssueConfig.TOKEN_ENV],
                            GithubIssueConfig.REPOSITORY_ENV to env[GithubIssueConfig.REPOSITORY_ENV],
                        ),
                    ),
            )
        }
    }
}
