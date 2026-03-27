package com.companyb.companyapp.logging

private val UUID_REGEX = Regex("^[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}$")

fun String.maskUUID(): String =
    this.replace(UUID_REGEX) { match ->
        val uuid = match.value
        "${uuid.take(8)}-****-****-****-${uuid.takeLast(12)}"
    }
