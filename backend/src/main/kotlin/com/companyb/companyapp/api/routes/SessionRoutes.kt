package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionStatus
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
}
