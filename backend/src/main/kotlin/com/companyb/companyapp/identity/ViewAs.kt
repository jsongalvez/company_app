package com.companyb.companyapp.identity

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.ViewedAsBy
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.ViewAsResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Developer "View as" (feedback 378fdb6d): a SUPERUSER sends [HEADER] with another user's id
 * and every API request then runs as that user, with that user's permissions. Look-only unless
 * [CHANGES_HEADER] is "1": without it the server refuses every write. The header is ignored
 * for anyone who is not a SUPERUSER, so it can never widen what a caller may do.
 *
 * While viewing, [realUser] holds the SUPERUSER's id for the current request so history rows
 * can show who really acted.
 */
object ViewAs {
    const val HEADER = "X-View-As"
    const val CHANGES_HEADER = "X-View-As-Changes"
    const val SUPERUSER_ROLE = "SUPERUSER"
    private val READ_METHODS = setOf("GET", "HEAD", "OPTIONS")

    fun isSuperuser(userId: UUID): Boolean =
        RoleRepository.findRoleNamesByUser(listOf(userId))[userId].orEmpty().contains(SUPERUSER_ROLE)

    /**
     * Auth-filter step: returns the user id the request runs as. Clears the request's
     * [realUser] first, since server threads are reused between requests.
     */
    fun resolve(
        context: Context,
        callerId: String,
    ): String {
        ViewedAsBy.set(null)
        val raw = context.header(HEADER)?.trim().orEmpty()
        // Starting and stopping always run as the superuser themselves.
        if (raw.isEmpty() || context.path().startsWith(ApiRoutes.VIEW_AS)) return callerId
        val caller = UUID.fromString(callerId)
        val target = parseTarget(raw)
        requireAllowed(context, caller)
        ViewedAsBy.set(caller)
        return target.toString()
    }

    private fun parseTarget(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw ValidationException("Invalid $HEADER header")

    private fun requireAllowed(
        context: Context,
        caller: UUID,
    ) {
        if (!isSuperuser(caller)) throw ForbiddenException("Only a superuser can view as someone else")
        if (context.method().name !in READ_METHODS && context.header(CHANGES_HEADER) != "1") {
            throw ForbiddenException("Look-only: turn on \"Allow changes\" in the top bar to do this")
        }
    }

    /** Checks the target can be viewed and records the start in history. */
    fun start(
        callerId: UUID,
        targetId: UUID,
        allowChanges: Boolean,
    ): ViewAsResponse {
        if (!isSuperuser(callerId)) throw ForbiddenException("Only a superuser can view as someone else")
        if (callerId == targetId) throw ValidationException("That's you")
        val response =
            transaction {
                val user = MeRepository.findUser(targetId) ?: throw NotFoundException("User not found")
                if (user.status != UserStatus.ACTIVE) throw ValidationException("That account is not active")
                val roles = RoleRepository.findRoleNamesForUserInTransaction(targetId)
                if (SUPERUSER_ROLE in roles) throw ValidationException("Can't view as another superuser")
                ViewAsAudit.record(callerId, targetId, mapOf("viewAs" to "started", "allowChanges" to "$allowChanges"))
                ViewAsResponse(
                    userId = targetId.toString(),
                    username = user.username,
                    displayName = user.displayName,
                    roles = roles,
                    allowChanges = allowChanges,
                )
            }
        logger.info { "[VIEW-AS] $callerId started viewing as $targetId allowChanges=$allowChanges" }
        return response
    }

    /** Records the end (or a change of the allow-changes switch) in history. */
    fun stop(
        callerId: UUID,
        targetId: UUID,
        event: String,
    ) {
        if (!isSuperuser(callerId)) throw ForbiddenException("Only a superuser can view as someone else")
        transaction { ViewAsAudit.record(callerId, targetId, mapOf("viewAs" to event)) }
        logger.info { "[VIEW-AS] $callerId $event viewing as $targetId" }
    }
}

/** History seam: view-as starts and stops are user-row events by the superuser. */
internal object ViewAsAudit {
    fun record(
        callerId: UUID,
        targetId: UUID,
        fields: Map<String, String?>,
    ) = AuditLog.record(
        tableName = AppUserTable.tableName,
        recordId = targetId,
        action = AuditAction.UPDATE,
        changedBy = callerId,
        newValue = AuditLog.jsonFields(fields),
    )
}
