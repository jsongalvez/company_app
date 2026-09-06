package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.service.ProductCategoryService
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
    path = ApiRoutes.PRODUCT_CATEGORIES,
    methods = [HttpMethod.GET],
    operationId = "product_categories_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ProductCategoryResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_CATEGORIES,
    methods = [HttpMethod.POST],
    operationId = "product_categories_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateProductCategoryRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ProductCategoryResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_CATEGORY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "categoryId", type = UUID::class, required = true)],
    operationId = "product_category",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductCategoryResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ProductCategoryRoutes {
    private const val CATEGORY_ID_PARAM = "categoryId"

    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.PRODUCT_CATEGORIES) { context ->
            CapabilityFilter.requireManageCatalog(
                context,
                "MANAGE_CATALOG capability required to manage product categories",
            )
        }

        config.routes.before(ApiRoutes.PRODUCT_CATEGORY_PATH) { context ->
            CapabilityFilter.requireManageCatalog(
                context,
                "MANAGE_CATALOG capability required to manage product categories",
            )
        }

        config.routes.post(ApiRoutes.PRODUCT_CATEGORIES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateProductCategoryRequest>()
            val categoryId = uuidOrThrow(request.id, "category id")
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Category name is required")
            val category =
                ProductCategoryService.create(
                    callerId = callerId,
                    id = categoryId,
                    name = name,
                )

            context.status(HttpStatus.CREATED)
            context.json(category.toResponse())
        }

        config.routes.get(ApiRoutes.PRODUCT_CATEGORIES) { context ->
            context.json(ProductCategoryService.findAll().map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.PRODUCT_CATEGORY_PATH) { context ->
            val categoryId = context.pathParamAsUuid(CATEGORY_ID_PARAM)
            context.json(ProductCategoryService.findById(categoryId).toResponse())
        }
    }

    private fun ProductCategory.toResponse(): ProductCategoryResponse =
        ProductCategoryResponse(
            id = id.toString(),
            name = name,
        )
}
