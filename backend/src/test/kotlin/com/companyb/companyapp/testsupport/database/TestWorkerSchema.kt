package com.companyb.companyapp.testsupport.database

import com.companyb.companyapp.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID

internal object TestWorkerSchema {
    private const val SCHEMA_PREFIX = "test_w_"
    private const val SCHEMA_MAX_LENGTH = 63
    private const val RANDOM_SUFFIX_LENGTH = 12
    private const val EXTENSION_LOCK_KEY = "company_app_test_extensions"
    private const val MAX_POOL_SIZE = 3
    private const val MIN_IDLE = 3
    private const val CONNECTION_TIMEOUT_MS = 30_000L
    private const val SEED_ROLE = "role"
    private const val SEED_CAPABILITY = "capability"
    private const val SEED_ROLE_CAPABILITY = "role_capability"
    private const val FLYWAY_HISTORY = "flyway_schema_history"

    private val ownedSchemaPattern = Regex("^test_w_[a-z0-9_]+$")
    private val dbNamePattern = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")

    data class Handle(
        val dataSource: HikariDataSource,
        val schema: String,
        val dbName: String,
    )

    fun resolveDbName(config: AppConfig): String = System.getenv("TEST_DB_NAME") ?: "${config.dbName}_test"

    fun requireTestDatabase(
        dbName: String,
        appDbName: String,
    ) {
        if (dbName.isBlank()) {
            error("Refusing to run backend tests against a blank database name")
        }
        if (!dbNamePattern.matches(dbName)) {
            error("Refusing to run backend tests against invalid database name '$dbName'")
        }
        if (dbName == appDbName) {
            error("Refusing to run backend tests against the application database '$appDbName'")
        }
    }

    fun isOwned(name: String?): Boolean {
        val valid =
            !name.isNullOrBlank() &&
                name != "public" &&
                name.startsWith(SCHEMA_PREFIX) &&
                name.length <= SCHEMA_MAX_LENGTH &&
                ownedSchemaPattern.matches(name)
        return valid
    }

    fun requireOwned(name: String?) {
        if (!isOwned(name)) {
            error("Refusing to manage unowned schema '$name' — expected '$SCHEMA_PREFIX<pid>_<rand>'")
        }
    }

    fun generate(): String {
        val pid = ProcessHandle.current().pid()
        val rand =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(RANDOM_SUFFIX_LENGTH)
                .lowercase()
        return "$SCHEMA_PREFIX${pid}_$rand"
    }

    fun jdbcUrl(
        config: AppConfig,
        dbName: String,
    ): String = "jdbc:postgresql://${config.dbHost}:${config.dbPort}/$dbName"

