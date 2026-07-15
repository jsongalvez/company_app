package com.companyb.companyapp.util

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private fun formatNow(): String = dateFormatter.format(Instant.now())

actual fun logDebug(
    tag: String,
    message: String,
) {
    Log.d(tag, message)
}

actual fun logInfo(
    tag: String,
    message: String,
) {
    Log.i(tag, message)
}

actual fun logWarn(
    tag: String,
    message: String,
) {
    Log.w(tag, message)
}

actual fun logError(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
    } else {
        Log.e(tag, message)
    }
}

actual fun currentTimestamp(): String = formatNow()
