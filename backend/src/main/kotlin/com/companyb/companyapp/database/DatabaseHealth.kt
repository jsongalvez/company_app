package com.companyb.companyapp.database

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Liveness probe for the database connection (#324). The Exposed/transaction knowledge of the
 * health check lives here in the database layer, keeping `api/routes` free of persistence
 * concerns (enforced by BackendFeatureBoundaryArchitectureTest).
 */
internal object DatabaseHealth {
    fun isReachable(): Boolean =
        runCatching {
            transaction {
                exec("SELECT 1")
            }
        }.isSuccess
}
