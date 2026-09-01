package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.AttendanceMarkResponse
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.dto.ClockOutResponse
import com.companyb.companyapp.dto.MarkAttendanceRequest
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.service.attendance.AttendanceMarkResult
import com.companyb.companyapp.service.attendance.AttendanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ATTENDANCE_CLOCK_IN,
    methods = [HttpMethod.POST],
    operationId = "attendance_clock_in",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.ATTENDANCE_CLOCK_OUT,
    methods = [HttpMethod.POST],
    operationId = "attendance_clock_out",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.BRANCH_ATTENDANCE_TODAY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_attendance_today_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.BRANCH_ATTENDANCE_MARKS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_attendance_marks_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object AttendanceRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    @Suppress("ThrowsCount")
    fun clockOut(config: JavalinConfig) {
        config.routes.post(ApiRoutes.ATTENDANCE_CLOCK_OUT) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<ClockOutRequest>()

            val attendanceId = uuidOrThrow(request.attendanceId, "attendance id")

            val result = AttendanceService.clockOut(attendanceId, callerId)

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
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun clockIn(config: JavalinConfig) {
        config.routes.post(ApiRoutes.ATTENDANCE_CLOCK_IN) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<ClockInRequest>()

            val attendanceId = uuidOrThrow(request.attendanceId, "attendance id")
            val branchId = uuidOrThrow(request.branchId, "branch id")

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
                ),
            )
        }
    }

    /** #404 — membership-gated roster: home members with live presence flags for today. */
    fun rosterToday(config: JavalinConfig) {
        config.routes.get(ApiRoutes.BRANCH_ATTENDANCE_TODAY_PATH) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

            context.json(
                AttendanceService.rosterToday(callerId, branchId).map { entry ->
                    MemberAttendanceResponse(
                        assignmentId = entry.assignmentId.toString(),
                        userId = entry.userId.toString(),
                        displayName = entry.displayName,
                        slot = entry.slot,
                        present = entry.present,
                    )
                },
            )
        }
    }

    /** #404 — member marks member present/absent at the branch today. */
    @Suppress("ThrowsCount")
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
                AttendanceMarkResponse(attendance = result.toClockInResponse()),
            )
        }
    }

    /** Route-layer wire mapping (#403 shape): null attendance survives as a null payload. */
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
