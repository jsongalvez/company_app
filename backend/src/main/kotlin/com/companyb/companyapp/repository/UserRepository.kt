package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger { }

object UserRepository {
    private val DEACTIVATE_SQL =
        """
        UPDATE app_user
        SET status = ?::user_status
        WHERE id = ?::uuid
        """.trimIndent()

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
            exec(
                "SELECT id FROM app_user WHERE status = ?::user_status",
                args = listOf(TextColumnType() to UserStatus.INACTIVE.name),
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        add(UUID.fromString(rs.getString("id")))
                    }
                }
            } ?: emptyList()
        }.also { logger.info { "[FIND-INACTIVE-USER-IDS] Fetched ${it.size} inactive user(s)" } }

    /**
     * Deactivates a user (status -> INACTIVE) and writes an UPDATE audit entry atomically.
     * Returns false (no write) when no user with [userId] exists.
     */
    fun deactivate(
        userId: UUID,
        changedBy: UUID,
    ): Boolean =
        transaction {
            val current =
                AppUserTable
                    .select(AppUserTable.status)
                    .where { AppUserTable.id eq userId }
                    .singleOrNull()
            if (current == null) {
                false
            } else {
                val oldStatus = current[AppUserTable.status]
                exec(
                    DEACTIVATE_SQL,
                    args =
                        listOf(
                            TextColumnType() to UserStatus.INACTIVE.name,
                            TextColumnType() to userId.toString(),
                        ),
                )
                AuditLogRepository.record(
                    tableName = AppUserTable.tableName,
                    recordId = userId,
                    action = AuditAction.UPDATE,
                    changedBy = changedBy,
                    oldValue = AuditLogRepository.jsonField("status", oldStatus.name),
                    newValue = AuditLogRepository.jsonField("status", UserStatus.INACTIVE.name),
                )
                true
            }
        }.also { updated -> logger.info { "[DEACTIVATE] User ${userId.toString().maskUUID()} deactivated=$updated" } }

    fun authorize(id: String): Boolean {
        val authorized =
            transaction {
                exec(
                    "SELECT 1 FROM app_user WHERE id = ?::uuid AND status = ?::user_status",
                    args =
                        listOf(
                            TextColumnType() to id,
                            TextColumnType() to UserStatus.ACTIVE.name,
                        ),
                ) { rs -> rs.next() }
            } ?: false
        if (authorized) {
            logger.info { "[AUTHORIZE] User ${id.maskUUID()} is authorized" }
        } else {
            logger.warn { "[AUTHORIZE] User ${id.maskUUID()} is not authorized" }
        }
        return authorized
    }
}
