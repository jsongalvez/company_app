package com.companyb.companyapp.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}
val dotenv = dotenv()

object DatabaseConfig {
    val dataSource: HikariDataSource by lazy {
        val config =
            HikariConfig().apply {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                addDataSourceProperty("user", dotenv["POSTGRES_USER"])
                addDataSourceProperty("password", dotenv["POSTGRES_PASSWORD"])
                addDataSourceProperty("databaseName", dotenv["POSTGRES_DB"])
                addDataSourceProperty("serverName", dotenv["DB_HOST"])
                addDataSourceProperty("portNumber", dotenv["DB_PORT"])

                maximumPoolSize = 3
                minimumIdle = 3
                connectionTimeout = 30_000
            }
        HikariDataSource(config)
    }

    fun runMigrations() {
        logger.info { "[RUN-MIGRATIONS] Starting flyway configuration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(
                "classpath:db/migration",
            ).load()
            .migrate()
        logger.info { "[RUN-MIGRATIONS] Flyway configuration done" }
    }

    fun runExposed() {
        logger.info { "[RUN-EXPOSED] Starting Exposed database connection" }
        Database.connect(dataSource)
        logger.info { "[RUN-EXPOSED] Exposed database connection done" }
    }
}
