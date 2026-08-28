@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler

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
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun SessionCreateBackHandler(enabled: Boolean) {
    BackHandler(enabled = enabled) {}
}
