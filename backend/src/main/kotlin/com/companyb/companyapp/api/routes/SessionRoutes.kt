package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.service.ConcernService
import com.companyb.companyapp.service.SessionService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object SessionRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/sessions") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateSessionRequest>()

            val sessionId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val clientId =
                runCatching { UUID.fromString(request.clientId) }
                    .getOrElse { throw BadRequestResponse("Invalid client id") }
            val branchId =
                runCatching { UUID.fromString(request.branchId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val practitionerId =
                request.requestedPractitionerId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid practitioner id") }
                }
            val finalPrice =
                runCatching { BigDecimal(request.finalPrice) }
                    .getOrElse { throw BadRequestResponse("Invalid final price") }
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

            if (finalPrice < BigDecimal.ZERO) {
                throw BadRequestResponse("finalPrice must be non-negative")
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

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.session.toResponse())
        }

        config.routes.patch("/api/sessions/{sessionId}/status") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<UpdateSessionStatusRequest>()

            val newStatus =
                runCatching { SessionStatus.valueOf(request.status.uppercase()) }
                    .getOrElse { throw BadRequestResponse("Invalid session status: ${request.status}") }

            val updated = SessionService.updateStatus(callerId, sessionId, newStatus, request.version)

            context.status(HttpStatus.OK)
            context.json(updated.toResponse())
        }

        config.routes.post("/api/sessions/{sessionId}/void") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<VoidSessionRequest>()

            val voidId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid void id") }
            if (request.voidReason.isBlank()) {
                throw BadRequestResponse("voidReason must not be blank")
            }

            val result = SessionService.voidSession(callerId, sessionId, voidId, request.voidReason)

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.sessionVoid.toResponse())
        }

        config.routes.post("/api/sessions/{sessionId}/unvoid") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<UnvoidSessionRequest>()

            if (request.unvoidedReason.isBlank()) {
                throw BadRequestResponse("unvoidedReason must not be blank")
            }

            val sessionVoid = SessionService.unvoidSession(callerId, sessionId, request.unvoidedReason)

            context.status(HttpStatus.OK)
            context.json(sessionVoid.toResponse())
        }

        config.routes.post("/api/sessions/{sessionId}/practitioners") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<AddPractitionerRequest>()

            val practitionerId =
                runCatching { UUID.fromString(request.practitionerId) }
                    .getOrElse { throw BadRequestResponse("Invalid practitioner id") }
            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid id") }

            val result =
                SessionService.addPractitioner(
                    callerId = callerId,
                    id = id,
                    sessionId = sessionId,
                    practitionerId = practitionerId,
                    remarks = request.remarks,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.practitioner.toResponse())
        }

        config.routes.patch("/api/sessions/{sessionId}/practitioners/{practitionerId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val practitionerId =
                runCatching { UUID.fromString(context.pathParam("practitionerId")) }
                    .getOrElse { throw BadRequestResponse("Invalid practitioner id") }
            val request = context.bodyAsClass<UpdatePractitionerRemarksRequest>()

            val updated =
                SessionService.updatePractitionerRemarks(
                    callerId = callerId,
                    sessionId = sessionId,
                    practitionerId = practitionerId,
                    remarks = request.remarks,
                )

            context.status(HttpStatus.OK)
            context.json(updated.toResponse())
        }

        config.routes.delete("/api/sessions/{sessionId}/practitioners/{practitionerId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val practitionerId =
                runCatching { UUID.fromString(context.pathParam("practitionerId")) }
                    .getOrElse { throw BadRequestResponse("Invalid practitioner id") }

            SessionService.removePractitioner(
                callerId = callerId,
                sessionId = sessionId,
                practitionerId = practitionerId,
            )

            context.status(HttpStatus.NO_CONTENT)
        }

        config.routes.get("/api/concerns") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val concerns = ConcernService.listAll(callerId)
            context.json(concerns.map { it.toResponse() })
        }

        config.routes.get("/api/sessions/{sessionId}/concerns") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }

            val concerns = ConcernService.getForSession(callerId, sessionId)
            context.json(concerns.map { it.toResponse() })
        }

        config.routes.post("/api/sessions/{sessionId}/concerns") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<AddSessionConcernRequest>()

            val concernId =
                runCatching { UUID.fromString(request.concernId) }
                    .getOrElse { throw BadRequestResponse("Invalid concern id") }

            ConcernService.addToSession(callerId, sessionId, concernId)
            context.status(HttpStatus.NO_CONTENT)
        }

        config.routes.delete("/api/sessions/{sessionId}/concerns/{concernId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val concernId =
                runCatching { UUID.fromString(context.pathParam("concernId")) }
                    .getOrElse { throw BadRequestResponse("Invalid concern id") }

            ConcernService.removeFromSession(callerId, sessionId, concernId)
            context.status(HttpStatus.NO_CONTENT)
        }

        config.routes.post("/api/sessions/{sessionId}/promote-concern") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val sessionId =
                runCatching { UUID.fromString(context.pathParam("sessionId")) }
                    .getOrElse { throw BadRequestResponse("Invalid session id") }
            val request = context.bodyAsClass<PromoteConcernRequest>()

            if (request.label.isBlank()) {
                throw BadRequestResponse("label must not be blank")
            }

            val concern = ConcernService.promoteConcern(callerId, sessionId, request.label)
            context.status(HttpStatus.CREATED)
            context.json(concern.toResponse())
        }
    }

    private fun Session.toResponse(): SessionResponse =
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
