package com.companyb.companyapp.testsupport.database

import com.companyb.companyapp.config.AppConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Test-database worker lifecycle for map #533 (#552).
 *
 * Owns worker provisioning, owned-schema guards and per-test reset only.
 * Imports no feature tables, stores or services; scenario fixtures live in
 * `testsupport.fixtures` families and integration barriers in `integration`.
 */
object TestDatabaseLifecycle {
    private var databaseReady = false

    @Volatile
    var testDataSource: HikariDataSource? = null
        private set

    @Volatile
    var workerSchema: String? = null
        private set

    fun ensureDatabase() {
        if (databaseReady) {
            return
        }
        synchronized(this) {
            if (databaseReady) {
                return
            }
            val config = AppConfig.parse()
            val handle = TestWorkerSchema.provision(config)
            org.jetbrains.exposed.v1.jdbc.Database
                .connect(handle.dataSource)
            testDataSource = handle.dataSource
            workerSchema = handle.schema
            databaseReady = true
            registerDisposalHook(config, handle.dbName, handle.schema)
        }
    }

    fun requireWorkerSchema(): String =
        workerSchema
            ?: error("TestDatabaseLifecycle.ensureDatabase() has not been called — workerSchema is null")

    fun isOwnedSchema(name: String?): Boolean = TestWorkerSchema.isOwned(name)

    fun requireOwnedSchema(name: String?) = TestWorkerSchema.requireOwned(name)

    fun requireTestDatabase(
        dbName: String,
        appDbName: String,
    ) = TestWorkerSchema.requireTestDatabase(dbName, appDbName)

    fun generateWorkerSchema(): String = TestWorkerSchema.generate()

    fun workerJdbcUrl(
        config: AppConfig,
        dbName: String,
    ): String = TestWorkerSchema.jdbcUrl(config, dbName)

    private fun registerDisposalHook(
        config: AppConfig,
        dbName: String,
        schema: String,
    ) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { testDataSource?.close() }
                testDataSource = null
                runCatching { TestWorkerSchema.drop(config, dbName, schema) }
            },
        )
    }

    fun isDatabaseReady(): Boolean = databaseReady

    /**
     * Returns the test [HikariDataSource], throwing if [ensureDatabase] has not been called.
     * Prefer this over `testDataSource!!` to get a clear error message on misuse.
     */
    fun requireTestDataSource(): HikariDataSource =
        testDataSource
            ?: error("TestDatabaseLifecycle.ensureDatabase() has not been called — testDataSource is null")

    /**
     * #494 — resets the owned worker schema by truncating every mutable table in one
     * RESTRICT statement (seed reference rows and Flyway history preserved).
     * Requires #493's positively identified owned schema; failure is loud, never silent.
     * Raw TRUNCATE DDL is unavoidable here — Exposed has no truncate API.
     */
    fun resetWorkerSchema() {
        val schema = requireWorkerSchema()
        TestWorkerSchema.reset(schema, requireTestDataSource())
    }
}
