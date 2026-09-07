package com.companyb.companyapp.remittance

// #561 — kept as the identity bundle per the ticket invariant: the (remittanceId,
// branchId) pair prevents mismatched-ID detail surfaces; the shared VM rides along
// because the detail lifetime is entry-scoped (one VM per detail entry).

/** Identity bundle for one remittance detail surface: the id, its branch, and the shared VM. */
data class RemittanceDetailArgs(
    val remittanceId: String,
    val branchId: String?,
    val viewModel: RemittanceViewModel,
)
