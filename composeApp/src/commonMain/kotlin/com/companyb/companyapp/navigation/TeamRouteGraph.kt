package com.companyb.companyapp.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapabilityAnyContext
import com.companyb.companyapp.ui.screen.ProfileScreen
import com.companyb.companyapp.ui.screen.RouteGateCard
import com.companyb.companyapp.ui.screen.UserManagementScreen
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.ProfileViewModel
import com.companyb.companyapp.viewmodel.UserViewModel

// #460 — team-management routes split out of auditGraph (LongMethod budget is 60;
// each graph stays under it and the registration file stays within TooManyFunctions).
internal fun NavGraphBuilder.teamGraph(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    // #135 — D5: code-only MANAGE_USERS route gate, now the #156 any-context
    // check (backend GLOBAL gate + 403 paths stay authoritative).
    composable<Route.UserManagement> {
        val snapshot by SessionState.snapshot.collectAsState()
        val capabilities = snapshot.capabilities
        val currentUser = snapshot.user
        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.MANAGE_USERS)) {
            val userViewModel: UserViewModel =
                viewModel { UserViewModel(apiClient) }
            val branchViewModel: BranchViewModel =
                viewModel { BranchViewModel(apiClient) }
            UserManagementScreen(
                viewModel = userViewModel,
                branchViewModel = branchViewModel,
                currentUserId = currentUser?.id,
            )
        } else {
            RouteGateCard(label = "User Management")
        }
    }
    composable<Route.MedicalMissionDelegates> {
        MedicalMissionDelegatesDestination(apiClient)
    }
    // #381 — own profile: no route gate (every authenticated user), pushed
    // route, entry-scoped VM (#112).
    composable<Route.Profile> {
        val profileViewModel: ProfileViewModel =
            viewModel { ProfileViewModel(apiClient) }
        ProfileScreen(
            viewModel = profileViewModel,
            onBack = { navController.popBackStack() },
        )
    }
}
