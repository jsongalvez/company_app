package com.companyb.companyapp.audit

import com.companyb.companyapp.observability.traced
import io.javalin.http.Context
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.util.UUID

/**
 * Saves sent from a phone's queue carry [HEADER], an id the phone made for that one save. The
 * server keeps the answer to each one it took, and answers a resend with the same answer without
 * running it again. So any save may wait on the phone and be sent twice (the first answer was
 * lost to a dropped signal) without being done twice, whatever the route.
 *
 * A refused save is not kept: the person may fix it and send it again under the same id.
 */
object OpLog {
    const val HEADER = "X-Op-Id"
    const val REPLAYED_HEADER = "X-Op-Replayed"
    private const val MAX_ID = 80
    private const val OK_MIN = 200
    private const val OK_MAX = 299

    /** Long past the phone's replay token, so no queued save outlives its answer. */
    private val KEEP: Duration = Duration.ofDays(14)

    private fun opId(context: Context): String? =
        context
            .header(HEADER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_ID && context.method().name != "GET" }

    /** How long a save may run before a resend of it may take over (the server stopped halfway). */
    private val ABANDONED: Duration = Duration.ofMinutes(2)
    private const val CLAIMED = "opClaimed"
    private const val BUSY = 503

    /**
     * Auth-filter step. A save the server already took answers as it did then, and runs no further.
     * A save still running (bad wifi: the phone gave up waiting and sent it again) is told to come
     * back later, so the two copies never run side by side. Otherwise this request claims the id.
     */
    fun answerRepeat(
        context: Context,
        userId: String,
    ) {
        val op = opId(context) ?: return
        val claim =
            traced("OpLog.answerRepeat") {
                transaction { AppliedOpStore.claimInTransaction(UUID.fromString(userId), op, ABANDONED) }
            }
        when (claim) {
            is OpClaim.Claimed -> {
                context.attribute(CLAIMED, true)
                return
            }

            is OpClaim.Kept -> {
                context.status(claim.status).result(claim.body)
            }

            OpClaim.Running -> {
                context.status(BUSY).result("""{"error":"Still saving this. It will send again by itself."}""")
            }
        }
        context.contentType("application/json").header(REPLAYED_HEADER, "1")
        context.skipRemainingHandlers()
    }

    /** After-filter step: keep the answer to a save the server took, or free the id of one it refused. */
    fun keep(context: Context) {
        val op = opId(context) ?: return
        if (context.attribute<Boolean>(CLAIMED) != true) return
        val userId = UUID.fromString(context.attribute<String>("userId") ?: return)
        val status = context.statusCode()
        val body = if (status in OK_MIN..OK_MAX) context.result().orEmpty() else null
        traced("OpLog.keep") {
            transaction {
                if (body != null) {
                    AppliedOpStore.keepInTransaction(userId, op, status, body, KEEP)
                } else {
                    AppliedOpStore.releaseInTransaction(userId, op)
                }
            }
        }
    }
}
