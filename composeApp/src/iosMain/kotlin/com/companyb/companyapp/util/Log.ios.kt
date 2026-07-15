package com.companyb.companyapp.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private val dateFormatter: NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = "HH:mm:ss.SSS"
    }

private fun formatNow(): String = dateFormatter.stringFromDate(NSDate())

actual fun currentTimestamp(): String = formatNow()

actual fun logDebug(
    tag: String,
    message: String,
) {
    println("[${formatNow()}] [DEBUG] [$tag] $message")
}

actual fun logInfo(
    tag: String,
    message: String,
) {
    println("[${formatNow()}] [INFO] [$tag] $message")
}

actual fun logWarn(
    tag: String,
    message: String,
) {
    println("[${formatNow()}] [WARN] [$tag] $message")
}

actual fun logError(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable != null) {
        println("[${formatNow()}] [ERROR] [$tag] $message")
        throwable.printStackTrace()
    } else {
        println("[${formatNow()}] [ERROR] [$tag] $message")
    }
}
