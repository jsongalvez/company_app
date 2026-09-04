package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.viewmodel.RemittanceViewModel

/** Identity bundle for one remittance detail surface: the id, its branch, and the shared VM. */
data class RemittanceDetailArgs(
    val remittanceId: String,
    val branchId: String?,
    val viewModel: RemittanceViewModel,
)
