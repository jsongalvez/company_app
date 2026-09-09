package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.AuthViewModel
import com.companyb.companyapp.app.ClockContext
import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.SessionBootstrapViewModel
import com.companyb.companyapp.app.auth.AcceptInviteScreen
import com.companyb.companyapp.app.auth.ForgotPasswordScreen
import com.companyb.companyapp.app.auth.LoginNavActions
import com.companyb.companyapp.app.auth.LoginScreen
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.app.hasDayGrant
import com.companyb.companyapp.audit.AuditLogHistoryScreen
import com.companyb.companyapp.audit.AuditLogScreen
import com.companyb.companyapp.audit.AuditLogViewModel
import com.companyb.companyapp.client.ClientViewModel
import com.companyb.companyapp.client.ClientsScreen
import com.companyb.companyapp.client.SaleClientSearchViewModel
import com.companyb.companyapp.commerce.catalog.ProductViewModel
import com.companyb.companyapp.commerce.stock.InventoryScreen
import com.companyb.companyapp.commerce.stock.InventoryViewModel
import com.companyb.companyapp.commerce.stock.ProductSaleViewModel
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.finance.FinanceReportsScreen
import com.companyb.companyapp.finance.FinanceReportsViewModel
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.remittance.RemittanceDetailArgs
import com.companyb.companyapp.remittance.RemittanceDetailScreen
import com.companyb.companyapp.remittance.RemittanceListScreen
import com.companyb.companyapp.remittance.RemittanceViewModel
import com.companyb.companyapp.session.detail.SessionDetailScreen
import com.companyb.companyapp.session.detail.SessionDetailViewModel
import com.companyb.companyapp.session.rates.BaseRatesScreen
import com.companyb.companyapp.session.rates.SessionRatesViewModel
import com.companyb.companyapp.ui.RouteGateCard
import com.companyb.companyapp.workforce.branch.BranchSelectScreen
import com.companyb.companyapp.workforce.branch.BranchSelectViewModel
import com.companyb.companyapp.workforce.relief.ReliefAccessViewModel
import com.companyb.companyapp.workforce.relief.ReliefDayScreen
import com.companyb.companyapp.workforce.relief.ReliefDayViewModel
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

// #456 — the shared route-registration seam lives here; the cross-shell hook type owns
// its own file so this registration file holds only graph functions (#460 governance).

// #456 — shared start destination (#94-grad: derives from the VALIDATED session,
// not raw token presence).
internal fun startDestination(): Route {
    val snapshot = AppSessionState.snapshot.value
    return startDestinationFor(snapshot.user, snapshot.clock)
}

/**
 * #669 — post-validation entry point: an already-restored clock opens Sessions at that
 * branch/day directly (no second clock-in, no dead-end branch label); a validated
 * session without a shift opens branch selection.
 */
internal fun startDestinationFor(
    user: MeResponse?,
    clock: ClockContext?,
): Route =
    when {
        user != null && clock != null -> Route.Dashboard()
        user != null -> Route.BranchSelect
        else -> Route.Login
    }

// #456 — shared shell boundary (#96 Q5: Login + BranchSelect render full-screen;
// #487: AcceptInvite + ForgotPassword are public too — all four stay chrome-free).
internal fun Route?.isPostClockIn(): Boolean =
    this != null && this !is Route.Login && this !is Route.BranchSelect &&
        this !is Route.AcceptInvite && this !is Route.ForgotPassword

// #726 7-param graph seam stays whole (declarative-UI wiring per #535: one leg per
// shell-owned input; bundling would manufacture a DTO).
fun NavGraphBuilder.appRouteGraph(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    onSubmissionLockChanged: (Boolean) -> Unit,
    hooks: PlatformRouteHooks,
    // #726 — narrow exit-intent seam; null = no intake guard (tests/previews).
    exitGuard: SessionCreateExitGuard? = null,
    onExitToRoute: ((Route) -> Unit)? = null,
) {
    authGraph(apiClient, tokenStore, navController)
    branchGraph(apiClient, navController, hooks.dashboardLive)
    clientGraph(apiClient, navController)
    inventoryGraph(apiClient, navController)
    financeGraph(apiClient, navController, hooks.onDeskQueueNavigate)
    auditGraph(apiClient, navController)
    teamGraph(apiClient)
    sessionGraph(
        apiClient = apiClient,
        navController = navController,
        onSubmissionLockChanged = onSubmissionLockChanged,
        onClientProfileClick = hooks.onClientProfileClick,
        exitGuard = exitGuard,
        onExitToRoute = onExitToRoute,
    )
}

