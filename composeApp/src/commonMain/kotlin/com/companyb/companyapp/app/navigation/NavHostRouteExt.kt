package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.commerce.catalog.ProductCatalogScreen
import com.companyb.companyapp.commerce.catalog.ProductViewModel
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.ui.RouteGateCard
import com.companyb.companyapp.workforce.relief.DelegateViewModel
import com.companyb.companyapp.workforce.relief.MedicalMissionDelegateScreen
import com.companyb.companyapp.workforce.team.UserViewModel
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
    val entry = state
    val routeClass = entry?.destination?.route?.let { ROUTES_BY_SERIAL_NAME[serialNameFromPattern(it)] }
    return if (entry != null && routeClass != null) entry.toRoute(routeClass) else null
}

fun NavHostController.previousRoute(): Route? {
    val entry = previousBackStackEntry
    val routeClass = entry?.destination?.route?.let { ROUTES_BY_SERIAL_NAME[serialNameFromPattern(it)] }
    return if (entry != null && routeClass != null) entry.toRoute(routeClass) else null
}

@Composable
internal fun rememberSessionCreateNavigationLock(): MutableState<Boolean> {
    val locked = remember { mutableStateOf(false) }
    val snapshot by AppSessionState.snapshot.collectAsState()
    val authenticatedUser = snapshot.user
    LaunchedEffect(authenticatedUser) {
        if (authenticatedUser == null) locked.value = false
    }
    return locked
}

internal fun serialNameFromPattern(pattern: String): String = pattern.substringBefore('?').substringBefore('/')

internal fun shellNavigationEnabled(
    sessionCreateNavigationLocked: Boolean,
    clientMutationInFlight: Boolean,
): Boolean = !sessionCreateNavigationLocked && !clientMutationInFlight

@Composable
internal fun MedicalMissionDelegatesDestination(apiClient: ApiClient) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    if (capabilities.hasCapability(
            CapabilityCodes.ASSIGN_DELEGATE,
            CapabilityContextType.GLOBAL,
            GLOBAL_CAPABILITY_CONTEXT_ID,
        )
    ) {
        val delegateViewModel: DelegateViewModel = viewModel { DelegateViewModel(apiClient) }
        val userViewModel: UserViewModel = viewModel { UserViewModel(apiClient) }
        MedicalMissionDelegateScreen(
            delegateViewModel = delegateViewModel,
            userViewModel = userViewModel,
        )
    } else {
        RouteGateCard(label = "Mission Delegates")
    }
}

// #441 — shared catalog admin; gate mirrors ProductRoutes/ProductCategoryRoutes exactly
// (GLOBAL MANAGE_CATALOG, #436 — no BRANCH leg, #131 strictness). Backend authoritative.
@Composable
internal fun ProductCatalogDestination(apiClient: ApiClient) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    if (capabilities.hasCapability(
            CapabilityCodes.MANAGE_CATALOG,
            CapabilityContextType.GLOBAL,
            GLOBAL_CAPABILITY_CONTEXT_ID,
        )
    ) {
        val productViewModel: ProductViewModel = viewModel { ProductViewModel(apiClient) }
        ProductCatalogScreen(viewModel = productViewModel)
    } else {
        RouteGateCard(label = "Product Catalog")
    }
}

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
        Route.MedicalMissionDelegates to Route.MedicalMissionDelegates::class,
        Route.ProductCatalog to Route.ProductCatalog::class,
        Route.Profile to Route.Profile::class,
        Route.SessionCreate to Route.SessionCreate::class,
        Route.SessionDetail to Route.SessionDetail::class,
    ).mapKeys {
        it.value
            .serializer()
            .descriptor.serialName
    }
