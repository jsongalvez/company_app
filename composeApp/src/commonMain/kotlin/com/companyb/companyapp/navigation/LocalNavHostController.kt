package com.companyb.companyapp.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/**
 * CompositionLocal providing [NavHostController] to [DrawerContent]. Set by both
 * AppNavHost actuals (androidMain + desktopMain) via `CompositionLocalProvider` around the
 * drawer shell's `*NavigationDrawer` container; consumed by [DrawerContent] for current-route
 * selection tracking + on-click navigation. Lets [DrawerContent] remain a zero-arg
 * `@Composable (Modifier) -> Unit` per the locked #96 Q7 slot-type seam — the alternative
 * (passing NavController directly) would re-narrow the locked signature.
 *
 * Static cache: one NavController per App lifecycle (provided once at the AppNavHost root);
 * the drawer reads it on every recomposition, never wraps it in its own state.
 */
val LocalNavHostController =
    staticCompositionLocalOf<NavHostController> {
        error("LocalNavHostController not provided — wrap AppNavHost's content in CompositionLocalProvider")
    }
