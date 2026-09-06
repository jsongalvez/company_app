package com.companyb.companyapp.seeding

import io.github.cdimascio.dotenv.dotenv

// #568 — fixture-only credentials live here, not in production AppConfig.
// Same env keys and blank-means-absent parsing the old fields used, so k6,
// .env.example, and existing seed behavior are unchanged; the dev entrypoint
// composes this with AppConfig instead of production parsing fixture secrets.
data class DevFixtureConfig(
    val globalUsername: String?,
    val globalPassword: String?,
    val scopedUsername: String?,
    val scopedPassword: String?,
    val reliefUsername: String?,
    val reliefPassword: String?,
) {
    companion object {
        fun parse(): DevFixtureConfig {
            val env =
                dotenv {
                    ignoreIfMissing = true
                }
            return DevFixtureConfig(
                globalUsername = env["TEST_USERNAME"]?.takeIf { it.isNotBlank() },
                globalPassword = env["TEST_PASSWORD"]?.takeIf { it.isNotBlank() },
                scopedUsername = env["SCOPED_USERNAME"]?.takeIf { it.isNotBlank() },
                scopedPassword = env["SCOPED_PASSWORD"]?.takeIf { it.isNotBlank() },
                reliefUsername = env["RELIEF_USERNAME"]?.takeIf { it.isNotBlank() },
                reliefPassword = env["RELIEF_PASSWORD"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