private fun NavGraphBuilder.authGraph(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
) {
    composable<Route.Login> {
        LoginScreen(
            authViewModel = viewModel { AuthViewModel(apiClient) },
            bootstrapViewModel = viewModel { SessionBootstrapViewModel(apiClient) },
            tokenStore = tokenStore,
            actions =
                LoginNavActions(
                    onLoginSuccess = {
                        // Per #91 — popUpTo(Login) inclusive on clock-in (foundation best-guess; #94-grad refines)
                        if (AppSessionState.snapshot.value.clock != null) {
                            // #669 — fresh login restored an active shift: open Sessions
                            // at that branch/day directly instead of branch selection.
                            navController.navigate(Route.Dashboard()) {
                                popUpTo(Route.Login) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Route.BranchSelect) {
                                popUpTo(Route.Login) { inclusive = true }
                            }
                        }
                    },
                    // #350 — invite redemption entry point.
                    onAcceptInviteClick = { navController.navigate(Route.AcceptInvite) },
                    // #353 — forgot-password entry point.
                    onForgotPasswordClick = { navController.navigate(Route.ForgotPassword) },
                ),
        )
    }
    composable<Route.AcceptInvite> {
        // Entry-scoped VM (the #112 shape): a resolved or abandoned invite
        // self-cleans — back to Login starts a fresh one.
        val acceptInviteViewModel: AuthViewModel = viewModel { AuthViewModel(apiClient) }
        AcceptInviteScreen(
            authViewModel = acceptInviteViewModel,
            tokenStore = tokenStore,
            onDone = { navController.popBackStack() },
        )
    }
    composable<Route.ForgotPassword> {
        // Entry-scoped VM (#112 shape, same as AcceptInvite).
        val forgotPasswordViewModel: AuthViewModel = viewModel { AuthViewModel(apiClient) }
        ForgotPasswordScreen(
            authViewModel = forgotPasswordViewModel,
            tokenStore = tokenStore,
            onDone = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.branchGraph(
    apiClient: ApiClient,
    navController: NavHostController,
    dashboardLive: @Composable () -> Unit,
) {
    composable<Route.BranchSelect> {
        val branchSelectViewModel: BranchSelectViewModel =
            viewModel { BranchSelectViewModel(apiClient) }
        val reliefInviteViewModel: ReliefInviteViewModel =
            viewModel { ReliefInviteViewModel(apiClient) }
        // #357 — the pre-clock-in relief-request panel (entry-scoped VM).
        val reliefAccessViewModel: ReliefAccessViewModel =
            viewModel { ReliefAccessViewModel(apiClient) }
        BranchSelectScreen(
            viewModel = branchSelectViewModel,
            reliefInviteViewModel = reliefInviteViewModel,
            reliefAccessViewModel = reliefAccessViewModel,
            onClockInComplete = {
                // Per #91 — popUpTo(Login) inclusive on clock-in; #94 Phase 3:
                // navigate Dashboard only after the capability refresh succeeded.
                // popUpTo(0): on the launch-validation path BranchSelect IS the
                // start destination (empty back stack below) — popUpTo(Login)
                // would no-op there and leave a dead-end BranchSelect reachable
                // via back (clocked in, no clock-out — #97-grad fog).
                navController.navigate(Route.Dashboard()) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }
    composable<Route.Dashboard> { entry ->
        // #358 — relief deep link: a (branchId, date) pair renders the
        // branch-day panel instead of the clock-in-gated live dashboard.
        val deepLink = entry.toRoute<Route.Dashboard>()
        if (deepLink.branchId != null && deepLink.date != null) {
            val reliefDayViewModel: ReliefDayViewModel =
                viewModel { ReliefDayViewModel(apiClient, deepLink.branchId, deepLink.date) }
            ReliefDayScreen(
                viewModel = reliefDayViewModel,
                date = deepLink.date,
            )
        } else {
            dashboardLive()
        }
    }
}

private fun NavGraphBuilder.clientGraph(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    composable<Route.Clients> {
        // #113 D7 — code-only route gate, now the #156 any-context check
        // (#92 Q3 "some branch"; backend GLOBAL gate + 403 paths stay authoritative).
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA)) {
            val clientsViewModel: ClientViewModel = viewModel { ClientViewModel(apiClient) }
            ClientsScreen(
                viewModel = clientsViewModel,
                onClientClick = { client ->
                    navController.navigate(Route.ClientDetail(client.id))
                },
            )
        } else {
            RouteGateCard(label = "Clients")
        }
    }
    composable<Route.ClientDetail> { entry ->
        ClientDetailContent(
            apiClient = apiClient,
            navController = navController,
            clientId = entry.toRoute<Route.ClientDetail>().clientId,
        )
    }
}

private fun NavGraphBuilder.inventoryGraph(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    composable<Route.Inventory> {
        // #391 — read-only branch inventory; gate mirrors the drawer item
        // (#156 any-context EDIT_BRANCH_DATA; backend branch-scoped gate
        // authoritative). Branch scope = the clocked-in branch.
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        val selectedBranchId = snapshot.clock?.branchId
        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA)) {
            val inventoryViewModel: InventoryViewModel =
                viewModel { InventoryViewModel(apiClient) }
            val productViewModel: ProductViewModel =
                viewModel { ProductViewModel(apiClient) }
            // #419 — walk-in product-sale entry rides this screen.
            val productSaleViewModel: ProductSaleViewModel =
                viewModel { ProductSaleViewModel(apiClient) }
            // #610 — entry-scoped buyer search behind the narrow client-owned seam (never the
            // full ClientViewModel edit/anonymize surface).
            val saleClientSearch: SaleClientSearchViewModel =
                viewModel { SaleClientSearchViewModel(apiClient) }
            InventoryScreen(
                viewModel = inventoryViewModel,
                productViewModel = productViewModel,
                productSaleViewModel = productSaleViewModel,
                clientSearch = saleClientSearch,
                branchId = selectedBranchId,
            )
        } else {
            RouteGateCard(label = "Inventory")
        }
    }
    composable<Route.BaseRates> {
        // #418 — coordinator base-rate admin; gate mirrors
        // `SessionBaseRateRoutes` exactly (MANAGE_PRODUCTS at BRANCH context for
        // the clocked-in branch — no GLOBAL leg, no day leg, #131 strictness).
        // #685 — the heading names the clocked-in branch; the null-branch state
        // routes to branch selection instead of a no-op retry. The null check runs
        // first: the exact-scope gate fail-closes on a null branch, so without it
        // the clock-in prompt would be unreachable behind the gate card.
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        val selectedBranchId = snapshot.clock?.branchId
        val ratesViewModel: SessionRatesViewModel =
            viewModel { SessionRatesViewModel(apiClient) }
        if (selectedBranchId == null) {
            BaseRatesScreen(
                viewModel = ratesViewModel,
                branchId = null,
                branchName = null,
                onSelectBranch = { navController.navigate(Route.BranchSelect) },
            )
        } else if (capabilities.hasCapability(
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityContextType.BRANCH,
                selectedBranchId,
            )
        ) {
            BaseRatesScreen(
                viewModel = ratesViewModel,
                branchId = selectedBranchId,
                branchName = snapshot.clock?.branchName,
                onSelectBranch = { navController.navigate(Route.BranchSelect) },
            )
        } else {
            RouteGateCard(label = "Base Rates")
        }
    }
    composable<Route.ProductCatalog> {
        ProductCatalogDestination(apiClient)
    }
}

private fun NavGraphBuilder.financeGraph(
    apiClient: ApiClient,
    navController: NavHostController,
    onDeskQueueNavigate: ((currentId: String, id: String) -> Unit)?,
) {
    composable<Route.Finance> {
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        // #105 D1 — the merged Finance & Reports screen, gate = widest read
        // capability (VIEW_BRANCH_DATA any-context, #92 Q3; backend gates
        // authoritative). #158 — a BRANCH_DAY grant holder (relief delegate)
        // reaches the day-scoped read entry without any VIEW grant.
        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.VIEW_BRANCH_DATA) ||
            capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
        ) {
            val financeReportsViewModel: FinanceReportsViewModel =
                viewModel { FinanceReportsViewModel(apiClient) }
            FinanceReportsScreen(viewModel = financeReportsViewModel)
        } else {
            RouteGateCard(label = "Finance & Reports")
        }
    }
    // #120 — D1: code-only route gate, now the #156 any-context check
    // (#92 Q3; backend 403 paths stay authoritative — D8).
    composable<Route.RemittanceList> {
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        val selectedBranchId = snapshot.clock?.branchId
        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.SUBMIT_REMITTANCE)) {
            val remittanceViewModel: RemittanceViewModel =
                viewModel { RemittanceViewModel(apiClient) }
            RemittanceListScreen(
                viewModel = remittanceViewModel,
                branchId = selectedBranchId,
                onRemittanceClick = { remittance ->
                    navController.navigate(Route.RemittanceDetail(remittance.id))
                },
            )
        } else {
            RouteGateCard(label = "Remittance")
        }
    }
    composable<Route.RemittanceDetail> { entry ->
        val snapshot by AppSessionState.snapshot.collectAsState()
        val selectedBranchId = snapshot.clock?.branchId
        val remittanceViewModel: RemittanceViewModel =
            viewModel { RemittanceViewModel(apiClient) }
        val detailRoute = entry.toRoute<Route.RemittanceDetail>()
        RemittanceDetailScreen(
            args =
                RemittanceDetailArgs(
                    remittanceId = detailRoute.id,
                    branchId = selectedBranchId,
                    viewModel = remittanceViewModel,
                ),
            onBack = { navController.popBackStack() },
            // #447 — desk queue selection (desktop NavHost navigates; mobile keeps the
            // default: the queue rail only renders on wide desktop layouts).
            onRemittanceClick = { id ->
                onDeskQueueNavigate?.invoke(detailRoute.id, id)
            },
            deskEnabled = onDeskQueueNavigate != null,
        )
    }
}

