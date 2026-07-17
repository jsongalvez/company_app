package com.companyb.companyapp.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private val loggers = ConcurrentHashMap<String, Logger>()

private fun logger(tag: String): Logger = loggers.getOrPut(tag) { LoggerFactory.getLogger(tag) }

private fun formatNow(): String = dateFormatter.format(Instant.now())

actual fun logDebug(
    tag: String,
    message: String,
) {
    val l = logger(tag)
    if (l.isDebugEnabled) l.debug("{}", message)
}

actual fun logInfo(
    tag: String,
    message: String,
) {
    val l = logger(tag)
    if (l.isInfoEnabled) l.info("{}", message)
}

actual fun logWarn(
    tag: String,
    message: String,
) {
    val l = logger(tag)
    if (l.isWarnEnabled) l.warn("{}", message)
}

actual fun logError(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val l = logger(tag)
    if (throwable != null) {
        l.error(message, throwable)
    } else {
        l.error("{}", message)
    }
}

actual fun currentTimestamp(): String = formatNow()
