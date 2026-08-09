package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import com.companyb.companyapp.dto.RemittanceResponse

// #120 D1 — smallest-divergent-subtree per #95/#99 (AuditLogScreen D11 precedent): shared chrome
// (tabs, header, create popup, state branches) lives in RemittanceListScreen (commonMain); only
// the row list rendering diverges — desktop dense table / mobile card list. VMs stay commonMain.
@Composable
expect fun RemittanceRowList(
    remittances: List<RemittanceResponse>,
    onRemittanceClick: (RemittanceResponse) -> Unit,
)
