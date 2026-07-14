package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.UiState

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
) {
    val logoutState by authViewModel.logoutState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to CompanyApp",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You are logged in.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { authViewModel.logout() },
            enabled = logoutState !is UiState.Loading,
        ) {
            Text("Logout")
        }
    }
}
