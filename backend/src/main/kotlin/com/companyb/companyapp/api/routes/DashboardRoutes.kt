package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardCommissionResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.repository.ClientNames
import com.companyb.companyapp.repository.ConcernWithSessionId
import com.companyb.companyapp.repository.SessionPractitionerWithName
import com.companyb.companyapp.service.dashboard.DashboardData
import com.companyb.companyapp.service.dashboard.DashboardService
import com.companyb.companyapp.session.Concern
import com.companyb.companyapp.session.Session
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_DASHBOARD_TODAY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "dashboard_today",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = DashboardResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object DashboardRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.BRANCH_DASHBOARD_TODAY_PATH, ::handleGetToday)
    }

    private fun handleGetToday(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val data = DashboardService.getToday(callerId, branchId)
        context.json(data.toResponse())
    }

    private fun DashboardData.toResponse(): DashboardResponse {
        val enrichment =
            DashboardSessionEnrichment(
                clientNames = clientNames,
                voidedSessionIds = voidedSessionIds,
                practitionerBySession = practitioners.groupBy { it.sessionId },
                concernsBySession = concerns.groupBy { it.sessionId },
                requestedPractitionerNames = requestedPractitionerNames,
                branchIdBySession = branchIdBySession,
            )

        return DashboardResponse(
            sessions =
                sessions.map { session ->
                    mapDashboardSession(session, enrichment)
                },
            commission =
                DashboardCommissionResponse(
                    amount = commission.amount.toPlainString(),
                    productSalesCount = commission.productSalesCount,
                ),
        )
    }
}

/** #366 — the per-read enrichment tables [mapDashboardSession] joins the session rows with. */
internal data class DashboardSessionEnrichment(
    val clientNames: Map<UUID, ClientNames>,
    val voidedSessionIds: Set<UUID>,
    val practitionerBySession: Map<UUID, List<SessionPractitionerWithName>>,
    val concernsBySession: Map<UUID, List<ConcernWithSessionId>>,
    /** #366 — requested-practitioner display names keyed by user id (#366). */
    val requestedPractitionerNames: Map<UUID, String> = emptyMap(),
    /**
     * #382 — owning branch per session id. The dashboard path knows it from the route; the
     * single-session detail read resolves it from the session's branch day.
     */
    val branchIdBySession: Map<UUID, UUID> = emptyMap(),
)

/**
 * Shared session → [DashboardSessionResponse] mapping — the dashboard list and the #152
 * session-detail read render byte-identical (#151 Q3: reuse the DTO, no new shape).
 * The practitioner/concern lists arrive pre-grouped by session so the dashboard list path
 * groups once instead of per session.
 */
internal fun mapDashboardSession(
    session: Session,
    enrichment: DashboardSessionEnrichment,
): DashboardSessionResponse {
    val client = enrichment.clientNames[session.clientId]
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
        isVoided = session.id in enrichment.voidedSessionIds,
        requestedPractitionerName =
            session.requestedPractitionerId?.let { enrichment.requestedPractitionerNames[it] },
        branchId = enrichment.branchIdBySession[session.id]?.toString() ?: "",
        practitioners =
            enrichment.practitionerBySession[session.id]
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
            enrichment.concernsBySession[session.id]
                .orEmpty()
                .map { it.concern.toResponse() },
    )
}

/** Single Concern → [ConcernResponse] seam (#459): dashboard and session reads share it. */
internal fun Concern.toResponse(): ConcernResponse =
    ConcernResponse(
        id = id.toString(),
        label = label,
        createdBy = createdBy?.toString(),
        createdAt = createdAt?.toString(),
    )
