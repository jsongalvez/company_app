package com.companyb.companyapp.database

import com.companyb.companyapp.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

private val logger = KotlinLogging.logger {}

object DatabaseConfig {
    private const val MAX_POOL_SIZE = 3
    private const val MIN_IDLE = 3
    private const val CONNECTION_TIMEOUT_MS = 30_000L

    data class PoolStats(
        val active: Int,
        val idle: Int,
        val awaiting: Int,
        val total: Int,
    ) {
        companion object {
            fun empty() = PoolStats(active = 0, idle = 0, awaiting = 0, total = 0)
        }
    }

    /**
     * Live HikariCP gauges (#473). Never throws: uninitialized datasource
     * (unit tests) reports zeros so /metrics stays scrapeable.
     */
    fun poolStats(): PoolStats =
        runCatching {
            val bean = dataSource.hikariPoolMXBean ?: return PoolStats.empty()
            PoolStats(
                active = bean.activeConnections,
                idle = bean.idleConnections,
                awaiting = bean.threadsAwaitingConnection,
                total = bean.totalConnections,
            )
        }.getOrDefault(PoolStats.empty())

    private val lock = Any()
    private var appConfig: AppConfig? = null
    private var dataSourceInstance: HikariDataSource? = null

    val dataSource: HikariDataSource
        get() =
            synchronized(lock) {
                dataSourceInstance ?: createDataSource().also { dataSourceInstance = it }
            }

    private fun createDataSource(): HikariDataSource {
        val cfg = appConfig ?: error("DatabaseConfig.initialize() must be called before accessing dataSource")
        val config =
            HikariConfig().apply {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                addDataSourceProperty("user", cfg.dbUser)
                addDataSourceProperty("password", cfg.dbPassword)
                addDataSourceProperty("databaseName", cfg.dbName)
                addDataSourceProperty("serverName", cfg.dbHost)
                addDataSourceProperty("portNumber", cfg.dbPort)

                maximumPoolSize = MAX_POOL_SIZE
                minimumIdle = MIN_IDLE
                connectionTimeout = CONNECTION_TIMEOUT_MS
            }
        return HikariDataSource(config)
    }

    @Suppress("TooGenericExceptionCaught")
    fun initialize(config: AppConfig) {
        synchronized(lock) {
            configure(config)
        }
        try {
            logger.info { "[INITIALIZE-DATABASE] Starting HikariCP connection" }
            dataSource
            logger.info { "[INITIALIZE-DATABASE] HikariCP connection enabled" }

            logger.info { "[INITIALIZE-DATABASE] Starting Flyway initialization" }
            val flyway =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
            flyway.migrate()
            logger.info { "[INITIALIZE-DATABASE] Flyway initialization done" }

            logger.info { "[INITIALIZE-DATABASE] Starting Exposed connection" }
            Database.connect(dataSource)
            logger.info { "[INITIALIZE-DATABASE] Exposed connection enabled" }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    internal fun prepareForTest(config: AppConfig) {
        synchronized(lock) {
            configure(config)
        }
    }

    private fun configure(config: AppConfig) {
        appConfig = config
    }

    fun close() {
        synchronized(lock) {
            dataSourceInstance?.close()
            dataSourceInstance = null
        }
    }
}
