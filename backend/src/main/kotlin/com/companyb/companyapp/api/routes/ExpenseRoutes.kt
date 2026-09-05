package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.RestoreExpenseRequest
import com.companyb.companyapp.dto.UpdateExpenseRequest
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.service.ExpenseService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HandlerType
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
    path = ApiRoutes.EXPENSES,
    methods = [HttpMethod.GET],
    queryParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "expenses_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ExpenseResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.EXPENSES,
    methods = [HttpMethod.POST],
    operationId = "expenses_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateExpenseRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ExpenseResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.EXPENSE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "expenseId", type = UUID::class, required = true)],
    operationId = "expense_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateExpenseRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ExpenseResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.EXPENSE_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [OpenApiParam(name = "expenseId", type = UUID::class, required = true)],
    operationId = "expense_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = DeleteExpenseRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ExpenseResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.EXPENSE_RESTORE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "expenseId", type = UUID::class, required = true)],
    operationId = "expense_restore",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RestoreExpenseRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ExpenseResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ExpenseRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        // --- Capability filters: EDIT_BRANCH_DATA (BRANCH or BRANCH_DAY — #157) ---

        config.routes.before(ApiRoutes.EXPENSES) { context ->
            val branchDayId =
                when (context.method()) {
                    HandlerType.POST -> {
                        val request = context.bodyAsClass<CreateExpenseRequest>()
                        uuidOrThrow(request.branchDayId, "branch day id")
                    }

                    HandlerType.GET -> {
                        context.uuidFromQuery("branchDayId")
                    }

                    else -> {
                        return@before
                    }
                }
            CapabilityFilter.requireBranchOrBranchDayCapability(context, branchDayId)
        }

        config.routes.before(ApiRoutes.EXPENSE_PATH) { context ->
            val expenseId = context.pathParamAsUuid("expenseId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForExpense(context, expenseId)
        }

        // #114 exact-segment lesson: before(ApiRoutes.EXPENSE_PATH) does NOT fire on the
        // 4-segment restore route — the restore filter is its own (record-scoped EDIT_BRANCH_DATA
        // via the expense's branch day; 404 for missing expense keeps the filter from running the
        // handler against a phantom).
        config.routes.before(ApiRoutes.EXPENSE_RESTORE_PATH) { context ->
            val expenseId = context.pathParamAsUuid("expenseId")
            CapabilityFilter.requireBranchOrBranchDayCapabilityForExpense(context, expenseId)
        }

        // --- Route handlers ---

        config.routes.post(ApiRoutes.EXPENSES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateExpenseRequest>()

            val id = uuidOrThrow(request.id, "expense id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val amount = parsePositiveBigDecimal(request.amount, "amount")
            val category =
                ExpenseCategory.valueOf(request.category.name)

            val expense =
                ExpenseService.create(
                    callerId = callerId,
                    id = id,
                    branchDayId = branchDayId,
                    amount = amount,
                    category = category,
                    notes = request.notes,
                    reason = request.reason,
                )

            context.status(HttpStatus.CREATED)
            context.json(expense.toResponse())
        }

        config.routes.patch(ApiRoutes.EXPENSE_PATH) { context ->
            val callerId = context.callerUuid()
            val expenseId = context.pathParamAsUuid("expenseId")
            val request = context.bodyAsClass<UpdateExpenseRequest>()

            val amount = parsePositiveBigDecimal(request.amount, "amount")
            val category =
                ExpenseCategory.valueOf(request.category.name)

            val expense =
                ExpenseService.update(
                    callerId = callerId,
                    expenseId = expenseId,
                    amount = amount,
                    category = category,
                    notes = request.notes,
                    expectedVersion = request.expectedVersion,
                    reason = request.reason,
                )

            context.status(HttpStatus.OK)
            context.json(expense.toResponse())
        }

        config.routes.delete(ApiRoutes.EXPENSE_PATH) { context ->
            val callerId = context.callerUuid()
            val expenseId = context.pathParamAsUuid("expenseId")
            val request = context.bodyAsClass<DeleteExpenseRequest>()

            if (request.reason.isBlank()) throw BadRequestResponse("Reason is required for expense deletion")

            val expense =
                ExpenseService.softDelete(
                    callerId = callerId,
                    expenseId = expenseId,
                    reason = request.reason,
                )

            context.status(HttpStatus.OK)
            context.json(expense.toResponse())
        }

        config.routes.post(ApiRoutes.EXPENSE_RESTORE_PATH) { context ->
            val callerId = context.callerUuid()
            val expenseId = context.pathParamAsUuid("expenseId")
            val request = context.bodyAsClass<RestoreExpenseRequest>()

            val expense =
                ExpenseService.restore(
                    callerId = callerId,
                    expenseId = expenseId,
                    reason = request.reason,
                )

            context.status(HttpStatus.OK)
            context.json(expense.toResponse())
        }

        config.routes.get(ApiRoutes.EXPENSES) { context ->
            val callerId = context.callerUuid()
            val branchDayId = context.uuidFromQuery("branchDayId")

            val expenses = ExpenseService.findByBranchDayId(callerId, branchDayId)

            context.status(HttpStatus.OK)
            context.json(expenses.map { it.toResponse() })
        }
    }

    private fun Expense.toResponse(): ExpenseResponse =
        ExpenseResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            amount = amount.toPlainString(),
            category =
                com.companyb.companyapp.domain.ExpenseCategory
                    .valueOf(category.name),
            notes = notes,
            createdBy = createdBy.toString(),
            createdAt = createdAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
            deletedReason = deletedReason,
            version = version,
        )
}
