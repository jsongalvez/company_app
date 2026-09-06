package com.companyb.companyapp.commerce

import java.util.UUID

/**
 * Named commerce-read seams for cross-owner consumers (map #533 #543): commission sale
 * validation/aggregation and remittance line validation. Thin delegations to the internal
 * commerce stores — not a facade over every method. Service-to-service calls need no
 * architecture allowlist entry; direct store imports from other owners stay banned.
 */
object CommerceReads {
    /** Existence read for commission inclusion validation and remittance line checks. */
    fun findSaleById(saleId: UUID): ProductSale? = ProductSaleRepository.findById(saleId)

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findSaleByIdInTransaction(saleId: UUID): ProductSale? = ProductSaleRepository.findByIdInTransaction(saleId)

    /** Non-voided day sales for the shared commission aggregation — runs on the caller's transaction. */
    fun findNonVoidedSalesByBranchDayInTransaction(branchDayId: UUID): List<ProductSale> =
        ProductSaleRepository.findNonVoidedSalesByBranchDayInTransaction(branchDayId)
}
