package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Title + Invite/Refresh actions + client-side search field of the User Management screen,
 * hoisted out of [UserManagementScreen] for the #462 LongMethod burn-down. Lives in its own
 * file because both UserManagementScreen.kt and UserManagementDialogs.kt sit at the detekt
 * file-function wall (10/11) — a same-file helper trips TooManyFunctions. Gates arrive as
 * booleans so the Screen keeps the derivations and this host stays a pure overlay. The
 * callbacks + gates ride a [UserManagementHeaderActions] (RoleEditActions precedent) so the
 * helper stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun UserManagementHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchEnabled: Boolean,
    actions: UserManagementHeaderActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "User Management",
            style = MaterialTheme.typography.titleLarge,
        )
        Row {
            TextButton(
                onClick = actions.onInvite,
                enabled = actions.inviteEnabled,
            ) {
                Text("Invite user")
            }
            TextButton(
                onClick = actions.onRefresh,
                // A refresh landing mid-mutation lets the mutation's in-place transform re-apply
                // to the fresh list (swap would double-apply — pass-1 P2/P4 HARD). Belt: the
                // button gate here; suspenders: UserViewModel.loadUsers also skips while any
                // mutation is in flight (covers non-click triggers like LaunchedEffect refires).
                enabled = actions.refreshEnabled,
            ) {
                Text("Refresh")
            }
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text("Search users") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = searchEnabled,
    )
}
