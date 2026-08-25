package com.companyb.companyapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * The current typed [Route] of the [NavHostController], or null if no destination is set.
 * Hoisted to commonMain so both AppNavHost actuals (androidMain + desktopMain) and
 * DrawerContent can call the same derivation — ADR-0020's "shared chrome stays in commonMain;
 * only the divergent piece gets expect/actual" applied to the smallest divergent subtree here
 * (zero divergence: the Navigation Compose API is already KMP-cross-target).
 *
 * Polymorphic decode is deliberately avoided: `entry.toRoute<Route>()` (the sealed-class
 * serializer) crashes with "Polymorphic value has not been read for class null". Navigation
 * encodes route args through the CONCRETE subclass serializer, so the entry's bundle never
 * carries the "type"/"value" keys the polymorphic decoder requires — the start destination
 * entry gets an empty bundle and arg-carrying entries get only their arg keys. Navigation's
 * type-safe API supports concrete classes only: resolve the concrete class from the
 * destination's route pattern — strip the query-arg suffix after '?' and the path-arg
 * suffix after '/' to recover the serial name — then decode non-polymorphically.
 */
@Composable
fun NavHostController.currentRoute(): Route? {
    val state by currentBackStackEntryAsState()
    val entry = state ?: return null
    val pattern = entry.destination.route ?: return null
    val routeClass = ROUTES_BY_SERIAL_NAME[serialNameFromPattern(pattern)] ?: return null
    return entry.toRoute(routeClass)
}

internal fun serialNameFromPattern(pattern: String): String = pattern.substringBefore('?').substringBefore('/')

@OptIn(InternalSerializationApi::class)
internal val ROUTES_BY_SERIAL_NAME: Map<String, KClass<out Route>> =
    mapOf(
        Route.Login to Route.Login::class,
        Route.AcceptInvite to Route.AcceptInvite::class,
        Route.ForgotPassword to Route.ForgotPassword::class,
        Route.BranchSelect to Route.BranchSelect::class,
        Route.Dashboard() to Route.Dashboard::class,
        Route.Clients to Route.Clients::class,
        Route.ClientDetail to Route.ClientDetail::class,
        Route.Inventory to Route.Inventory::class,
        Route.BaseRates to Route.BaseRates::class,
        Route.Finance to Route.Finance::class,
        Route.RemittanceList to Route.RemittanceList::class,
        Route.RemittanceDetail to Route.RemittanceDetail::class,
        Route.Notifications to Route.Notifications::class,
        Route.AuditLog to Route.AuditLog::class,
        Route.AuditLogHistory to Route.AuditLogHistory::class,
        Route.UserManagement to Route.UserManagement::class,
        Route.Profile to Route.Profile::class,
        Route.SessionCreate to Route.SessionCreate::class,
        Route.SessionDetail to Route.SessionDetail::class,
    ).mapKeys {
        it.value
            .serializer()
            .descriptor.serialName
    }