    fun createDataSource(
        config: AppConfig,
        dbName: String,
        schema: String,
    ): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                addDataSourceProperty("user", config.dbUser)
                addDataSourceProperty("password", config.dbPassword)
                addDataSourceProperty("databaseName", dbName)
                addDataSourceProperty("serverName", config.dbHost)
                addDataSourceProperty("portNumber", config.dbPort)
                addDataSourceProperty("currentSchema", "$schema, public")
                connectionInitSql = "SET search_path TO \"$schema\", public"
                maximumPoolSize = MAX_POOL_SIZE
                minimumIdle = MIN_IDLE
                connectionTimeout = CONNECTION_TIMEOUT_MS
            },
        )

    fun ensureExtensions(
        config: AppConfig,
        dbName: String,
    ) {
        val url = jdbcUrl(config, dbName)
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
                stmt.execute("SELECT pg_advisory_xact_lock(hashtext('$EXTENSION_LOCK_KEY'))")
                stmt.execute("CREATE EXTENSION IF NOT EXISTS btree_gist SCHEMA public")
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public")
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements SCHEMA public")
            }
            val placements = readExtensionPlacements(conn)
            verifyPlacements(placements)
            conn.commit()
        }
    }

    private fun readExtensionPlacements(conn: java.sql.Connection): Map<String, String> {
        val placements = mutableMapOf<String, String>()
        conn.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT extname, n.nspname FROM pg_extension e " +
                        "JOIN pg_namespace n ON n.oid = e.extnamespace " +
                        "WHERE extname IN ('btree_gist', 'pg_trgm', 'pg_stat_statements')",
                )
            while (rs.next()) {
                placements[rs.getString(1)] = rs.getString(2)
            }
        }
        return placements
    }

    private fun verifyPlacements(placements: Map<String, String>) {
        for (ext in listOf("btree_gist", "pg_trgm", "pg_stat_statements")) {
            val schema = placements[ext]
            if (schema != "public") {
                error("Extension '$ext' lives in schema '$schema', expected 'public' — refusing to relocate")
            }
        }
    }

    private fun failOnSchemaCollision(
        config: AppConfig,
        dbName: String,
        schema: String,
    ) {
        val url = jdbcUrl(config, dbName)
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = '$schema'")
                if (rs.next()) {
                    error("Worker schema '$schema' already exists — refusing to share tables (retry)")
                }
            }
        }
    }

    fun drop(
        config: AppConfig,
        dbName: String,
        schema: String,
    ) {
        requireOwned(schema)
        val url = jdbcUrl(config, dbName)
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP SCHEMA IF EXISTS \"$schema\" CASCADE")
            }
        }
    }

    /**
     * #494 — truncates every mutable table in the owned worker [schema] in one statement.
     * Discovers BASE TABLEs via database metadata, preserves seed reference rows
     * (role/capability/role_capability) and Flyway history, and uses RESTRICT so an
     * unexpected FK from outside the owned schema fails instead of wiping external data.
     * TRUNCATE never fires the snapshot ON DELETE trigger, so no trigger toggle is needed.
     */
    fun reset(
        schema: String,
        dataSource: HikariDataSource,
    ) {
        requireOwned(schema)
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val tables = discoverMutableTables(conn, schema)
                if (tables.isEmpty()) {
                    error("Refusing to reset worker schema '$schema' — no mutable tables discovered")
                }
                truncateTables(conn, schema, tables)
                conn.commit()
            } catch (failure: Exception) {
                runCatching { conn.rollback() }
                throw failure
            }
        }
    }

    private fun discoverMutableTables(
        conn: java.sql.Connection,
        schema: String,
    ): List<String> {
        conn.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT tablename FROM pg_tables " +
                        "WHERE schemaname = '$schema' " +
                        "AND tablename NOT IN " +
                        "('$SEED_ROLE', '$SEED_CAPABILITY', " +
                        "'$SEED_ROLE_CAPABILITY', '$FLYWAY_HISTORY') " +
                        "AND EXISTS (SELECT 1 FROM information_schema.tables t2 " +
                        "WHERE t2.table_schema = pg_tables.schemaname " +
                        "AND t2.table_name = pg_tables.tablename " +
                        "AND t2.table_type = 'BASE TABLE') " +
                        "ORDER BY tablename",
                )
            val tables = mutableListOf<String>()
            while (rs.next()) {
                tables.add(rs.getString(1))
            }
            return tables.sorted()
        }
    }

    private fun truncateTables(
        conn: java.sql.Connection,
        schema: String,
        tables: List<String>,
    ) {
        val quoted =
            tables.joinToString(", ") { table ->
                "\"$schema\".\"${table.replace("\"", "\"\"")}\""
            }
        conn.createStatement().use { stmt ->
            stmt.execute("TRUNCATE TABLE $quoted RESTRICT")
        }
    }

    fun provision(config: AppConfig): Handle {
        val dbName = resolveDbName(config)
        requireTestDatabase(dbName, config.dbName)
        val schema = generate()
        requireOwned(schema)
        ensureExtensions(config, dbName)
        failOnSchemaCollision(config, dbName, schema)
        val ds = createDataSource(config, dbName, schema)
        try {
            Flyway
                .configure()
                .dataSource(ds)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        } catch (failure: Exception) {
            runCatching { ds.close() }
            runCatching { drop(config, dbName, schema) }
            throw failure
        }
        return Handle(ds, schema, dbName)
    }
}
