package com.companyb.companyapp.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.companyb.companyapp.dto.ClientResponse

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
actual fun ClientDetailBackHandler(enabled: Boolean) {
    BackHandler(enabled = enabled) {}
}
