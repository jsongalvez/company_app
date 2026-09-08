package com.companyb.companyapp.client
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.client.ClientPatchField
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.CreateClientRequest
import com.companyb.companyapp.contracts.client.UpdateClientRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.exception.ValidationException
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
    path = ApiRoutes.CLIENTS,
    methods = [HttpMethod.GET],
    queryParams = [OpenApiParam(name = "q", type = String::class, required = true)],
    operationId = "clients_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ClientResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CLIENTS,
    methods = [HttpMethod.POST],
    operationId = "clients_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateClientRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClientResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ClientResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CLIENT_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "client_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClientResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CLIENT_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "client_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateClientRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ClientResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CLIENT_ANONYMIZE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "clientId", type = UUID::class, required = true)],
    operationId = "client_anonymize",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ClientRoutes {
    private const val CLIENT_ID_PARAM = "clientId"

    fun register(config: JavalinConfig) {
        // #653 exact-segment lesson (#114): before(CLIENTS) does NOT fire on child
        // segments — item + anonymize routes need their own GLOBAL EDIT_BRANCH_DATA
        // guards (PII-destruction stays under the same gate: no narrower code exists).
        config.routes.before(ApiRoutes.CLIENTS) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }

        config.routes.before(ApiRoutes.CLIENT_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }

        config.routes.before(ApiRoutes.CLIENT_ANONYMIZE_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }

        config.routes.post(ApiRoutes.CLIENTS, ::handleCreate)
        config.routes.get(ApiRoutes.CLIENTS, ::handleSearch)
        config.routes.get(ApiRoutes.CLIENT_PATH, ::handleGetById)
        config.routes.patch(ApiRoutes.CLIENT_PATH, ::handleUpdate)
        config.routes.post(ApiRoutes.CLIENT_ANONYMIZE_PATH, ::handleAnonymize)
    }

    private fun handleCreate(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateClientRequest>()
        val clientId = uuidOrThrow(request.id, "client id")

        val firstName = request.firstName.trim()
        val lastName = request.lastName.trim()
        // #599: create guards split into named checks (ThrowsCount budget is 2 per function).
        requireCreateNames(firstName, lastName)
        requireCreateBpPair(request.systolicBp, request.diastolicBp)

        val result =
            ClientService.create(
                callerId = callerId,
                id = clientId,
                firstName = firstName,
                lastName = lastName,
                middleName = request.middleName,
                suffix = request.suffix,
                phoneNumber = request.phoneNumber,
                address = request.address,
                gender = request.gender,
                age = request.age,
                systolicBp = request.systolicBp,
                diastolicBp = request.diastolicBp,
                medicalConditions = request.medicalConditions,
            )

        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.client.toResponse())
    }

    private fun handleSearch(context: Context) {
        val query = context.queryParam("q") ?: throw BadRequestResponse("Query parameter 'q' is required")
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) throw BadRequestResponse("Search query cannot be blank")
        val clients = ClientService.search(trimmedQuery)
        val sessionCounts = ClientService.countSessions(clients.map { it.id })
        context.json(clients.map { it.toResponse(sessionCounts[it.id] ?: 0) })
    }

    private fun handleGetById(context: Context) {
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)
        val client = ClientService.findById(clientId)
        context.json(client.toResponse())
    }

    private fun handleUpdate(context: Context) {
        val callerId = context.callerUuid()
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)
        val request = context.bodyAsClass<UpdateClientRequest>()
        validateClientPatch(request)

        val updated =
            ClientService.update(
                callerId = callerId,
                clientId = clientId,
                firstName = request.firstName?.trim(),
                lastName = request.lastName?.trim(),
                middleName = request.middleName,
                suffix = request.suffix,
                phoneNumber = request.phoneNumber,
                address = request.address,
                gender = request.gender,
                age = request.age,
                systolicBp = request.systolicBp,
                diastolicBp = request.diastolicBp,
                medicalConditions = request.medicalConditions,
                clearFields = request.clearFields,
            )

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    /**
     * PATCH shape validation (#523) — mirrors [resolveClientPatch] with
     * [BadRequestResponse] to preserve the route's existing error shape. The service
     * re-validates defensively for direct callers.
     */
    private fun validateClientPatch(request: UpdateClientRequest) {
        val clears = request.clearFields
        // Colocated clear-set policy (#541): the command owns the shape, the route only
        // maps its error to the route's existing BadRequestResponse shape.
        try {
            checkClearShape(clears)
        } catch (e: ValidationException) {
            throw BadRequestResponse(e.message ?: "Invalid clear fields").apply { initCause(e) }
        }
        checkRequiredName(request.firstName, "First name")
        checkRequiredName(request.lastName, "Last name")
        checkSettable(
            "Middle name",
            request.middleName,
            ClientPatchField.MIDDLE_NAME in clears,
            "middleName",
        )
        checkSettable("Suffix", request.suffix, ClientPatchField.SUFFIX in clears, "suffix")
        checkSettable(
            "Phone number",
            request.phoneNumber,
            ClientPatchField.PHONE_NUMBER in clears,
            "phoneNumber",
        )
        checkSettable("Address", request.address, ClientPatchField.ADDRESS in clears, "address")
        checkSettable(
            "Medical conditions",
            request.medicalConditions,
            ClientPatchField.MEDICAL_CONDITIONS in clears,
            "medicalConditions",
        )
        checkBpPatch(
            request.systolicBp,
            request.diastolicBp,
            ClientPatchField.SYSTOLIC_BP in clears,
            ClientPatchField.DIASTOLIC_BP in clears,
        )
    }

    private fun handleAnonymize(context: Context) {
        val callerId = context.callerUuid()
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)

        ClientService.anonymize(callerId, clientId)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun Client.toResponse(): ClientResponse = toResponse(ClientService.countSessions(listOf(id))[id] ?: 0)

    /**
     * #599: create name guards split from handleCreate (ThrowsCount budget is 2 per function).
     */
    private fun requireCreateNames(
        firstName: String,
        lastName: String,
    ) {
        if (firstName.isBlank()) throw BadRequestResponse("First name is required")
        if (lastName.isBlank()) throw BadRequestResponse("Last name is required")
    }

    /**
     * #599: create BP-pair guard split from handleCreate (ThrowsCount budget is 2 per function).
     */
    private fun requireCreateBpPair(
        systolicBp: Short?,
        diastolicBp: Short?,
    ) {
        val hasBothBp = systolicBp != null && diastolicBp != null
        val hasNone = systolicBp == null && diastolicBp == null
        if (!hasBothBp && !hasNone) {
            throw BadRequestResponse(
                "Both systolic and diastolic blood pressure must be provided together or not at all",
            )
        }
    }

    private fun Client.toResponse(sessionCount: Int): ClientResponse =
        ClientResponse(
            id = id.toString(),
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            suffix = suffix,
            phoneNumber = phoneNumber,
            address = address,
            gender = gender,
            age = age,
            systolicBp = systolicBp,
            diastolicBp = diastolicBp,
            medicalConditions = medicalConditions,
            sessionCount = sessionCount,
        )
}

