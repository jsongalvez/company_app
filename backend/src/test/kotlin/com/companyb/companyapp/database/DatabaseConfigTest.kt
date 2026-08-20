package com.companyb.companyapp.database

import com.companyb.companyapp.config.AppConfig
import kotlin.test.Test
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
        val config = AppConfig.parse().copy(dbName = "${AppConfig.parse().dbName}_test")
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
}
