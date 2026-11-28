package com.companyb.companyapp.branchday

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.delete
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.contracts.branchday.SiteHubCertRequest
import com.companyb.companyapp.contracts.branchday.SiteHubCertResponse
import com.companyb.companyapp.contracts.branchday.SiteHubLaptopRequest
import com.companyb.companyapp.contracts.branchday.SiteHubLaptopResponse
import com.companyb.companyapp.contracts.branchday.SiteHubLaptopsResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.config.JavalinConfig
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
        OpenApiResponse(status = "429", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SITE_HUB_LAPTOPS,
    methods = [HttpMethod.GET],
    operationId = "site_hub_list_laptops",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SiteHubLaptopsResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SITE_HUB_LAPTOP_PATH,
    methods = [HttpMethod.DELETE],
    operationId = "site_hub_revoke_laptop",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "laptopId", required = true)],
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SITE_HUB_CERT,
    methods = [HttpMethod.POST],
    operationId = "site_hub_cert",
    security = [],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SiteHubCertRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SiteHubCertResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "502", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "503", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object SiteHubRoutes {
    private const val MAX_NAME = 80
    private const val LAPTOP_ID_PARAM = "laptopId"

    fun register(config: JavalinConfig) {
        config.post(
            ApiRoutes.SITE_HUB_LAPTOPS,
            Gate.Staff("any active staff member may pair a site hub laptop"),
        ) { ctx ->
            val name =
                ctx
                    .bodyAsClass<SiteHubLaptopRequest>()
                    .name
                    .trim()
                    .take(MAX_NAME)
            val caller = ctx.callerUuid()
            when (val res = SiteHubCerts.pair(caller, name)) {
                is SiteHubCerts.PairResult.Paired -> {
                    logger.info { "[SITE-HUB] laptop ($name, id=${res.id}) paired by $caller" }
                    ctx.json(SiteHubLaptopResponse(res.id.toString(), res.token))
                }

                SiteHubCerts.PairResult.TooMany -> {
                    refuse(ctx, HttpStatus.TOO_MANY_REQUESTS, "Too many laptops paired this hour. Wait and try again.")
                }
            }
        }
        config.get(ApiRoutes.SITE_HUB_LAPTOPS, Gate.Can(Act.MANAGE_USERS)) { ctx ->
            ctx.json(SiteHubLaptopsResponse(SiteHubCerts.listLaptops()))
        }
        config.delete(ApiRoutes.SITE_HUB_LAPTOP_PATH, Gate.Can(Act.MANAGE_USERS)) { ctx ->
            val laptopId = ctx.pathParamAsUuid(LAPTOP_ID_PARAM)
            val caller = ctx.callerUuid()
            if (SiteHubCerts.revoke(laptopId, caller)) {
                logger.info { "[SITE-HUB] laptop $laptopId revoked by $caller" }
                ctx.status(HttpStatus.NO_CONTENT)
            } else {
                refuse(ctx, HttpStatus.NOT_FOUND, "Laptop not found.")
            }
        }
        config.post(ApiRoutes.SITE_HUB_CERT, Gate.Open("the laptop token is the key")) { ctx ->
            val req = ctx.bodyAsClass<SiteHubCertRequest>()
            answerCert(ctx, SiteHubCerts.cert(req.token))
        }
    }

    private fun answerCert(
        ctx: Context,
        result: SiteHubCerts.CertResult,
    ) {
        when (result) {
            is SiteHubCerts.CertResult.Ready -> {
                val c = result.cert
                ctx.json(SiteHubCertResponse(c.certPem, c.keyPem, c.notBefore.toString(), c.notAfter.toString()))
            }

            SiteHubCerts.CertResult.BadToken -> {
                refuse(ctx, HttpStatus.UNAUTHORIZED, "This laptop isn't paired. Sign in again in the site hub.")
            }

            SiteHubCerts.CertResult.NotSetUp -> {
                refuse(ctx, HttpStatus.SERVICE_UNAVAILABLE, "Site hub certificates are not set up on the server yet.")
            }

            is SiteHubCerts.CertResult.Failed -> {
                refuse(ctx, HttpStatus.BAD_GATEWAY, "Couldn't get the site hub certificate. Try again later.")
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
