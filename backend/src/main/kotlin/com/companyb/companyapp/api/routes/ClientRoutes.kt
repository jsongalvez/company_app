package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.service.ClientService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object ClientRoutes {
    private const val CLIENT_ID_PARAM = "clientId"

    @Suppress("LongMethod", "ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/clients") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateClientRequest>()
            val clientId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid client id") }

            val result =
                ClientService.create(
                    callerId = callerId,
                    id = clientId,
                    firstName = request.firstName,
                    lastName = request.lastName,
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

        config.routes.get("/api/clients") { context ->
            val query = context.queryParam("q") ?: throw BadRequestResponse("Query parameter 'q' is required")
            context.json(ClientService.search(query).map { it.toResponse() })
        }

        config.routes.get("/api/clients/{$CLIENT_ID_PARAM}") { context ->
            val clientId =
                runCatching { UUID.fromString(context.pathParam(CLIENT_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid client id") }
            context.json(ClientService.findById(clientId).toResponse())
        }

        config.routes.patch("/api/clients/{$CLIENT_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val clientId =
                runCatching { UUID.fromString(context.pathParam(CLIENT_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid client id") }
            val request = context.bodyAsClass<UpdateClientRequest>()

            val updated =
                ClientService.update(
                    callerId = callerId,
                    clientId = clientId,
                    firstName = request.firstName,
                    lastName = request.lastName,
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

        config.routes.post("/api/clients/{$CLIENT_ID_PARAM}/anonymize") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val clientId =
                runCatching { UUID.fromString(context.pathParam(CLIENT_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid client id") }

            ClientService.anonymize(callerId, clientId)
            context.status(HttpStatus.NO_CONTENT)
        }
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
