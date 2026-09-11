package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.commerce.ProductCategoryTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
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

    /**
     * Inserts a product-sale row mirroring production derivation (#903).
     *
     * Production (`ProductSaleRepository.insertSaleInTransaction`) copies `unitPrice`,
     * `commissionAmount`, and `name` from the live product card and derives
     * `total = unitPrice × quantity`. Null overrides default to the referenced card so
     * aggregates reading `total_amount_at_time` (daily summary, remittance PRODUCT
     * lines) compute over production-shaped money.
     *
     * @param unitPrice per-unit price; null resolves to the card's `unitPrice`.
     * @param totalAmount per-sale total; null derives `unitPrice × quantity`. An explicit
     * value differing from the derived total throws unless [allowIncoherentTotal] is set.
     * @param commissionAmount per-unit commission (production multiplies by quantity, then
     * splits per user — see `CommissionService.splitCommission`); null resolves to the
     * card's `commissionAmount`.
     * @param productName null resolves to the card's `name`.
     * @param allowIncoherentTotal opt-in for the rare test that deliberately needs an
     * off-shape total; reads as deliberate in review.
     *
     * Explicit price/commission/name pins intentionally create off-card shapes — use them
     * only where the test needs that shape (e.g. commission-split math with a
     * non-card commission) and keep totals coherent otherwise.
     */
    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestProductSale(
        id: UUID,
        branchDayId: UUID,
        productId: UUID,
        handledBy: UUID,
        clientId: UUID? = null,
        quantity: Int = 1,
        unitPrice: BigDecimal? = null,
        totalAmount: BigDecimal? = null,
        commissionAmount: BigDecimal? = null,
        productName: String? = null,
        isWalkIn: Boolean = true,
        allowIncoherentTotal: Boolean = false,
    ) {
        transaction {
            val card =
                ProductTable
                    .selectAll()
                    .where { ProductTable.id eq productId }
                    .singleOrNull()
                    ?: error("product card not found for $productId — seed via insertTestProduct first (#903)")
            val resolvedUnitPrice = unitPrice ?: card[ProductTable.unitPrice]
            val resolvedCommission = commissionAmount ?: card[ProductTable.commissionAmount]
            val resolvedName = productName ?: card[ProductTable.name]
            val derivedTotal = resolvedUnitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
            val resolvedTotal = totalAmount ?: derivedTotal
            require(allowIncoherentTotal || resolvedTotal.compareTo(derivedTotal) == 0) {
                "incoherent sale total $resolvedTotal != unit price $resolvedUnitPrice × quantity $quantity" +
                    " — omit totalAmount to derive it or pass allowIncoherentTotal=true to pin deliberately (#903)"
            }
            ProductSaleTable.insert {
                it[ProductSaleTable.id] = id
                it[ProductSaleTable.branchDayId] = branchDayId
                it[ProductSaleTable.productId] = productId
                it[ProductSaleTable.quantity] = quantity
                it[ProductSaleTable.isWalkIn] = isWalkIn
                it[ProductSaleTable.handledBy] = handledBy
                it[ProductSaleTable.unitPriceAtTime] = resolvedUnitPrice
                it[ProductSaleTable.totalAmountAtTime] = resolvedTotal
                it[ProductSaleTable.commissionAmountAtTime] = resolvedCommission
                it[ProductSaleTable.productName] = resolvedName
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
        workBranchDayId: UUID = branchDayId,
        payingBranchDayId: UUID = branchDayId,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = TestFixtures.uuid()
                it[CompensationTable.workBranchDayId] = workBranchDayId
                it[CompensationTable.payingBranchDayId] = payingBranchDayId
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
