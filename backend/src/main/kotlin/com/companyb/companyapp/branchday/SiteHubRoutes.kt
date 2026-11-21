package com.companyb.companyapp.branchday

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.delete
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.put
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.contracts.branchday.SiteHubChallengeRequest
import com.companyb.companyapp.contracts.branchday.SiteHubChallengeResponse
import com.companyb.companyapp.contracts.branchday.SiteHubLaptopRequest
import com.companyb.companyapp.contracts.branchday.SiteHubLaptopResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.io.IOException

private val logger = KotlinLogging.logger {}

@OpenApi(
    path = ApiRoutes.SITE_HUB_LAPTOPS,
    methods = [HttpMethod.POST],
    operationId = "site_hub_pair_laptop",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SiteHubLaptopRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SiteHubLaptopResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SITE_HUB_CHALLENGE,
    methods = [HttpMethod.PUT],
    operationId = "site_hub_challenge_put",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SiteHubChallengeRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SiteHubChallengeResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "429", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "502", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "503", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SITE_HUB_CHALLENGE,
    methods = [HttpMethod.DELETE],
    operationId = "site_hub_challenge_delete",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SiteHubChallengeRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "502", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "503", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object SiteHubRoutes {
    private const val MAX_NAME = 80

    fun register(config: JavalinConfig) {
        config.post(ApiRoutes.SITE_HUB_LAPTOPS, Gate.Can(Act.MANAGE_USERS)) { ctx ->
            val name =
                ctx
                    .bodyAsClass<SiteHubLaptopRequest>()
                    .name
                    .take(MAX_NAME)
            val t = SiteHubCerts.pair()
            logger.info { "[SITE-HUB] laptop ($name) paired by ${ctx.callerUuid()} until ${t.expiresAt}" }
            ctx.json(SiteHubLaptopResponse(t.token, t.expiresAt.toString()))
        }
        config.put(ApiRoutes.SITE_HUB_CHALLENGE, Gate.Open("the laptop token is the key")) { ctx ->
            val r = ctx.bodyAsClass<SiteHubChallengeRequest>()
            answer(ctx, HttpStatus.OK) { SiteHubCerts.put(r.token, r.value) }
        }
        config.delete(ApiRoutes.SITE_HUB_CHALLENGE, Gate.Open("the laptop token is the key")) { ctx ->
            val r = ctx.bodyAsClass<SiteHubChallengeRequest>()
            answer(ctx, HttpStatus.NO_CONTENT) { SiteHubCerts.remove(r.token, r.value) }
        }
    }

    private fun answer(
        ctx: Context,
        done: HttpStatus,
        act: () -> SiteHubCerts.Result,
    ) {
        val result =
            try {
                act()
            } catch (e: IOException) {
                logger.warn { "[SITE-HUB] DNS write failed: ${e.message}" }
                refuse(ctx, HttpStatus.BAD_GATEWAY, "Couldn't write the DNS record. Try again later.")
                return
            }
        when (result) {
            is SiteHubCerts.Result.Done -> {
                if (done == HttpStatus.OK) ctx.json(SiteHubChallengeResponse(result.token)) else ctx.status(done)
            }

            SiteHubCerts.Result.BadToken -> {
                refuse(ctx, HttpStatus.UNAUTHORIZED, "This laptop isn't paired. Sign in again in the site hub.")
            }

            SiteHubCerts.Result.BadValue -> {
                refuse(ctx, HttpStatus.BAD_REQUEST, "That isn't a certificate challenge.")
            }

            SiteHubCerts.Result.TooMany -> {
                refuse(ctx, HttpStatus.TOO_MANY_REQUESTS, "Too many tries. Wait an hour.")
            }

            SiteHubCerts.Result.NotSetUp -> {
                refuse(ctx, HttpStatus.SERVICE_UNAVAILABLE, "Site hub certificates are not set up on the server yet.")
            }
        }
    }

    private fun refuse(
        ctx: Context,
        status: HttpStatus,
        words: String,
    ) {
        ctx.status(status).json(ErrorResponse(words))
    }
}
