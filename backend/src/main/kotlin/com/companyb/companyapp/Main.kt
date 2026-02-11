package com.companyb.companyapp

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

fun main() {
    initializeJavalin()
}
