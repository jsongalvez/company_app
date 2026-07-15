package com.companyb.companyapp.util

expect fun logDebug(
    tag: String,
    message: String,
)

expect fun logInfo(
    tag: String,
    message: String,
)

expect fun logWarn(
    tag: String,
    message: String,
)

expect fun logError(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)

expect fun currentTimestamp(): String
