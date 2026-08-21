package com.companyb.companyapp.config

import io.github.cdimascio.dotenv.dotenv

data class AppConfig(
    val appHost: String,
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
) {
    companion object {
        fun parse(): AppConfig {
            val env =
                dotenv {
                    ignoreIfMissing = true
                }
            return AppConfig(
                appHost = env["APP_HOST"] ?: "localhost",
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
            )
        }
    }
}
