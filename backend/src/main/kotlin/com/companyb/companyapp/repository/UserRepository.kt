package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger { }

object UserRepository {
    fun findByUsername(username: String): AppUser? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.username eq username }
                .map { row ->
                    AppUser(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        passwordHash = row[AppUserTable.passwordHash],
                    )
                }.singleOrNull()
        }.also { logger.info { "[FIND-BY-USERNAME] Fetched username" } }

    fun createUser(
        username: String,
        passwordHash: String,
    ) {
        transaction {
            AppUserTable
                .insert {
                    it[AppUserTable.username] = username
                    it[AppUserTable.passwordHash] = passwordHash
                }
        }.also { logger.info { "[CREATE-USER] Added user to ${AppUserTable.tableName} table" } }
    }
}
