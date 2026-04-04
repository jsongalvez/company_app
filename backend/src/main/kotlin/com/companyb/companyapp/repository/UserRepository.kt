package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

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
    ): UUID =
        transaction {
            AppUserTable
                .insert {
                    it[AppUserTable.username] = username
                    it[AppUserTable.passwordHash] = passwordHash
                } get AppUserTable.id
        }.also { logger.info { "[CREATE-USER] Added user to ${AppUserTable.tableName} table" } }

    fun authorize(id: String): Boolean =
        transaction {
            AppUserTable
                .selectAll()
                .where { (AppUserTable.id eq UUID.fromString(id)) and (AppUserTable.status eq UserStatus.ACTIVE) }
                .any()
        }.also { isAuthorized ->
            if (isAuthorized) {
                logger.info { "[AUTHORIZE] User ${id.maskUUID()} is authorized" }
            } else {
                logger.warn { "[AUTHORIZE] User ${id.maskUUID()} is not authorized" }
            }
        }
}
