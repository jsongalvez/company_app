package com.companyb.companyapp.workforce.team

import androidx.compose.runtime.Composable
import com.companyb.companyapp.workforce.team.UserSlotRow

@Composable
actual fun UserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    callbacks: SlotOrderCallbacks,
    errors: List<String>,
) = MobileUserSlotOrderList(branchName, rows, mutationsDisabled, callbacks, errors)
