package com.companyb.companyapp.session.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun SessionList(
    args: SessionListArgs,
    modifier: Modifier,
) = MobileSessionList(args, modifier)
