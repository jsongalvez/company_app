package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.bodyIfPresent
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidFromQuery
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.RemovePractitionerRequest
import com.companyb.companyapp.dto.RemoveSessionConcernRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.session.dashboard.DashboardService
import com.companyb.companyapp.session.dashboard.DashboardSessionEnrichment
import com.companyb.companyapp.session.dashboard.mapDashboardSession
import com.companyb.companyapp.session.dashboard.toResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
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

@Suppress("TooManyFunctions")
@OpenApi(
    path = ApiRoutes.CONCERNS,
    methods = [HttpMethod.GET],
    operationId = "concerns",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ConcernResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSIONS,
    methods = [HttpMethod.POST],
    operationId = "sessions",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateSessionRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = SessionResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = DashboardSessionResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERNS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_concerns_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ConcernResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERNS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_concerns_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddSessionConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERN_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "concernId", type = UUID::class, required = true),
    ],
    operationId = "session_concern_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RemoveSessionConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_FINAL_PRICE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_final_price",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateSessionFinalPriceRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONERS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_practitioners",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddPractitionerRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONERS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_practitioners_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<SessionPractitionerResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_SESSION_PREVIEW_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "branch_session_preview",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionPreviewResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONER_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "practitionerId", type = UUID::class, required = true),
    ],
    operationId = "session_practitioner_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdatePractitionerRemarksRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONER_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "practitionerId", type = UUID::class, required = true),
    ],
    operationId = "session_practitioner_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RemovePractitionerRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PROMOTE_CONCERN_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_promote_concern",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = PromoteConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ConcernResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_STATUS_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_status",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateSessionStatusRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_UNVOID_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_unvoid",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UnvoidSessionRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionVoidResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_VOID_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_void",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = VoidSessionRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionVoidResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = SessionVoidResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object SessionRoutes {
    /**
     * Request-scoped attribute: the branch day the create gate resolved (#157). The handler
     * hands it to [SessionService.create] so the gate
     * and the write share one day resolution (no Manila-midnight divergence). Absent when the
     * gate ran the plain branch check (no day row existed — no BRANCH_DAY grant possible).
     */
    private const val GATED_BRANCH_DAY_ATTR = "gatedBranchDayId"

    @Suppress("LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.SESSIONS) { context ->
            if (context.method() != HandlerType.POST) return@before
            val request = context.bodyAsClass<CreateSessionRequest>()
            val branchId = uuidOrThrow(request.branchId, "branch id")
            // Day-scoped (#157) via the shared today gate (#452): a BRANCH_DAY relief
            // grant for today satisfies the create gate. The resolved day is handed to
            // the handler so the gate and the write share one resolution (no
            // midnight-boundary divergence); a 403'd attempt leaves no day row behind.
            val gatedDayId = CapabilityFilter.requireBranchOrDayForBranch(context, branchId)
            if (gatedDayId != null) {
                context.attribute(GATED_BRANCH_DAY_ATTR, gatedDayId)
            }
        }

        config.routes.before(ApiRoutes.SESSION_STATUS_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            val request = context.bodyAsClass<UpdateSessionStatusRequest>()
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
            CapabilityFilter.requireStatusCorrectionCapability(context, sessionId, request.status)
        }

        config.routes.before(ApiRoutes.SESSION_FINAL_PRICE_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before(ApiRoutes.SESSION_VOID_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.VOID_SESSION,
            )
        }

        config.routes.before(ApiRoutes.SESSION_UNVOID_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.VOID_SESSION,
            )
        }

        config.routes.before(ApiRoutes.SESSION_PRACTITIONERS_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/practitioners/{practitionerId}") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before(ApiRoutes.SESSION_CONCERNS_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        // #304 — the DELETE child path needs its own gate: Javalin path filters match
        // exact literals, so the parent /concerns filter never fires for /concerns/{concernId}.
        config.routes.before(ApiRoutes.SESSION_CONCERN_PATH) { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before(ApiRoutes.CONCERNS) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage concerns",
            )
        }

        config.routes.before("/api/sessions/{sessionId}/promote-concern") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.get(ApiRoutes.SESSION_PATH, ::handleGetSession)
        config.routes.post(ApiRoutes.SESSIONS, ::handleCreateSession)
        // #348 — the practitioners list read (add-self refresh); the existing
        // SESSION_PRACTITIONERS_PATH before-filter already gates GET on the same
        // EDIT_BRANCH_DATA branch-or-branch-day check as the mutations.
        config.routes.get(ApiRoutes.SESSION_PRACTITIONERS_PATH, ::handleGetPractitioners)
        config.routes.patch(ApiRoutes.SESSION_STATUS_PATH, ::handleUpdateStatus)
        config.routes.patch(ApiRoutes.SESSION_FINAL_PRICE_PATH, ::handleUpdateFinalPrice)
        config.routes.post(ApiRoutes.SESSION_VOID_PATH, ::handleVoidSession)
        config.routes.post(ApiRoutes.SESSION_UNVOID_PATH, ::handleUnvoidSession)
        config.routes.post(ApiRoutes.SESSION_PRACTITIONERS_PATH, ::handleAddPractitioner)
        config.routes.patch(
            "/api/sessions/{sessionId}/practitioners/{practitionerId}",
            ::handleUpdatePractitionerRemarks,
        )
        config.routes.delete(
            "/api/sessions/{sessionId}/practitioners/{practitionerId}",
            ::handleRemovePractitioner,
        )
        config.routes.get(ApiRoutes.CONCERNS, ::handleGetConcerns)
        config.routes.get(ApiRoutes.SESSION_CONCERNS_PATH, ::handleGetSessionConcerns)
        config.routes.post(ApiRoutes.SESSION_CONCERNS_PATH, ::handleAddSessionConcern)
        config.routes.delete(ApiRoutes.SESSION_CONCERN_PATH, ::handleRemoveSessionConcern)
        config.routes.post(ApiRoutes.SESSION_PROMOTE_CONCERN_PATH, ::handlePromoteConcern)
        config.routes.before(ApiRoutes.BRANCH_SESSION_PREVIEW_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            // Same gate as the create it previews (#452): the preview passes exactly
            // when the create would.
            CapabilityFilter.requireBranchOrDayForBranch(context, branchId)
        }
        config.routes.get(ApiRoutes.BRANCH_SESSION_PREVIEW_PATH, ::handleGetSessionPreview)
    }

    private fun handleGetPractitioners(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")

        val practitioners = SessionPractitionerService.getForSession(callerId, sessionId)
        context.json(practitioners.map { it.toResponse() })
    }

    private fun handleGetSessionPreview(context: Context) {
        val branchId = context.pathParamAsUuid("branchId")
        val clientId = context.uuidFromQuery("clientId")

        val preview = SessionService.previewSession(branchId, clientId)
        context.json(
            SessionPreviewResponse(
                sessionType = preview.sessionType,
                basePrice = preview.basePrice.toPlainString(),
            ),
        )
    }

    private fun handleGetSession(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")

        // #152 — notifications-path session detail (#151): bearer-only gate (the notification
        // row IS the authorization), no capability/day-state filters, 404 for both non-bearer
        // and missing sessions. Byte-identical rendering with the dashboard path (Q3).
        val data = DashboardService.getSessionDetail(callerId, sessionId)
        context.json(
            mapDashboardSession(
                session = data.session,
                enrichment =
                    DashboardSessionEnrichment(
                        clientNames = data.clientNames,
                        voidedSessionIds = data.voidedSessionIds,
                        practitionerBySession = data.practitioners.groupBy { it.sessionId },
                        concernsBySession = data.concerns.groupBy { it.sessionId },
                        requestedPractitionerNames = data.requestedPractitionerNames,
                        branchIdBySession = data.branchIdBySession,
                    ),
            ),
        )
    }

    // #509 — replay-path 403 fallback is deliberate handling (empty concerns), not swallowing.
    @Suppress("ThrowsCount", "SwallowedException")
    private fun handleCreateSession(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateSessionRequest>()

        val sessionId = uuidOrThrow(request.id, "session id")
        val clientId = uuidOrThrow(request.clientId, "client id")
        val branchId = uuidOrThrow(request.branchId, "branch id")
        val practitionerId =
            request.requestedPractitionerId?.let { uuidOrThrow(it, "practitioner id") }
        val finalPrice = parseNonNegativeBigDecimal(request.finalPrice, "finalPrice")
        val nextAppt =
            request.nextAppointmentDate?.let {
                runCatching { LocalDate.parse(it) }
                    .getOrElse { throw BadRequestResponse("Invalid nextAppointmentDate format") }
            }

        val result =
            SessionService.create(
                callerId = callerId,
                id = sessionId,
                clientId = clientId,
                branchId = branchId,
                isWalkIn = request.isWalkIn,
                requestedPractitionerId = practitionerId,
                finalPrice = finalPrice,
                remarks = request.remarks,
                otherConcerns = request.otherConcerns,
                nextAppointmentDate = nextAppt,
                gatedBranchDayId = context.attribute(GATED_BRANCH_DAY_ATTR),
                reason = request.reason,
            )

        val concerns =
            if (result.created) {
                SessionConcernService.getForSession(callerId, sessionId).map { it.toResponse() }
            } else {
                // #509 — idempotent replay that lands after a day transition (e.g. the day
                // was remitted between the original create and this retry) already committed;
                // the concern read gates on day readability, so tolerate its 403 and still
                // acknowledge the write with an empty list instead of failing the retry.
                try {
                    SessionConcernService.getForSession(callerId, sessionId).map { it.toResponse() }
                } catch (_: ForbiddenException) {
                    emptyList()
                }
            }
        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.session.toResponse(concerns))
    }

    private fun handleUpdateStatus(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<UpdateSessionStatusRequest>()

        val newStatus = request.status

        val updated = SessionService.updateStatus(callerId, sessionId, newStatus, request.version, request.reason)

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleUpdateFinalPrice(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<UpdateSessionFinalPriceRequest>()

        val newPrice = parseNonNegativeBigDecimal(request.finalPrice, "finalPrice")

        val updated = SessionService.updateFinalPrice(callerId, sessionId, newPrice, request.version, request.reason)

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleVoidSession(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<VoidSessionRequest>()

        if (request.voidReason.isBlank()) throw BadRequestResponse("voidReason must not be blank")
        val voidId = uuidOrThrow(request.id, "void id")

        val result = SessionService.voidSession(callerId, sessionId, voidId, request.voidReason)

        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.sessionVoid.toResponse())
    }

    private fun handleUnvoidSession(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<UnvoidSessionRequest>()

        if (request.unvoidedReason.isBlank()) throw BadRequestResponse("unvoidedReason must not be blank")
        val sessionVoid = SessionService.unvoidSession(callerId, sessionId, request.unvoidedReason)

        context.status(HttpStatus.OK)
        context.json(sessionVoid.toResponse())
    }

    private fun handleAddPractitioner(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<AddPractitionerRequest>()

        val practitionerId = uuidOrThrow(request.practitionerId, "practitioner id")
        val id = uuidOrThrow(request.id, "id")

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = id,
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = request.remarks,
                reason = request.reason,
            )

        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.practitioner.toResponse())
    }

    private fun handleUpdatePractitionerRemarks(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val practitionerId = context.pathParamAsUuid("practitionerId")
        val request = context.bodyAsClass<UpdatePractitionerRemarksRequest>()

        val updated =
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = request.remarks,
                reason = request.reason,
            )

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleRemovePractitioner(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val practitionerId = context.pathParamAsUuid("practitionerId")
        val reason = context.bodyIfPresent<RemovePractitionerRequest>()?.reason

        SessionPractitionerService.removePractitioner(
            callerId = callerId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            reason = reason,
        )

        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleGetConcerns(context: Context) {
        val concerns = ConcernService.listAll()
        context.json(concerns.map { it.toResponse() })
    }

    private fun handleGetSessionConcerns(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")

        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        context.json(concerns.map { it.toResponse() })
    }

    private fun handleAddSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<AddSessionConcernRequest>()

        val concernId = uuidOrThrow(request.concernId, "concern id")

        SessionConcernService.addToSession(callerId, sessionId, concernId, request.reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleRemoveSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val concernId = context.pathParamAsUuid("concernId")
        val reason = context.bodyIfPresent<RemoveSessionConcernRequest>()?.reason

        SessionConcernService.removeFromSession(callerId, sessionId, concernId, reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handlePromoteConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<PromoteConcernRequest>()

        if (request.label.isBlank()) throw BadRequestResponse("label must not be blank")
        val concernId = uuidOrThrow(request.id, "concern id")

        val concern =
            SessionConcernService.promoteConcern(
                callerId,
                sessionId,
                concernId,
                request.label,
                request.reason,
            )
        context.status(HttpStatus.CREATED)
        context.json(concern.toResponse())
    }

    private fun Session.toResponse(concerns: List<ConcernResponse> = emptyList()): SessionResponse =
        SessionResponse(
            id = id.toString(),
            clientId = clientId.toString(),
            branchDayId = branchDayId.toString(),
            requestedPractitionerId = requestedPractitionerId?.toString(),
            sessionType = sessionType,
            isWalkIn = isWalkIn,
            sessionStatus = sessionStatus,
            basePrice = basePrice.toPlainString(),
            finalPrice = finalPrice.toPlainString(),
            remarks = remarks,
            otherConcerns = otherConcerns,
            bookedAt = bookedAt?.toString(),
            nextAppointmentDate = nextAppointmentDate?.toString(),
            version = version,
            concerns = concerns,
        )

    private fun SessionVoid.toResponse(): SessionVoidResponse =
        SessionVoidResponse(
            id = id.toString(),
            sessionId = sessionId.toString(),
            voidedAt = voidedAt.toString(),
            voidedBy = voidedBy.toString(),
            voidReason = voidReason,
            unvoidedAt = unvoidedAt?.toString(),
            unvoidedBy = unvoidedBy?.toString(),
            unvoidedReason = unvoidedReason,
        )

    private fun SessionPractitioner.toResponse(): SessionPractitionerResponse =
        SessionPractitionerResponse(
            id = id.toString(),
            sessionId = sessionId.toString(),
            practitionerId = practitionerId.toString(),
            remarks = remarks,
            slotAtTime = slotAtTime.toInt(),
        )
}
