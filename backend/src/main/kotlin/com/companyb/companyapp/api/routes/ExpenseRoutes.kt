package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.service.ExpenseService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.util.UUID

object ExpenseRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        // --- Capability filters: enforce EDIT_BRANCH_DATA before the route handler runs ---

        config.routes.before("/api/expenses") { context ->
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
            CapabilityFilter.requireBranchCapability(context, branchDayId)
        }

        config.routes.before("/api/expenses/{expenseId}") { context ->
            val expenseId = context.pathParamAsUuid("expenseId")
            CapabilityFilter.requireBranchCapabilityForExpense(context, expenseId)
        }

        // --- Route handlers ---

        config.routes.post("/api/expenses") { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateExpenseRequest>()

            val id = uuidOrThrow(request.id, "expense id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }
            if (amount <= BigDecimal.ZERO) throw BadRequestResponse("Amount must be positive")
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
            val callerId = context.callerUuid()
            val expenseId = context.pathParamAsUuid("expenseId")
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
            category = category.name,
            notes = notes,
            createdBy = createdBy.toString(),
            createdAt = createdAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
        )
}
