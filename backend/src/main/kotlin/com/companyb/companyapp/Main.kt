package com.companyb.companyapp

import com.companyb.companyapp.database.DatabaseConfig
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin

private val logger = KotlinLogging.logger {}

val dotenv = dotenv()

fun initializeJavalin() {
    logger.info { "[INITIALIZE-JAVALIN] Starting application" }

    Javalin
        .create {}
        .start(dotenv["APP_PORT"].toInt())

    logger.info { "[INITIALIZE-JAVALIN] Application started" }
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
