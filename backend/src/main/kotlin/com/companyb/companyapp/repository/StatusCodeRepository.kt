package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.StatusCode
import com.companyb.companyapp.repository.model.StatusCodeTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

object StatusCodeRepository {
    val logger = KotlinLogging.logger { }

    fun fetchStatusCode(code: String): StatusCode? =
        transaction {
            StatusCodeTable
                .select(
                    listOf(
                        StatusCodeTable.code,
                        StatusCodeTable.label,
                        StatusCodeTable.detail,
                    ),
                ).where { StatusCodeTable.code eq code }
                .map { row ->
                    StatusCode(
                        code = row[StatusCodeTable.code],
                        label = row[StatusCodeTable.label],
                        detail = row[StatusCodeTable.detail],
                    )
                }.singleOrNull()
        }.also { logger.info { "[FETCH-STATUS-CODE] Fetched status code" } }
}
