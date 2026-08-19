package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier,
) = MobileAuditLogEntryList(args, modifier)
