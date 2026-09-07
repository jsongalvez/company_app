package com.companyb.companyapp.session.create

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun SessionCreateBodyLayout(
    args: SessionCreateBodyArgs,
    onCreateNewClick: () -> Unit,
    modifier: Modifier,
) = SessionCreateMobileBody(
    args = args,
    onCreateNewClick = onCreateNewClick,
    modifier = modifier,
)

@Composable
internal actual fun SessionCreateBackHandler(enabled: Boolean) {
    BackHandler(enabled = enabled) {}
}
