package com.companyb.companyapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore

@Composable
expect fun AppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier = Modifier,
)
