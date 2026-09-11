package com.companyb.companyapp.contracts.commerce

import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`): old clients decode newer server values as UNKNOWN instead of failing
 * the whole response. Never persisted, never sent.
 */
@Serializable
enum class InventoryMovementReason { RESTOCK, SALE, TESTER, SAMPLE, MISSING, ADJUSTMENT, UNKNOWN }

@Serializable
data class CreateProductSaleRequest(
    val id: String,
    val branchDayId: String,
    val sessionId: String? = null,
    val clientId: String? = null,
    val isWalkIn: Boolean,
    val productId: String,
    val quantity: Int,
    val expectedVersion: Int,
    val reason: String? = null,
)

@Serializable
data class ProductSaleResponse(
    val id: String,
    val branchDayId: String,
    val sessionId: String?,
    val clientId: String?,
    val isWalkIn: Boolean,
    val productId: String,
    val productName: String,
    val handledBy: String,
    val quantity: Int,
    val unitPriceAtTime: String,
    val totalAmountAtTime: String,
    val commissionAmountAtTime: String,
    val soldAt: String,
)

@Serializable
data class AddInventoryCardRequest(
    val productId: String,
)

@Serializable
data class RestockRequest(
    val id: String,
    val quantity: Int,
    val branchDayId: String,
    val editReason: String? = null,
)

@Serializable
data class BranchInventoryResponse(
    val id: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val currentStock: Int,
    val version: Int,
    val unitPrice: String,
    val commissionAmount: String,
    // #442 — 5-value inventory sheet breakdown (owner 5-value decision 2026-09-03).
    // Available is the live sellable count (equals currentStock, the low-stock authority).
    // Stock = RESTOCK sum baseline; Sales = SALE units (positive); TesterSample = TESTER+SAMPLE
    // combined display (movements stay separate); Missing = MISSING units (positive, notes-mandated);
    // Adjustment = signed ADJUSTMENT net so Available reconciles when corrections exist:
    // Available = Stock - Sales - TesterSample - Missing + Adjustment.
    // Branch-scoped all-history; legacy rows included.
    val available: Int = 0,
    val stock: Int = 0,
    val sales: Int = 0,
    val testerSample: Int = 0,
    val missing: Int = 0,
    val adjustment: Int = 0,
)

@Serializable
data class InventoryMovementRequest(
    val movementId: String,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val notes: String? = null,
    val branchDayId: String,
    val expectedVersion: Int,
    val editReason: String? = null,
)

@Serializable
data class InventoryMovementResponse(
    val id: String,
    val productId: String,
    val branchId: String,
    val branchDayId: String,
    val reason: InventoryMovementReason = InventoryMovementReason.UNKNOWN,
    val quantityChange: Int,
    val movedBy: String,
    val movedAt: String,
    val notes: String?,
)
