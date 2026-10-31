package com.companyb.companyapp.workforce
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.workforce.AttendanceMarkResponse
import com.companyb.companyapp.contracts.workforce.ClockInRequest
import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.contracts.workforce.ClockOutRequest
import com.companyb.companyapp.contracts.workforce.ClockOutResponse
import com.companyb.companyapp.contracts.workforce.MarkAttendanceRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.StartService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ATTENDANCE_CLOCK_IN,
    methods = [HttpMethod.POST],
    operationId = "attendance_clock_in",
    queryParams = [OpenApiParam(name = "withToday", type = Boolean::class, required = false)],
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ClockInRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClockInResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ClockInResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ATTENDANCE_CLOCK_OUT,
    methods = [HttpMethod.POST],
    operationId = "attendance_clock_out",
    queryParams =
        [
            OpenApiParam(name = "branchId", type = UUID::class, required = false),
            OpenApiParam(name = "withToday", type = Boolean::class, required = false),
        ],
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ClockOutRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClockOutResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_ATTENDANCE_MARKS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_attendance_marks_post",
    queryParams = [OpenApiParam(name = "withToday", type = Boolean::class, required = false)],
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = MarkAttendanceRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = AttendanceMarkResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object AttendanceRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    /**
     * A clock change moves the shift, rights, the day and Today's lists: answer with all of it,
     * read after the write.
     */
    private fun Context.startAfter(
        callerId: UUID,
        branchId: UUID?,
    ) = StartService.get(callerId, branchId, queryParam("withToday") == "true")

    fun clockOut(config: JavalinConfig) {
        config.routes.post(ApiRoutes.ATTENDANCE_CLOCK_OUT) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<ClockOutRequest>()

            val attendanceId = uuidOrThrow(request.attendanceId, "attendance id")

            val result = AttendanceService.clockOut(attendanceId, callerId)
            val screenBranch = context.queryParam(BRANCH_ID_PARAM)?.runCatching(UUID::fromString)?.getOrNull()

            context.status(HttpStatus.OK)
            context.json(
                ClockOutResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    userId = result.userId.toString(),
                    markedBy = result.markedBy.toString(),
                    clockIn = result.clockIn.toString(),
                    clockOut = result.clockOut?.toString(),
                    isRelief = result.isRelief,
                    start = context.startAfter(callerId, screenBranch),
                ),
            )
        }
    }

    fun clockIn(config: JavalinConfig) {
        config.routes.post(ApiRoutes.ATTENDANCE_CLOCK_IN) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<ClockInRequest>()

            val attendanceId = uuidOrThrow(request.attendanceId, "attendance id")
            val branchId = uuidOrThrow(request.branchId, "branch id")
            BranchService.findById(branchId)
            // Audit M3 (user, message 13675): a role at the branch, or an approved relief request.
            Access.require(
                callerId,
                Act.WORK_DAY,
                Place.today(branchId),
                "Only this branch's staff, or relief approved for today, can clock in here",
            )

            val result = AttendanceService.clockIn(attendanceId, branchId, callerId)

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(
                ClockInResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    userId = result.userId.toString(),
                    markedBy = result.markedBy.toString(),
                    clockIn = result.clockIn.toString(),
                    clockOut = result.clockOut?.toString(),
                    isRelief = result.isRelief,
                    start = context.startAfter(callerId, branchId),
                ),
            )
        }
    }

    /** member marks member present/absent at the branch today. */
    fun mark(config: JavalinConfig) {
        config.routes.post(ApiRoutes.BRANCH_ATTENDANCE_MARKS_PATH) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val request = context.bodyAsClass<MarkAttendanceRequest>()

            val targetUserId = uuidOrThrow(request.userId, "user id")
            val attendanceId = request.attendanceId?.let { uuidOrThrow(it, "attendance id") }

            val result =
                AttendanceService.mark(
                    callerId = callerId,
                    branchId = branchId,
                    targetUserId = targetUserId,
                    present = request.present,
                    attendanceId = attendanceId,
                )

            context.json(
                AttendanceMarkResponse(
                    attendance = result.toClockInResponse(),
                    start = context.startAfter(callerId, branchId),
                ),
            )
        }
    }

    /** Route-layer wire mapping (shape): null attendance survives as a null payload. */
    private fun AttendanceMarkResult.toClockInResponse(): ClockInResponse? =
        attendance?.let { row ->
            ClockInResponse(
                id = row.id.toString(),
                branchDayId = row.branchDayId.toString(),
                userId = row.userId.toString(),
                markedBy = row.markedBy.toString(),
                clockIn = row.clockIn.toString(),
                clockOut = row.clockOut?.toString(),
                isRelief = isRelief,
            )
        }
}
