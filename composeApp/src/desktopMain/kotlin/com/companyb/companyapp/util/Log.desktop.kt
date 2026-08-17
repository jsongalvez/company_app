package com.companyb.companyapp.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val loggers = ConcurrentHashMap<String, Logger>()

private fun getLogger(tag: String): Logger = loggers.getOrPut(tag) { LoggerFactory.getLogger(tag) }

actual fun logDebug(
    tag: String,
    message: String,
) {
    val logger = getLogger(tag)
    if (logger.isDebugEnabled) logger.debug("{}", message)
}

actual fun logInfo(
    tag: String,
    message: String,
) {
    val logger = getLogger(tag)
    if (logger.isInfoEnabled) logger.info("{}", message)
}

actual fun logWarn(
    tag: String,
    message: String,
) {
    val logger = getLogger(tag)
    if (logger.isWarnEnabled) logger.warn("{}", message)
}

actual fun logError(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val logger = getLogger(tag)
    if (throwable != null) {
        logger.error(message, throwable)
    } else {
        logger.error("{}", message)
    }
}
