package com.companyb.companyapp.identity
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.patch
import com.companyb.companyapp.api.post
import com.companyb.companyapp.contracts.authorization.RightsResponse
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.identity.ChangePasswordRequest
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.identity.UpdateProfileRequest
import com.companyb.companyapp.contracts.workforce.ActiveAttendanceResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.MeService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ME,
    methods = [HttpMethod.GET],
    operationId = "me",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = MeResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_BRANCHES,
    methods = [HttpMethod.GET],
    operationId = "me_branches",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<MeBranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_CAPABILITIES,
    methods = [HttpMethod.GET],
    operationId = "me_capabilities",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RightsResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_ACTIVE_ATTENDANCE,
    methods = [HttpMethod.GET],
    operationId = "me_active_attendance",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ActiveAttendanceResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME,
    methods = [HttpMethod.PATCH],
    operationId = "me_update",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateProfileRequest::class)], required = true),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = MeResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_PASSWORD,
    methods = [HttpMethod.POST],
    operationId = "me_change_password",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ChangePasswordRequest::class)], required = true),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object MeRoutes {
    fun updateMe(config: JavalinConfig) {
        config.patch(ApiRoutes.ME, Gate.SignedIn("the caller's own profile")) { context ->
            val request = context.bodyAsClass<UpdateProfileRequest>()
            context.json(MeService.updateProfile(context.callerUuid(), request.displayName, request.email))
        }
    }

    fun changePassword(config: JavalinConfig) {
        config.post(ApiRoutes.ME_PASSWORD, Gate.SignedIn("the caller's own password")) { context ->
            val request = context.bodyAsClass<ChangePasswordRequest>()
            AuthService.changePassword(context.callerUuid(), request.currentPassword, request.newPassword)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    fun getMe(config: JavalinConfig) {
        config.get(ApiRoutes.ME, Gate.SignedIn("the caller's own profile")) { context ->
            val callerId = context.callerUuid()
            val response: MeResponse = MeService.getMe(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getCapabilities(config: JavalinConfig) {
        config.get(ApiRoutes.ME_CAPABILITIES, Gate.SignedIn("the caller's own grants")) { context ->
            val callerId = context.callerUuid()
            val response: RightsResponse = MeService.getRights(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getBranches(config: JavalinConfig) {
        config.get(ApiRoutes.ME_BRANCHES, Gate.SignedIn("the caller's own branches")) { context ->
            val callerId = context.callerUuid()
            val response: List<MeBranchResponse> = MeService.getBranches(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    /** caller-only active-shift read for launch/login resume (null shift = none). */
    fun getActiveAttendance(config: JavalinConfig) {
        config.get(ApiRoutes.ME_ACTIVE_ATTENDANCE, Gate.SignedIn("the caller's own clock-in")) { context ->
            val callerId = context.callerUuid()
            val response: ActiveAttendanceResponse = MeService.getActiveAttendance(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }
}
