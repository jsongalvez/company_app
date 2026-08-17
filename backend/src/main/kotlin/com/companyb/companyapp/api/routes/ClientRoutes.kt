package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.service.ClientService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/clients",
    methods = [HttpMethod.GET],
    operationId = "clients_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/clients",
    methods = [HttpMethod.POST],
    operationId = "clients_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/clients/{clientId}",
    methods = [HttpMethod.GET],
    operationId = "client_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/clients/{clientId}",
    methods = [HttpMethod.PATCH],
    operationId = "client_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/clients/{clientId}/anonymize",
    methods = [HttpMethod.POST],
    operationId = "client_anonymize",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object ClientRoutes {
    private const val CLIENT_ID_PARAM = "clientId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/clients") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.EDIT_BRANCH_DATA,
                "EDIT_BRANCH_DATA capability required to manage clients",
            )
        }

        config.routes.post("/api/clients", ::handleCreate)
        config.routes.get("/api/clients", ::handleSearch)
        config.routes.get("/api/clients/{$CLIENT_ID_PARAM}", ::handleGetById)
        config.routes.patch("/api/clients/{$CLIENT_ID_PARAM}", ::handleUpdate)
        config.routes.post("/api/clients/{$CLIENT_ID_PARAM}/anonymize", ::handleAnonymize)
    }

    @Suppress("ThrowsCount")
    private fun handleCreate(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateClientRequest>()
        val clientId = uuidOrThrow(request.id, "client id")

        val firstName = request.firstName.trim()
        if (firstName.isBlank()) throw BadRequestResponse("First name is required")
        val lastName = request.lastName.trim()
        if (lastName.isBlank()) throw BadRequestResponse("Last name is required")
        val hasBothBp = request.systolicBp != null && request.diastolicBp != null
        val hasNone = request.systolicBp == null && request.diastolicBp == null
        if (!hasBothBp && !hasNone) {
            throw BadRequestResponse(
                "Both systolic and diastolic blood pressure must be provided together or not at all",
            )
        }

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
        context.json(ClientService.search(trimmedQuery).map { it.toResponse() })
    }

    private fun handleGetById(context: Context) {
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)
        context.json(ClientService.findById(clientId).toResponse())
    }

    @Suppress("ThrowsCount")
    private fun handleUpdate(context: Context) {
        val callerId = context.callerUuid()
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)
        val request = context.bodyAsClass<UpdateClientRequest>()

        val firstName = request.firstName?.trim()
        if (firstName != null && firstName.isBlank()) {
            throw BadRequestResponse("First name cannot be blank")
        }
        val lastName = request.lastName?.trim()
        if (lastName != null && lastName.isBlank()) {
            throw BadRequestResponse("Last name cannot be blank")
        }
        val systolicBp = request.systolicBp
        val diastolicBp = request.diastolicBp
        val hasBothBp = systolicBp != null && diastolicBp != null
        val hasNone = systolicBp == null && diastolicBp == null
        if (!hasBothBp && !hasNone) {
            throw BadRequestResponse(
                "Both systolic and diastolic blood pressure must be provided together or not at all",
            )
        }

        val updated =
            ClientService.update(
                callerId = callerId,
                clientId = clientId,
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

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleAnonymize(context: Context) {
        val callerId = context.callerUuid()
        val clientId = context.pathParamAsUuid(CLIENT_ID_PARAM)

        ClientService.anonymize(callerId, clientId)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun Client.toResponse(): ClientResponse =
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
        )
}
