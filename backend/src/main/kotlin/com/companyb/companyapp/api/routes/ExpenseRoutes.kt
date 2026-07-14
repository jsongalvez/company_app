package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.service.ExpenseService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.util.UUID

object ExpenseRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/expenses") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateExpenseRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid expense id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }
            val category =
                runCatching { ExpenseCategory.valueOf(request.category.uppercase()) }
                    .getOrElse { throw BadRequestResponse("Invalid expense category") }

            val expense =
                ExpenseService.create(
                    callerId = callerId,
                    id = id,
                    branchDayId = branchDayId,
                    amount = amount,
                    category = category,
                    notes = request.notes,
                )

            context.status(HttpStatus.CREATED)
            context.json(expense.toResponse())
        }

        config.routes.delete("/api/expenses/{expenseId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val expenseId =
                runCatching { UUID.fromString(context.pathParam("expenseId")) }
                    .getOrElse { throw BadRequestResponse("Invalid expense id") }
            val request = context.bodyAsClass<DeleteExpenseRequest>()

            val expense =
                ExpenseService.softDelete(
                    callerId = callerId,
                    expenseId = expenseId,
                    reason = request.reason,
                )

            context.status(HttpStatus.OK)
            context.json(expense.toResponse())
        }

        config.routes.get("/api/expenses") { context ->
            val branchDayIdParam =
                context.queryParam("branchDayId")
                    ?: throw BadRequestResponse("branchDayId query param is required")
            val branchDayId =
                runCatching { UUID.fromString(branchDayIdParam) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }

            val expenses = ExpenseService.findByBranchDayId(branchDayId)

            context.status(HttpStatus.OK)
            context.json(expenses.map { it.toResponse() })
        }
    }

    private fun Expense.toResponse(): ExpenseResponse =
        ExpenseResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            amount = amount.toPlainString(),
            category = category.name,
            notes = notes,
            createdBy = createdBy.toString(),
            createdAt = createdAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
        )
}