private fun NavGraphBuilder.auditGraph(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    // #123 — D9: no route gate (always-visible per #108; backend-authoritative
    // read scoping). hasAnyCapability = zero-grant "No branch access" state.
    composable<Route.AuditLog> {
        val snapshot by AppSessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        val currentUser = snapshot.user
        val auditLogViewModel: AuditLogViewModel =
            viewModel { AuditLogViewModel(apiClient) }
        // #390 — client-table rows jump to Route.ClientDetail; gated to the
        // backend's exact client-read scope (GLOBAL EDIT_BRANCH_DATA) so a
        // BRANCH_DAY day-grant holder never sees an affordance that would 403.
        val canManageClients =
            capabilities.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.GLOBAL,
                GLOBAL_CAPABILITY_CONTEXT_ID,
            )
        AuditLogScreen(
            viewModel = auditLogViewModel,
            currentUserId = currentUser?.id,
            hasAnyCapability = capabilities.isNotEmpty(),
            onFullHistory = { entry ->
                navController.navigate(Route.AuditLogHistory(entry.tableName, entry.recordId))
            },
            onOpenClientRecord =
                if (canManageClients) {
                    { entry -> navController.navigate(Route.ClientDetail(entry.recordId)) }
                } else {
                    null
                },
        )
    }
    // #123 — D8 per-record history: pushed route, entry-scoped VM (fresh entry
    // self-cleans, #112 pattern).
    composable<Route.AuditLogHistory> { entry ->
        val route = entry.toRoute<Route.AuditLogHistory>()
        val snapshot by AppSessionState.snapshot.collectAsState()
        val currentUser = snapshot.user
        val auditLogViewModel: AuditLogViewModel = viewModel { AuditLogViewModel(apiClient) }
        AuditLogHistoryScreen(
            viewModel = auditLogViewModel,
            tableName = route.tableName,
            recordId = route.recordId,
            currentUserId = currentUser?.id,
            onBack = { navController.popBackStack() },
        )
    }
}

