package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardCommissionResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.repository.ClientNames
import com.companyb.companyapp.repository.ConcernWithSessionId
import com.companyb.companyapp.repository.SessionPractitionerWithName
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.service.dashboard.DashboardData
import com.companyb.companyapp.service.dashboard.DashboardService
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/branches/{branchId}/dashboard/today",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "dashboard_today",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object DashboardRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/dashboard/today", ::handleGetToday)
    }

    private fun handleGetToday(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val data = DashboardService.getToday(callerId, branchId)
        context.json(data.toResponse())
    }

    private fun DashboardData.toResponse(): DashboardResponse {
        val practitionerBySession =
            practitioners.groupBy { it.sessionId }
        val concernsBySession =
            concerns.groupBy { it.sessionId }

        return DashboardResponse(
            sessions =
                sessions.map { session ->
                    mapDashboardSession(
                        session = session,
                        clientNames = clientNames,
                        voidedSessionIds = voidedSessionIds,
                        practitionerBySession = practitionerBySession,
                        concernsBySession = concernsBySession,
                    )
                },
            commission =
                DashboardCommissionResponse(
                    amount = commission.amount.toPlainString(),
                    productSalesCount = commission.productSalesCount,
                ),
        )
    }
}

/**
 * Shared session → [DashboardSessionResponse] mapping — the dashboard list and the #152
 * session-detail read render byte-identical (#151 Q3: reuse the DTO, no new shape).
 * The practitioner/concern lists arrive pre-grouped by session so the dashboard list path
 * groups once instead of per session.
 */
internal fun mapDashboardSession(
    session: Session,
    clientNames: Map<UUID, ClientNames>,
    voidedSessionIds: Set<UUID>,
    practitionerBySession: Map<UUID, List<SessionPractitionerWithName>>,
    concernsBySession: Map<UUID, List<ConcernWithSessionId>>,
): DashboardSessionResponse {
    val client = clientNames[session.clientId]
    return DashboardSessionResponse(
        id = session.id.toString(),
        clientId = session.clientId.toString(),
        clientName =
            listOfNotNull(client?.firstName, client?.lastName)
                .joinToString(" ")
                .ifBlank { null },
        sessionType = session.sessionType,
        isWalkIn = session.isWalkIn,
        sessionStatus = session.sessionStatus,
        basePrice = session.basePrice.toPlainString(),
        finalPrice = session.finalPrice.toPlainString(),
        remarks = session.remarks,
        otherConcerns = session.otherConcerns,
        bookedAt = session.bookedAt?.toString(),
        nextAppointmentDate = session.nextAppointmentDate?.toString(),
        version = session.version,
        isVoided = session.id in voidedSessionIds,
        practitioners =
            practitionerBySession[session.id]
                .orEmpty()
                .map {
                    DashboardPractitionerResponse(
                        practitionerId = it.practitionerId.toString(),
                        displayName = it.displayName,
                        remarks = it.remarks,
                        slotAtTime = it.slotAtTime,
                    )
                },
        concerns =
            concernsBySession[session.id]
                .orEmpty()
                .map {
                    ConcernResponse(
                        id = it.concern.id.toString(),
                        label = it.concern.label,
                        createdBy = it.concern.createdBy?.toString(),
                        createdAt = it.concern.createdAt?.toString(),
                    )
                },
    )
}
