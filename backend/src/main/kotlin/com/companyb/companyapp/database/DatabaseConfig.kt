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

    private var appConfig: AppConfig? = null

    val dataSource: HikariDataSource by lazy {
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
        HikariDataSource(config)
    }

    fun initialize(config: AppConfig) {
        appConfig = config
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
    }
}
