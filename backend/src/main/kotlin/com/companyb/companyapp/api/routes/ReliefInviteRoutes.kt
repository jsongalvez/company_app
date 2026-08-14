package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.CreateReliefInviteRequest
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.repository.model.ReliefInviteView
import com.companyb.companyapp.service.ReliefInviteService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.time.LocalDate
import java.util.UUID

/**
 * Relief invite flow (#159/#160): branch-initiated, assignment-gated inviter surface
 * (`/api/branches/{branchId}/relief-*`) + bearer-gated invitee surface
 * (`/api/relief-invites`). All gates are service-level — the inviter gate is the active
 * `user_branch_assignment` (a membership record, not a capability), so no
 * [com.companyb.companyapp.api.middleware.CapabilityFilter] before-filters apply (and the
 * 2-segment `/api/branches` MANAGE_USERS filter does not fire on these 4-segment paths —
 * Javalin 7 segment matching).
 */
object ReliefInviteRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val INVITE_ID_PARAM = "inviteId"

    @Suppress("LongMethod", "ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/relief-invites") { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val request = context.bodyAsClass<CreateReliefInviteRequest>()
            val inviteeUserId = uuidOrThrow(request.inviteeUserId, "inviteeUserId")
            val date = parseRequiredDate(request.date, "date")

            val invite = ReliefInviteService.createInvite(callerId, branchId, inviteeUserId, date)

            context.status(HttpStatus.CREATED)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/relief-invites") { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listSent(callerId, branchId).map { it.toResponse() })
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/relief-candidates") { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val query = context.queryParam("q").orEmpty()
            val date = parseRequiredDate(context.queryParam("date"), "date")

            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService
                    .searchCandidates(callerId, branchId, query, date)
                    .map { candidate ->
                        ReliefCandidateResponse(
                            id = candidate.id.toString(),
                            username = candidate.username,
                            displayName = candidate.displayName,
                        )
                    },
            )
        }

        config.routes.get("/api/relief-invites") { context ->
            val callerId = context.callerUuid()

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listReceived(callerId).map { it.toResponse() })
        }

        config.routes.post("/api/relief-invites/{$INVITE_ID_PARAM}/accept") { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.acceptInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.post("/api/relief-invites/{$INVITE_ID_PARAM}/decline") { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.declineInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.post("/api/relief-invites/{$INVITE_ID_PARAM}/retract") { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.retractInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }
    }

    private fun parseRequiredDate(
        raw: String?,
        name: String,
    ): LocalDate {
        val value = raw ?: throw BadRequestResponse("$name is required")
        return runCatching { LocalDate.parse(value) }
            .getOrElse { throw BadRequestResponse("Invalid $name format (expected yyyy-MM-dd)") }
    }

    private fun ReliefInviteView.toResponse(): ReliefInviteResponse =
        ReliefInviteResponse(
            id = invite.id.toString(),
            branchId = branchId.toString(),
            branchName = branchName,
            branchDayId = invite.branchDayId.toString(),
            date = date.toString(),
            invitedBy = invite.invitedBy.toString(),
            inviterName = inviterName,
            invitee = invite.invitee.toString(),
            inviteeName = inviteeName,
            status = invite.status,
            createdAt = invite.createdAt.toString(),
            respondedAt = invite.respondedAt?.toString(),
        )
}
