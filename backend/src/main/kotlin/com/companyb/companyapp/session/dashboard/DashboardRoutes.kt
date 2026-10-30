package com.companyb.companyapp.session.dashboard
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.ConcernResponse
import com.companyb.companyapp.contracts.session.DashboardCommissionResponse
import com.companyb.companyapp.contracts.session.DashboardPractitionerResponse
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.session.Concern
import com.companyb.companyapp.session.Session
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_SESSIONS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "date", type = String::class, required = true)],
    operationId = "branch_sessions_for_day",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<DashboardSessionResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object DashboardRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_SESSIONS_PATH) { context ->
            if (context.method() == HandlerType.GET) {
                val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
                val date = context.queryParam("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                Access.require(context, Act.SEE_DAY, date?.let { Place.onDate(branchId, it) } ?: Place.Branch(branchId))
            }
        }
        config.routes.get(ApiRoutes.BRANCH_SESSIONS_PATH, ::handleGetSessionsForDay)
    }

    /** Any day's sessions at a branch, for the Expenses & pay page (feedback 3578107f). */
    private fun handleGetSessionsForDay(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val raw = context.queryParam("date") ?: throw BadRequestResponse("date is required")
        val date = runCatching { LocalDate.parse(raw) }.getOrElse { throw BadRequestResponse("Invalid date") }
        val data = DashboardService.getSessionsForDay(branchId, date)
        val enrichment =
            DashboardSessionEnrichment(
                clientNames = data.clientNames,
                voidedSessionIds = data.voidedSessionIds,
                practitionerBySession = data.practitioners.groupBy { it.sessionId },
                concernsBySession = data.concerns.groupBy { it.sessionId },
                requestedPractitionerNames = data.requestedPractitionerNames,
                branchIdBySession = data.branchIdBySession,
            )
        context.json(data.sessions.map { mapDashboardSession(it, enrichment) })
    }

    internal fun DashboardData.toResponse(): DashboardResponse {
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

/** the per-read enrichment tables [mapDashboardSession] joins the session rows with. */
internal data class DashboardSessionEnrichment(
    val clientNames: Map<UUID, ClientNames>,
    val voidedSessionIds: Set<UUID>,
    val practitionerBySession: Map<UUID, List<SessionPractitionerWithName>>,
    val concernsBySession: Map<UUID, List<ConcernWithSessionId>>,
    /** requested-practitioner display names keyed by user id. */
    val requestedPractitionerNames: Map<UUID, String> = emptyMap(),
    /**
     * owning branch per session id. The dashboard path knows it from the route; the
     * single-session detail read resolves it from the session's branch day.
     */
    val branchIdBySession: Map<UUID, UUID> = emptyMap(),
)

/**
 * Shared session → [DashboardSessionResponse] mapping — the dashboard list and the
 * session-detail read render byte-identical (reuse the DTO, no new shape).
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

/** Single Concern → [ConcernResponse] seam: dashboard and session reads share it. */
internal fun Concern.toResponse(): ConcernResponse =
    ConcernResponse(
        id = id.toString(),
        label = label,
        createdBy = createdBy?.toString(),
        createdAt = createdAt?.toString(),
    )