/** Route-level PATCH mirrors (#523): BadRequestResponse shapes over the colocated policy. */

private fun checkRequiredName(
    value: String?,
    label: String,
) {
    if (value != null && value.trim().isEmpty()) {
        throw BadRequestResponse("$label cannot be blank")
    }
}

private fun checkBpPatch(
    systolicBp: Short?,
    diastolicBp: Short?,
    clearSystolic: Boolean,
    clearDiastolic: Boolean,
) {
    val hasValue = systolicBp != null || diastolicBp != null
    val hasClear = clearSystolic || clearDiastolic
    val setAndClear = hasValue && hasClear
    val halfSet = (systolicBp != null) != (diastolicBp != null)
    val halfClear = clearSystolic != clearDiastolic
    val partial = setAndClear || halfSet || halfClear
    if (setAndClear) {
        throw BadRequestResponse("Blood pressure cannot be both set and cleared")
    }
    if (partial) {
        throw BadRequestResponse(
            "Both systolic and diastolic blood pressure must be provided together or not at all",
        )
    }
}

private fun checkSettable(
    label: String,
    value: String?,
    clear: Boolean,
    field: String,
) {
    if (clear && value != null) {
        throw BadRequestResponse("$field cannot be both set and cleared")
    }
    if (!clear && value != null && value.trim().isEmpty()) {
        throw BadRequestResponse("$label cannot be blank (use clearFields to clear)")
    }
}
