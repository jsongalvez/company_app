package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun SessionList(
    args: SessionListArgs,
    modifier: Modifier,
) = MobileSessionList(args, modifier)
