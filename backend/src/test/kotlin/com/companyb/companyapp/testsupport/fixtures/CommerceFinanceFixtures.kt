package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.commerce.ProductCategoryTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Commerce and finance fixtures for map #533 (#552).
 *
 * Owns catalog, sale, compensation and expense rows only.
 */
object CommerceFinanceFixtures {
    fun insertTestCategory(
        id: UUID,
        name: String = "Test Category ${id.toString().take(8)}",
    ) {
        transaction {
            ProductCategoryTable.insert {
                it[ProductCategoryTable.id] = id
                it[ProductCategoryTable.name] = name
            }
        }
    }

    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestProduct(
        id: UUID,
        name: String = "Test Product ${id.toString().take(8)}",
        categoryId: UUID,
        unitPrice: BigDecimal = BigDecimal("100.00"),
        commissionAmount: BigDecimal = BigDecimal("10.00"),
        reorderPoint: Int? = null,
    ) {
        transaction {
            ProductTable.insert {
                it[ProductTable.id] = id
                it[ProductTable.name] = name
                it[ProductTable.productCategoryId] = categoryId
                it[ProductTable.unitPrice] = unitPrice
                it[ProductTable.commissionAmount] = commissionAmount
                if (reorderPoint != null) it[ProductTable.reorderPoint] = reorderPoint
            }
        }
    }

    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestProductSale(
        id: UUID,
        branchDayId: UUID,
        productId: UUID,
        handledBy: UUID,
        clientId: UUID? = null,
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("100.00"),
        totalAmount: BigDecimal = BigDecimal("100.00"),
        commissionAmount: BigDecimal = BigDecimal("10.00"),
        productName: String = "Test Product",
        isWalkIn: Boolean = true,
    ) {
        transaction {
            ProductSaleTable.insert {
                it[ProductSaleTable.id] = id
                it[ProductSaleTable.branchDayId] = branchDayId
                it[ProductSaleTable.productId] = productId
                it[ProductSaleTable.quantity] = quantity
                it[ProductSaleTable.isWalkIn] = isWalkIn
                it[ProductSaleTable.handledBy] = handledBy
                it[ProductSaleTable.unitPriceAtTime] = unitPrice
                it[ProductSaleTable.totalAmountAtTime] = totalAmount
                it[ProductSaleTable.commissionAmountAtTime] = commissionAmount
                it[ProductSaleTable.productName] = productName
                if (clientId != null) it[ProductSaleTable.clientId] = clientId
            }
        }
    }

    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestCompensation(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        assignedBy: UUID,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = TestFixtures.uuid()
                it[CompensationTable.workBranchDayId] = branchDayId
                it[CompensationTable.payingBranchDayId] = branchDayId
                it[CompensationTable.userId] = userId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = assignedBy
            }
        }
    }

    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestExpense(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        deleted: Boolean = false,
    ) {
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = TestFixtures.uuid()
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = userId
                it[ExpenseTable.notes] = "Test expense"
                if (deleted) {
                    it[ExpenseTable.deletedBy] = userId
                    it[ExpenseTable.deletedAt] = CurrentTimestampWithTimeZone
                }
            }
        }
    }
}
