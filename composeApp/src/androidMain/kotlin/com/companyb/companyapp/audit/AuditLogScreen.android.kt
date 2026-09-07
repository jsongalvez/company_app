package com.companyb.companyapp.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier,
) = MobileAuditLogEntryList(args, modifier)
