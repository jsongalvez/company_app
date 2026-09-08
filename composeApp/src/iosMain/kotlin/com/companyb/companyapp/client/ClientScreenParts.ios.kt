@file:Suppress("DEPRECATION") // #599 ios uses ui.backhandler (no activity-compose alternative)

package com.companyb.companyapp.client

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.companyb.companyapp.contracts.client.ClientResponse

@Composable
actual fun ClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
) = MobileClientResultList(results, onClientClick)

@Composable
actual fun ClientDetailLayout(
    identity: @Composable ColumnScope.() -> Unit,
    contactHealth: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) = MobileClientDetailLayout(identity, contactHealth, actions)

@Composable
@OptIn(ExperimentalComposeUiApi::class)
actual fun ClientDetailBackHandler(enabled: Boolean) {
    BackHandler(enabled = enabled) {}
}
