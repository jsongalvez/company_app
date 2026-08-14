package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.RemovePractitionerRequest
import com.companyb.companyapp.dto.RemoveSessionConcernRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.UpdateSessionTypeRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.service.ConcernService
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.dashboard.DashboardService
import com.companyb.companyapp.service.session.SessionService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("TooManyFunctions")
object SessionRoutes {
    @Suppress("LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/sessions") { context ->
            if (context.method() != HandlerType.POST) return@before
            val request = context.bodyAsClass<CreateSessionRequest>()
            val branchId = uuidOrThrow(request.branchId, "branch id")
            // Day-scoped (#157): a BRANCH_DAY relief grant for today satisfies the create gate.
            // Find-only (never creates): a missing day means no BRANCH_DAY grant can exist for
            // it, so the plain branch check covers the no-day case; a 403'd attempt must not
            // leave a day row behind.
            val todayBranchDay = BranchDayService.findToday(branchId)
            if (todayBranchDay != null) {
                CapabilityFilter.requireBranchOrBranchDayCapability(context, todayBranchDay.id)
            } else {
                CapabilityFilter.requireBranchCapabilityForBranchId(
                    context,
                    branchId,
                    CapabilityCodes.EDIT_BRANCH_DATA,
                )
            }
        }

        config.routes.before("/api/sessions/{sessionId}/status") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/type") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/final-price") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/void") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.VOID_SESSION,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/unvoid") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.VOID_SESSION,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/practitioners") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/sessions/{sessionId}/concerns") { context ->
            val sessionId = context.pathParamAsUuid("sessionId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForSession(
                context,
                sessionId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.before("/api/concerns") { context ->
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

        config.routes.get("/api/sessions/{sessionId}", ::handleGetSession)
        config.routes.post("/api/sessions", ::handleCreateSession)
        config.routes.patch("/api/sessions/{sessionId}/status", ::handleUpdateStatus)
        config.routes.patch("/api/sessions/{sessionId}/type", ::handleUpdateType)
        config.routes.patch("/api/sessions/{sessionId}/final-price", ::handleUpdateFinalPrice)
        config.routes.post("/api/sessions/{sessionId}/void", ::handleVoidSession)
        config.routes.post("/api/sessions/{sessionId}/unvoid", ::handleUnvoidSession)
        config.routes.post("/api/sessions/{sessionId}/practitioners", ::handleAddPractitioner)
        config.routes.patch(
            "/api/sessions/{sessionId}/practitioners/{practitionerId}",
            ::handleUpdatePractitionerRemarks,
        )
        config.routes.delete(
            "/api/sessions/{sessionId}/practitioners/{practitionerId}",
            ::handleRemovePractitioner,
        )
        config.routes.get("/api/concerns", ::handleGetConcerns)
        config.routes.get("/api/sessions/{sessionId}/concerns", ::handleGetSessionConcerns)
        config.routes.post("/api/sessions/{sessionId}/concerns", ::handleAddSessionConcern)
        config.routes.delete("/api/sessions/{sessionId}/concerns/{concernId}", ::handleRemoveSessionConcern)
        config.routes.post("/api/sessions/{sessionId}/promote-concern", ::handlePromoteConcern)
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
                clientNames = data.clientNames,
                voidedSessionIds = data.voidedSessionIds,
                practitionerBySession = data.practitioners.groupBy { it.sessionId },
                concernsBySession = data.concerns.groupBy { it.sessionId },
            ),
        )
    }

    @Suppress("ThrowsCount")
    private fun handleCreateSession(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateSessionRequest>()

        val sessionId = uuidOrThrow(request.id, "session id")
        val clientId = uuidOrThrow(request.clientId, "client id")
        val branchId = uuidOrThrow(request.branchId, "branch id")
        val practitionerId =
            request.requestedPractitionerId?.let { uuidOrThrow(it, "practitioner id") }
        val finalPrice = parseNonNegativeBigDecimal(request.finalPrice, "finalPrice")
        val bookedAt =
            request.bookedAt?.let {
                runCatching { OffsetDateTime.parse(it) }
                    .getOrElse { throw BadRequestResponse("Invalid bookedAt format") }
            }
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
                bookedAt = bookedAt,
                nextAppointmentDate = nextAppt,
            )

        val concerns = SessionService.getSessionConcerns(callerId, sessionId).map { it.toResponse() }
        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.session.toResponse(concerns))
    }

    private fun handleUpdateStatus(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<UpdateSessionStatusRequest>()

        val newStatus =
            runCatching { SessionStatus.valueOf(request.status.uppercase()) }
                .getOrElse { throw BadRequestResponse("Invalid session status: ${request.status}") }

        val updated = SessionService.updateStatus(callerId, sessionId, newStatus, request.version, request.reason)

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleUpdateType(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<UpdateSessionTypeRequest>()

        val newType =
            runCatching { SessionType.valueOf(request.sessionType.uppercase()) }
                .getOrElse { throw BadRequestResponse("Invalid session type: ${request.sessionType}") }

        val updated = SessionService.updateType(callerId, sessionId, newType, request.version, request.reason)

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
            SessionService.addPractitioner(
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
            SessionService.updatePractitionerRemarks(
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

        SessionService.removePractitioner(
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

        val concerns = SessionService.getSessionConcerns(callerId, sessionId)
        context.json(concerns.map { it.toResponse() })
    }

    private fun handleAddSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<AddSessionConcernRequest>()

        val concernId = uuidOrThrow(request.concernId, "concern id")

        SessionService.addSessionConcern(callerId, sessionId, concernId, request.reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleRemoveSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val concernId = context.pathParamAsUuid("concernId")
        val reason = context.bodyIfPresent<RemoveSessionConcernRequest>()?.reason

        SessionService.removeSessionConcern(callerId, sessionId, concernId, reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handlePromoteConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<PromoteConcernRequest>()

        if (request.label.isBlank()) throw BadRequestResponse("label must not be blank")
        val concernId = uuidOrThrow(request.id, "concern id")

        val concern = SessionService.promoteConcern(callerId, sessionId, concernId, request.label, request.reason)
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

    private fun Concern.toResponse(): ConcernResponse =
        ConcernResponse(
            id = id.toString(),
            label = label,
            createdBy = createdBy?.toString(),
            createdAt = createdAt?.toString(),
        )
}