// #574 — NavType for Route.SessionDetail.row. Codec is JSON over lowercase hex: the [0-9a-f]
// alphabet never collides with route delimiters (? & = # %) and survives the framework's
// percent-decode leg as identity, so no dependency on internal NavUriUtils. Null follows the
// StringType "null" convention on both legs (the arg has a default, so absent encodes as null).
// Hex radix for the SessionDetail row codec below; a named constant keeps MagicNumber
// honest about the one literal the hex pair codec actually depends on.
private const val HEX_RADIX = 16

internal val SessionDetailRowNavType =
    object : NavType<DashboardSessionResponse?>(isNullableAllowed = true) {
        private val json = Json { ignoreUnknownKeys = true }

        override val name: String = "dashboard_session_row"

        override fun put(
            bundle: SavedState,
            key: String,
            value: DashboardSessionResponse?,
        ) {
            bundle.write {
                if (value != null) putString(key, json.encodeToString(value)) else putNull(key)
            }
        }

        override fun get(
            bundle: SavedState,
            key: String,
        ): DashboardSessionResponse? =
            bundle.read {
                if (contains(key) && !isNull(key)) {
                    json.decodeFromString<DashboardSessionResponse>(getString(key))
                } else {
                    null
                }
            }

        override fun parseValue(value: String): DashboardSessionResponse? =
            if (value == "null") null else json.decodeFromString<DashboardSessionResponse>(value.fromNavHex())

        override fun serializeAsValue(value: DashboardSessionResponse?): String =
            if (value == null) "null" else json.encodeToString(value).toNavHex()

        private fun String.toNavHex(): String =
            encodeToByteArray().joinToString("") { it.toUByte().toString(HEX_RADIX).padStart(2, '0') }

        private fun String.fromNavHex(): String =
            chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray().toString(Charsets.UTF_8)
    }

