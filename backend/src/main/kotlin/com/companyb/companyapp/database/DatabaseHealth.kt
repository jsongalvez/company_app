package com.companyb.companyapp.database

import com.companyb.companyapp.observability.traced
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Liveness probe for the database connection. The Exposed/transaction knowledge of the
 * health check lives here in the database layer, keeping `api/routes` free of persistence
 * concerns (enforced by SemanticOwnershipArchitectureTest).
 */
internal object DatabaseHealth {
    fun isReachable(): Boolean =
        traced("DatabaseHealth.isReachable") {
            runCatching {
                transaction {
                    exec("SELECT 1")
                }
            }.isSuccess
        }
}
