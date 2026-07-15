package com.companyb.companyapp.util

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private fun formatNow(): String = dateFormatter.format(Instant.now())

actual fun logDebug(
    tag: String,
    message: String,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (logger.isDebugEnabled) logger.debug("{}", message)
}

actual fun logInfo(
    tag: String,
    message: String,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (logger.isInfoEnabled) logger.info("{}", message)
}

actual fun logWarn(
    tag: String,
    message: String,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (logger.isWarnEnabled) logger.warn("{}", message)
}

actual fun logError(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (throwable != null) {
        logger.error(message, throwable)
    } else {
        logger.error("{}", message)
    }
}

actual fun currentTimestamp(): String = formatNow()