private fun NavGraphBuilder.sessionGraph(
    apiClient: ApiClient,
    navController: NavHostController,
    onSubmissionLockChanged: (Boolean) -> Unit,
    onClientProfileClick: ((String) -> Unit)?,
    exitGuard: SessionCreateExitGuard? = null,
    onExitToRoute: ((Route) -> Unit)? = null,
) {
    composable<Route.SessionCreate> {
        SessionCreateContent(
            apiClient = apiClient,
            navController = navController,
            onSubmissionLockChanged = onSubmissionLockChanged,
            onClientProfileClick = onClientProfileClick,
            exitGuard = exitGuard,
            onExitToRoute = onExitToRoute,
        )
    }
    // #574 — Route.SessionDetail.row is a custom @Serializable DTO; type-safe routes resolve
    // custom args only via typeMap (without it graph-build throws "could not find any NavType").
    // One registration covers all three legs: navigate-encode and toRoute-decode both read the
    // destination's argument types.
    composable<Route.SessionDetail>(
        typeMap = mapOf(typeOf<DashboardSessionResponse?>() to SessionDetailRowNavType),
    ) { entry ->
        val route = entry.toRoute<Route.SessionDetail>()
        // Entry-scoped (#112): fresh VM per detail entry — the one-shot fetch
        // state self-cleans on pop. The dashboard path seeds Success with the
        // nav-arg row (zero requests); the notifications path (row = null)
        // fetches once via the session-detail GET (#152).
        val sessionDetailViewModel: SessionDetailViewModel =
            viewModel { SessionDetailViewModel(apiClient, route.sessionId, route.row) }
        SessionDetailScreen(
            sessionId = route.sessionId,
            viewModel = sessionDetailViewModel,
            apiClient = apiClient,
            onBack = { navController.popBackStack() },
        )
    }
    composable<Route.Notifications> {
        NotificationsContent(apiClient, navController)
    }
}
