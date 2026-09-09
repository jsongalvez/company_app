package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsAuthGate(repo: DarkOpsRepo) {
    var picked by remember { mutableStateOf(repo.currentUserId) }
    var secret by remember { mutableStateOf("ops-2026") }
    Box(modifier = Modifier.fillMaxSize().background(DarkOpsPalette.Void), contentAlignment = Alignment.Center) {
        OpsPanel(modifier = Modifier.width(520.dp)) {
            DarkOpsWordmark()
            Spacer(Modifier.height(OpsPadMd))
            Text("OPERATOR SIGN-IN  //  fake directory, any secret works", style = DarkOpsType.labelSmall)
            Spacer(Modifier.height(OpsPadSm))
            repo.users.forEach { user ->
                OpsRow(selected = picked == user.id, onClick = { picked = user.id }) {
                    Text(
                        if (picked ==
                            user.id
                        ) {
                            "[*]"
                        } else {
                            "[ ]"
                        },
                        style = DarkOpsType.bodyMedium,
                        color = DarkOpsPalette.Phosphor,
                    )
                    Spacer(Modifier.width(OpsPadSm))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = DarkOpsType.bodyMedium)
                        Text(user.role + if (user.locked) "  // locked" else "", style = DarkOpsType.bodySmall)
                    }
                    OpsBadge(user.role, if (user.locked) DarkOpsPalette.Red else DarkOpsPalette.Cyan)
                }
            }
            Spacer(Modifier.height(OpsPadSm))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("secret", style = DarkOpsType.bodySmall) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = DarkOpsType.bodyMedium,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(OpsPadSm))
            Button(
                onClick = {
                    repo.currentUserId = picked
                    repo.authed = true
                },
                colors = greenButton(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("LOGIN  >", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
            Spacer(Modifier.height(4.dp))
            OpsNote("pick K. Dela Pena to see the ONBOARDING lockout")
        }
    }
}

@Composable
internal fun DarkOpsWordmark() {
    Text("COMPANYAPP // DARK-OPS", style = DarkOpsType.displaySmall, color = DarkOpsPalette.Phosphor)
    Text("dense ops console  ·  fake data  ·  no network", style = DarkOpsType.bodySmall)
}

@Composable
internal fun DarkOpsOnboardingLock(repo: DarkOpsRepo) {
    Box(modifier = Modifier.fillMaxSize().background(DarkOpsPalette.Void), contentAlignment = Alignment.Center) {
        OpsPanel(modifier = Modifier.width(520.dp)) {
            DarkOpsWordmark()
            Spacer(Modifier.height(OpsPadMd))
            OpsBadge("ONBOARDING // LOCKED", DarkOpsPalette.Red)
            Spacer(Modifier.height(OpsPadSm))
            Text(repo.currentUser.name + " holds an empty capability bundle.", style = DarkOpsType.bodyMedium)
            Text("Zero capabilities derive even after a branch assignment.", style = DarkOpsType.bodyMedium)
            Text("Waits on MANAGE_USERS granting a real role.", style = DarkOpsType.bodyMedium)
            Spacer(Modifier.height(OpsPadSm))
            OpsNote("capabilities: (none)")
            Spacer(Modifier.height(OpsPadSm))
            Row(horizontalArrangement = Arrangement.spacedBy(OpsPadSm)) {
                TextButton(onClick = { repo.logout() }) {
                    Text("< sign out", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Amber)
                }
            }
        }
    }
}

@Composable
internal fun DarkOpsBranchSelect(repo: DarkOpsRepo) {
    Box(modifier = Modifier.fillMaxSize().background(DarkOpsPalette.Void), contentAlignment = Alignment.Center) {
        OpsPanel(modifier = Modifier.width(560.dp)) {
            Text("SELECT BRANCH  //  " + repo.currentUser.name, style = DarkOpsType.titleMedium)
            Spacer(Modifier.height(OpsPadSm))
            repo.branches.forEach { branch ->
                OpsRow(selected = repo.branchId == branch.id, onClick = { repo.branchId = branch.id }) {
                    Text(if (repo.branchId == branch.id) "[*]" else "[ ]", style = DarkOpsType.bodyMedium)
                    Spacer(Modifier.width(OpsPadSm))
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, style = DarkOpsType.bodyMedium)
                        Text("branch day rolls at 04:00 Asia/Manila", style = DarkOpsType.bodySmall)
                    }
                    OpsBadge(branch.kind, DarkOpsPalette.Violet)
                }
            }
            Spacer(Modifier.height(OpsPadSm))
            Button(onClick = { repo.branchPicked = true }, colors = greenButton(), modifier = Modifier.fillMaxWidth()) {
                Text("ENTER CONSOLE  >", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
        }
    }
}

@Composable
internal fun greenButton() =
    ButtonDefaults.buttonColors(
        containerColor = DarkOpsPalette.Phosphor,
        contentColor = DarkOpsPalette.Void,
        disabledContainerColor = DarkOpsPalette.Border,
        disabledContentColor = DarkOpsPalette.Faint,
    )

@Composable
internal fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkOpsPalette.Ink,
        unfocusedTextColor = DarkOpsPalette.Ink,
        focusedBorderColor = DarkOpsPalette.Phosphor,
        unfocusedBorderColor = DarkOpsPalette.BorderBright,
        focusedLabelColor = DarkOpsPalette.Phosphor,
        unfocusedLabelColor = DarkOpsPalette.Dim,
        cursorColor = DarkOpsPalette.Phosphor,
    )
