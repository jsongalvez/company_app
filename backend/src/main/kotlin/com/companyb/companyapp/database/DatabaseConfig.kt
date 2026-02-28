package com.companyb.companyapp.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv

val dotenv = dotenv()

object DatabaseConfig {
    val dataSource: HikariDataSource by lazy {
        val config =
            HikariConfig().apply {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                addDataSourceProperty("user", dotenv["POSTGRES_USER"])
                addDataSourceProperty("password", dotenv["POSTGRES_PASSWORD"])
                addDataSourceProperty("databaseName", dotenv["POSTGRES_DB"])

                maximumPoolSize = 3
            }
        HikariDataSource(config)
    }
}
