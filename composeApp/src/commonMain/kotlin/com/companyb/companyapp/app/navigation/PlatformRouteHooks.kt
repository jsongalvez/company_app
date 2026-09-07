package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable

/**
 * #456 — the shared route-registration seam. One registration of every [Route]
 * (routes + gates) feeds both platform shells; the shells retain only chrome
 * (drawer/top-bar), master-detail presentation, and remittance desk differences.
 * Adding a screen touches this file once — desktop vs mobile cannot desync.
 *
 * The seam is real (two adapters vary across it: the desktop and mobile shells
 * below pass different [PlatformRouteHooks]); everything else is identical.
 */
class PlatformRouteHooks(
    val dashboardLive: @Composable () -> Unit,
    val onClientProfileClick: ((String) -> Unit)? = null,
    val onDeskQueueNavigate: ((currentId: String, id: String) -> Unit)? = null,
)
