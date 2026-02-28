package com.companyb.companyapp

import com.companyb.companyapp.database.DatabaseConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin

private val logger = KotlinLogging.logger {}

fun initializeJavalin() {
    logger.info { "[Server] [Startup] Application starting" }

    Javalin
        .create {}
        .start(7070)

    logger.info { "[Server] [Ready] Application started" }
}

fun initializeHikariCP() {
    logger.info { "[INITIALIZE-HIKARI-CP] Starting HikariCP connection" }
    DatabaseConfig.dataSource
    logger.info { "[INITIALIZE-HIKARI-CP] HikariCP connection enabled" }
}

fun main() {
    initializeHikariCP()
    initializeJavalin()
}
