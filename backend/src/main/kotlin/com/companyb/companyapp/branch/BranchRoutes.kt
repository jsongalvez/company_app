package com.companyb.companyapp.branch
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.patch
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.BranchReadScope
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.branch.CreateBranchRequest
import com.companyb.companyapp.contracts.branch.RenameBranchRequest
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
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCHES,
    methods = [HttpMethod.GET],
    operationId = "branches_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCHES,
    methods = [HttpMethod.POST],
    operationId = "branches_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateBranchRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCHES_ACCESSIBLE,
    methods = [HttpMethod.GET],
    operationId = "branches_accessible",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_rename",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RenameBranchRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val MAX_NAME_LENGTH = 100

    fun register(config: JavalinConfig) {
        val branchesGate =
            Gate.Before { context ->
                Access.require(
                    context,
                    Act.MANAGE_USERS,
                    message = "MANAGE_USERS capability required to manage branches",
                )
            }

        val branchGate =
            Gate.Before { context ->
                val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
                BranchService.findById(branchId)
                Access.require(context, Act.SEE_BRANCH, Place.Branch(branchId))
            }

        // The Reports picker: the caller's branches, or every branch for a GLOBAL VIEW_BRANCH_DATA holder.
        // Zero-grant callers get an empty list, not 403.
        config.get(
            ApiRoutes.BRANCHES_ACCESSIBLE,
            Gate.SignedIn("lists only the branches the caller may see"),
        ) { context ->
            context.json(accessibleBranches(context.callerUuid()))
        }

        config.post(ApiRoutes.BRANCHES, branchesGate) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateBranchRequest>()
            val branchId = uuidOrThrow(request.id, "branch id")
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Branch name is required")
            // the forward-compat sentinel is never valid input (the Postgres enum
            // column carries no such label). Unknown strings still 400 at decode.
            if (request.branchType == BranchType.UNKNOWN) throw BadRequestResponse("Unknown branch type")
            val result =
                BranchService.create(
                    callerId = callerId,
                    id = branchId,
                    name = name,
                    branchType = request.branchType,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.branch.toResponse())
        }

        config.get(ApiRoutes.BRANCHES, branchesGate) { context ->
            context.json(BranchService.findAll().map { it.toResponse() })
        }

        // Rename: branchGate checks only the read window; rename() checks MANAGE_USERS, as create does.
        config.patch(ApiRoutes.BRANCH_PATH, branchGate) { context -> rename(context) }

        config.get(ApiRoutes.BRANCH_PATH, branchGate) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            context.json(BranchService.findById(branchId).toResponse())
        }
    }

    private fun rename(context: io.javalin.http.Context) {
        Access.require(context, Act.MANAGE_USERS, message = "MANAGE_USERS capability required to manage branches")
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val name = context.bodyAsClass<RenameBranchRequest>().name.trim()
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            throw BadRequestResponse("Branch name must be 1 to $MAX_NAME_LENGTH characters")
        }
        context.json(BranchService.rename(context.callerUuid(), branchId, name).toResponse())
    }

    /** The branches in the caller's read window (all of them for a global viewer). */
    internal fun accessibleBranches(callerId: UUID): List<BranchResponse> {
        val window = BranchReadScope.windowBranchIds(callerId)
        return (
            if (window ==
                null
            ) {
                BranchService.findAll()
            } else {
                BranchService.findByIds(window)
            }
        ).map { it.toResponse() }
    }

    internal fun Branch.toResponse(): BranchResponse =
        BranchResponse(
            id = id.toString(),
            name = name,
            branchType = branchType,
        )
}
