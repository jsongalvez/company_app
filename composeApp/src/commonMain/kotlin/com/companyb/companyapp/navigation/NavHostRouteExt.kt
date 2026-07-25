package com.companyb.companyapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute

/**
 * The current typed [Route] of the [NavHostController], or null if no destination is set.
 * Hoisted to commonMain so both AppNavHost actuals (androidMain + desktopMain) and
 * [DrawerContent] can call the same derivation — ADR-0020's "shared chrome stays in commonMain;
 * only the divergent piece gets expect/actual" applied to the smallest divergent subtree here
 * (zero divergence: serveral Navigation Compose API is already KMP-cross-target).
 */
@Composable
fun NavHostController.currentRoute(): Route? {
    val entry by currentBackStackEntryAsState()
    return entry?.toRoute<Route>()
}
