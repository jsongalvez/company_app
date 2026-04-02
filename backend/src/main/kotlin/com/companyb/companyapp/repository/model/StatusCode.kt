package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table

data class StatusCode(
    val code: String,
    val label: String,
    val detail: String,
)

object StatusCodeTable : Table("status_code") {
    val code = varchar("code", 64).uniqueIndex()
    val label = varchar("label", 64)
    val detail = text("detail")

    override val primaryKey = PrimaryKey(code)
}

object StatusCodes {
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val USERNAME_TAKEN = "USERNAME_TAKEN"
}
