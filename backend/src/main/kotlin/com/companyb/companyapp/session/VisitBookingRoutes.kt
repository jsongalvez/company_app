package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.BookingResponse
import com.companyb.companyapp.contracts.session.CreateBookingRequest
import com.companyb.companyapp.contracts.session.UpdateBookingRequest
import com.companyb.companyapp.dto.ErrorResponse
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
import java.time.LocalTime
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_BOOKINGS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "bookings_create",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateBookingRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BookingResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = BookingResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_BOOKING_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [
        OpenApiParam(name = "branchId", type = UUID::class, required = true),
        OpenApiParam(name = "bookingId", type = UUID::class, required = true),
    ],
    operationId = "bookings_update",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateBookingRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BookingResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CLIENT_BOOKINGS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "bookings_for_client",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BookingResponse>::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object VisitBookingRoutes {
    private const val BRANCH_ID = "branchId"

    fun register(config: JavalinConfig) {
        // Any branch member may book: the same capability that lets them add sessions there.
        // The branch's booked visits are read with the Today screen.
        config.routes.before(ApiRoutes.BRANCH_BOOKINGS_PATH) { context -> requireMember(context) }
        config.routes.before(ApiRoutes.BRANCH_BOOKING_PATH) { context -> requireMember(context) }
        config.routes.before(ApiRoutes.CLIENT_BOOKINGS_PATH) { context ->
            CapabilityFilter.requireAnyContextCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }

        config.routes.post(ApiRoutes.BRANCH_BOOKINGS_PATH) { context ->
            val request = context.bodyAsClass<CreateBookingRequest>()
            val (booking, created) =
                VisitBookingService.create(
                    callerId = context.callerUuid(),
                    id = uuidOrThrow(request.id, "booking id"),
                    branchId = context.pathParamAsUuid(BRANCH_ID),
                    clientId = uuidOrThrow(request.clientId, "client id"),
                    input = input(request.visitDate, request.visitTime, request.note),
                )
            context.status(if (created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(booking)
        }
        config.routes.patch(ApiRoutes.BRANCH_BOOKING_PATH) { context ->
            val request = context.bodyAsClass<UpdateBookingRequest>()
            context.json(
                VisitBookingService.update(
                    callerId = context.callerUuid(),
                    id = context.pathParamAsUuid("bookingId"),
                    branchId = context.pathParamAsUuid(BRANCH_ID),
                    input = input(request.visitDate, request.visitTime, request.note),
                    status = request.status,
                ),
            )
        }
        config.routes.get(ApiRoutes.CLIENT_BOOKINGS_PATH) { context ->
            context.json(VisitBookingService.forClient(context.pathParamAsUuid("clientId")))
        }
    }

    private fun requireMember(context: io.javalin.http.Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID)
        BranchService.findById(branchId)
        CapabilityFilter.requireBranchCapabilityForBranchId(context, branchId, CapabilityCodes.EDIT_BRANCH_DATA)
    }

    private fun input(
        date: String,
        time: String?,
        note: String?,
    ): BookingInput =
        BookingInput(
            visitDate =
                runCatching {
                    LocalDate.parse(
                        date,
                    )
                }.getOrElse { throw BadRequestResponse("Invalid visit date") },
            visitTime =
                time?.takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalTime.parse(it) }.getOrElse { throw BadRequestResponse("Invalid visit time") }
                },
            note = note,
        )
}
