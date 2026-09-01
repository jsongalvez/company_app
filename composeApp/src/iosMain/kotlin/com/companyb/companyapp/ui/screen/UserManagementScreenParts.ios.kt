package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import com.companyb.companyapp.viewmodel.UserSlotRow

@Composable
actual fun UserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    onSwap: (assignmentIdA: String, assignmentIdB: String) -> Unit,
    onEditSlot: (row: UserSlotRow) -> Unit,
    errors: List<String>,
) = MobileUserSlotOrderList(branchName, rows, mutationsDisabled, onSwap, onEditSlot, errors)
