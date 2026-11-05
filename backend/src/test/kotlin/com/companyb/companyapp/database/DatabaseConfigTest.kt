package com.companyb.companyapp.database

import com.companyb.companyapp.app.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class DatabaseConfigTest {
    @Test
    fun `close is idempotent`() {
        DatabaseConfig.close()
        DatabaseConfig.close()
    }

    @Test
    fun `recreates datasource after close`() {
        val parsed = AppConfig.parse()
        // Resolve the test database exactly like TestDatabaseLifecycle.ensureDatabase():
        // CI names it via TEST_DB_NAME, local runs fall back to "<db>_test".
        val config = parsed.copy(dbName = System.getenv("TEST_DB_NAME") ?: "${parsed.dbName}_test")
        DatabaseConfig.prepareForTest(config)
        val first = DatabaseConfig.dataSource
        try {
            DatabaseConfig.close()
            assertTrue(first.isClosed)
            val second = DatabaseConfig.dataSource
            assertNotSame(first, second)
        } finally {
            DatabaseConfig.close()
        }
    }

    @Test
    fun `app connections cut a stuck statement and an abandoned transaction`() {
        val parsed = AppConfig.parse()
        DatabaseConfig.prepareForTest(parsed.copy(dbName = System.getenv("TEST_DB_NAME") ?: "${parsed.dbName}_test"))
        try {
            DatabaseConfig.dataSource.connection.use { connection ->
                val shown =
                    listOf("statement_timeout", "idle_in_transaction_session_timeout").map { name ->
                        connection.createStatement().executeQuery("SHOW $name").use { rows ->
                            rows.next()
                            rows.getString(1)
                        }
                    }
                assertEquals(listOf("20s", "1min"), shown)
            }
        } finally {
            DatabaseConfig.close()
        }
    }
}
