package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.CreateReliefInviteRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.repository.model.ReliefInviteView
import com.companyb.companyapp.service.ReliefInviteService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

/**
 * Relief invite flow (#159/#160): branch-initiated, assignment-gated inviter surface
 * (`/api/branches/{branchId}/relief-*`) + bearer-gated invitee surface
 * (`/api/relief-invites`). All gates are service-level — the inviter gate is the active
 * `user_branch_assignment` (a membership record, not a capability), so no
 * [com.companyb.companyapp.authorization.CapabilityFilter] before-filters apply (and the
 * 2-segment `/api/branches` MANAGE_USERS filter does not fire on these 4-segment paths —
 * Javalin 7 segment matching).
 */
@OpenApi(
    path = ApiRoutes.BRANCH_RELIEF_CANDIDATES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(
            name = "q",
            type = String::class,
            required = false,
        ), OpenApiParam(name = "date", type = String::class, required = true),
    ],
    operationId = "relief_candidates",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefCandidateResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_RELIEF_INVITES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_relief_invites_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefInviteResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_RELIEF_INVITES_ACCEPTED_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_relief_invites_accepted_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefInviteResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_RELIEF_INVITES_BY_DATE_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "date", type = String::class, required = true)],
    operationId = "branch_relief_invites_by_date_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefInviteResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_RELIEF_INVITES_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_relief_invites_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateReliefInviteRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ReliefInviteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_INVITES,
    methods = [HttpMethod.GET],
    operationId = "relief_invites",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefInviteResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_INVITE_ACCEPT_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "inviteId", type = UUID::class, required = true)],
    operationId = "relief_invite_accept",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefInviteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_INVITE_DECLINE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "inviteId", type = UUID::class, required = true)],
    operationId = "relief_invite_decline",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefInviteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_INVITE_RETRACT_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "inviteId", type = UUID::class, required = true)],
    operationId = "relief_invite_retract",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefInviteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_INVITE_REVOKE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "inviteId", type = UUID::class, required = true)],
    operationId = "relief_invite_revoke",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefInviteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ReliefInviteRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val INVITE_ID_PARAM = "inviteId"

    @Suppress("LongMethod", "ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.BRANCH_RELIEF_INVITES_PATH) { context ->
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

        config.routes.get(ApiRoutes.BRANCH_RELIEF_INVITES_PATH) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listSent(callerId, branchId).map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.BRANCH_RELIEF_INVITES_ACCEPTED_PATH) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listBranchAccepted(callerId, branchId).map { it.toResponse() })
        }

        // #401 — deep-link day read: the notification tap's (branchId, date) pair, every
        // invite status at that day. Audience scoping lives in the service.
        config.routes.get(ApiRoutes.BRANCH_RELIEF_INVITES_BY_DATE_PATH) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val date = parseRequiredDate(context.queryParam("date"), "date")

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listForDay(callerId, branchId, date).map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.BRANCH_RELIEF_CANDIDATES_PATH) { context ->
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

        config.routes.get(ApiRoutes.RELIEF_INVITES) { context ->
            val callerId = context.callerUuid()

            context.status(HttpStatus.OK)
            context.json(ReliefInviteService.listReceived(callerId).map { it.toResponse() })
        }

        config.routes.post(ApiRoutes.RELIEF_INVITE_ACCEPT_PATH) { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.acceptInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.post(ApiRoutes.RELIEF_INVITE_DECLINE_PATH) { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.declineInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.post(ApiRoutes.RELIEF_INVITE_RETRACT_PATH) { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.retractInvite(callerId, inviteId)
            context.status(HttpStatus.OK)
            context.json(
                ReliefInviteService.viewFor(invite)?.toResponse()
                    ?: throw BadRequestResponse("Invite not found"),
            )
        }

        config.routes.post(ApiRoutes.RELIEF_INVITE_REVOKE_PATH) { context ->
            val callerId = context.callerUuid()
            val inviteId = context.pathParamAsUuid(INVITE_ID_PARAM)

            val invite = ReliefInviteService.revokeInvite(callerId, inviteId)
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
