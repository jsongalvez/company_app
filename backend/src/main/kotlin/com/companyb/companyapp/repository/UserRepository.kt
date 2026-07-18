package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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

    fun isEmailTaken(email: String): Boolean =
        transaction {
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.email eq email }
                .empty()
                .not()
        }.also {
            if (it) {
                logger.info { "[IS-EMAIL-TAKEN] Email is taken" }
            }
        }

    fun createUser(
        username: String,
        passwordHash: String,
        email: String,
        displayName: String,
    ): UUID =
        transaction {
            AppUserTable
                .insert {
                    it[AppUserTable.username] = username
                    it[AppUserTable.passwordHash] = passwordHash
                    it[AppUserTable.email] = email
                    it[AppUserTable.displayName] = displayName
                } get AppUserTable.id
        }.also { logger.info { "[CREATE-USER] Added user to ${AppUserTable.tableName} table" } }

    fun findInactiveUserIds(): List<UUID> =
        transaction {
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.status eq UserStatus.INACTIVE }
                .map { it[AppUserTable.id] }
        }.also { logger.info { "[FIND-INACTIVE-USER-IDS] Fetched ${it.size} inactive user(s)" } }

    fun deactivate(
        userId: UUID,
        auditFn: (String, AppUser) -> Unit = { _, _ -> },
    ): AppUser? =
        transaction {
            val current =
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.id eq userId }
                    .singleOrNull()
            if (current == null) {
                null
            } else {
                val oldStatus = current[AppUserTable.status].name
                AppUserTable.update({ AppUserTable.id eq userId }) {
                    it[status] = UserStatus.INACTIVE
                }
                val deactivated =
                    AppUser(
                        id = current[AppUserTable.id].toString(),
                        username = current[AppUserTable.username],
                        passwordHash = current[AppUserTable.passwordHash],
                    )
                auditFn(oldStatus, deactivated)
                deactivated
            }
        }.also { updated ->
            logger.info { "[DEACTIVATE] User ${userId.toString().maskUUID()} deactivated=${updated != null}" }
        }

    fun authorize(id: String): Boolean {
        // safe: id is non-null String; caller guards null before calling
        val userId = UUID.fromString(id)
        val authorized =
            transaction {
                AppUserTable
                    .select(AppUserTable.id)
                    .where { (AppUserTable.id eq userId) and (AppUserTable.status eq UserStatus.ACTIVE) }
                    .empty()
                    .not()
            }
        if (authorized) {
            logger.info { "[AUTHORIZE] User ${id.maskUUID()} is authorized" }
        } else {
            logger.warn { "[AUTHORIZE] User ${id.maskUUID()} is not authorized" }
        }
        return authorized
    }
}
