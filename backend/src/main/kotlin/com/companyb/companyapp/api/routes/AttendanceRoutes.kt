package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.dto.ClockOutResponse
import com.companyb.companyapp.service.attendance.AttendanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object AttendanceRoutes {
    @Suppress("ThrowsCount")
    fun clockOut(config: JavalinConfig) {
        config.routes.post("/api/attendance/clock-out") { context ->
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
        config.routes.post("/api/attendance/clock-in") { context ->
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
}
