package com.companyb.companyapp.remittance

import androidx.compose.runtime.Composable
import com.companyb.companyapp.dto.RemittanceResponse

@Composable
actual fun RemittanceRowList(
    remittances: List<RemittanceResponse>,
    onRemittanceClick: (RemittanceResponse) -> Unit,
) = MobileRemittanceRowList(remittances, onRemittanceClick)
