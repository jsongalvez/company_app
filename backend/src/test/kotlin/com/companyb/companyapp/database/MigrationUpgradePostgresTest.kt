package com.companyb.companyapp.database

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.test.DatabaseTestHelper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationUpgradePostgresTest {
    @Test
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun `V22 backfills revocation boundaries and auth rejects old token after restart`() {
        val config = AppConfig.parse()
        DatabaseTestHelper.ensureDatabase()
        val schema = "jwt_upgrade_${UUID.randomUUID().toString().replace('-', '_')}"
        val dataSource = createDataSource(config, schema)
        val existingDataSource = DatabaseTestHelper.requireTestDataSource()
        val tokenUserId = UUID.randomUUID()
        val oldUserId = UUID.randomUUID()
        val nullDateUserId = UUID.randomUUID()

        try {
            JwtService.init(config)
            Flyway
                .configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .initSql("SET search_path TO \"$schema\", public")
                .target("21")
                .load()
                .migrate()

            val oldToken = JwtService.generateToken(tokenUserId.toString())
            val oldBoundary = Instant.now()
            val futureBoundary = Instant.now().plus(2, ChronoUnit.HOURS)
            dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO app_user (id, username, password_hash, email, display_name)
                        VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        insertUser(statement, tokenUserId, "token-user", 1)
                        insertUser(statement, oldUserId, "old-user", 6)
                        insertUser(statement, nullDateUserId, "null-date-user", 11)
                        statement.executeUpdate()
                    }
                connection
                    .prepareStatement(
                        """
                        UPDATE app_user
                        SET status = 'INACTIVE', deactivated_at = ?
                        WHERE id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setTimestamp(1, Timestamp.from(oldBoundary))
                        statement.setObject(2, tokenUserId)
                        statement.addBatch()
                        statement.setTimestamp(1, Timestamp.from(futureBoundary))
                        statement.setObject(2, oldUserId)
                        statement.addBatch()
                        statement.setObject(1, null)
                        statement.setObject(2, nullDateUserId)
                        statement.addBatch()
                        statement.executeBatch()
                    }
            }

            Flyway
                .configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .initSql("SET search_path TO \"$schema\", public")
                .load()
                .migrate()

            dataSource.connection.use { connection ->
                connection
                    .prepareStatement("SELECT jwt_revoked_at FROM app_user WHERE id = ?")
                    .use { statement ->
                        statement.setObject(1, tokenUserId)
                        statement.executeQuery().use { result ->
                            assertCloseTo(oldBoundary, result.singleInstant())
                        }
                        statement.setObject(1, oldUserId)
                        statement.executeQuery().use { result ->
                            assertCloseTo(futureBoundary, result.singleInstant())
                        }
                        statement.setObject(1, nullDateUserId)
                        statement.executeQuery().use { result ->
                            assertNotNull(result.singleInstant())
                        }
                    }
                connection
                    .prepareStatement(
                        "UPDATE app_user SET status = 'ACTIVE', deactivated_at = NULL WHERE id IN (?, ?, ?)",
                    ).use { statement ->
                        statement.setObject(1, tokenUserId)
                        statement.setObject(2, oldUserId)
                        statement.setObject(3, nullDateUserId)
                        statement.executeUpdate()
                    }
            }

            Database.connect(dataSource)
            JwtService.init(config)
            DenyList.clear()
            DenyList.loadPersistedRevocations()

            assertNull(JwtService.verifyToken(oldToken))
            assertEquals(tokenUserId.toString(), UserRepository.findByUsername("token-user")?.id)
        } finally {
            DenyList.clear()
            Database.connect(existingDataSource)
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP SCHEMA \"$schema\" CASCADE")
                }
            }
            dataSource.close()
        }
    }

    private fun createDataSource(
        config: AppConfig,
        schema: String,
    ): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                addDataSourceProperty("user", config.dbUser)
                addDataSourceProperty("password", config.dbPassword)
                addDataSourceProperty("databaseName", config.dbName)
                addDataSourceProperty("serverName", config.dbHost)
                addDataSourceProperty("portNumber", config.dbPort)
                addDataSourceProperty("currentSchema", schema)
                maximumPoolSize = 2
                minimumIdle = 1
            },
        )

    private fun insertUser(
        statement: java.sql.PreparedStatement,
        id: UUID,
        username: String,
        index: Int,
    ) {
        statement.setObject(index, id)
        statement.setString(index + 1, username)
        statement.setString(index + 2, "test-password-hash")
        statement.setString(index + 3, "$username@example.test")
        statement.setString(index + 4, username)
    }

    private fun java.sql.ResultSet.singleInstant(): Instant {
        assertEquals(true, next())
        return getTimestamp(1).toInstant()
    }

    private fun assertCloseTo(
        expected: Instant,
        actual: Instant,
    ) {
        assertTrue(
            Duration.between(expected, actual).abs() <= Duration.ofNanos(1_000),
            "expected $expected but was $actual",
        )
    }
}
