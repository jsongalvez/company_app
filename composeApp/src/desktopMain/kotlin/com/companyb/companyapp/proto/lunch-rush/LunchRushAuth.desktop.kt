package com.companyb.companyapp.proto.lunchrush

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LunchRushAuthGate(repo: LunchRushRepo) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LunchRushPalette.Paper)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(520.dp)) {
            Text("MIDDAY RUSH BOARD", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
            Text("lunch-rush — peak-load triage", style = LunchRushType.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Fake sign-in only. Pick a crew member to take the floor. No network, no backend.",
                style = LunchRushType.bodyMedium,
            )
            Spacer(Modifier.height(RushPadLg))
            repo.users.forEach { user ->
                RushTicketCard(hot = user.role == "Coordinator") {
                    RushRow(
                        left = {
                            Column {
                                Text(user.name, style = LunchRushType.titleMedium)
                                Text(
                                    user.role + if (user.locked) " — locked" else "",
                                    style = LunchRushType.bodySmall,
                                )
                            }
                        },
                        right = {
                            Button(
                                onClick = {
                                    repo.currentUserId = user.id
                                    repo.authed = true
                                    repo.audit(repo.actor(), "AUTH.LOGIN", "${user.name} took the floor (fake)")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Char),
                            ) {
                                Text("TAKE FLOOR")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(RushPadSm))
            }
        }
    }
}

@Composable
internal fun LunchRushOnboardingLock(repo: LunchRushRepo) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LunchRushPalette.Paper)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(480.dp)) {
            Text("ONBOARDING — LOCKED", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
            Text("Not on the rota yet", style = LunchRushType.displaySmall)
            Spacer(Modifier.height(8.dp))
            RushNote("ONBOARDING crew hold zero capabilities until a MANAGER clears them. Read-only lockout screen; sign out to switch crew.")
            Spacer(Modifier.height(RushPadMd))
            Text("Capabilities: none", style = LunchRushType.bodyMedium)
            Spacer(Modifier.height(RushPadLg))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.logout() }) { Text("Sign out") }
            }
        }
    }
}

@Composable
internal fun LunchRushBranchSelect(repo: LunchRushRepo) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LunchRushPalette.Paper)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(560.dp)) {
            Text("PICK YOUR COUNTER", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
            Text("Where is the rush worst?", style = LunchRushType.displaySmall)
            Spacer(Modifier.height(RushPadMd))
            repo.branches.forEach { branch ->
                RushTicketCard {
                    RushRow(
                        left = {
                            Column {
                                Text(branch.name, style = LunchRushType.titleMedium)
                                Text(branch.kind, style = LunchRushType.bodySmall)
                            }
                        },
                        right = {
                            Button(
                                onClick = {
                                    repo.branchId = branch.id
                                    repo.branchPicked = true
                                    repo.audit(repo.actor(), "BRANCH.SELECT", "${branch.name} counter picked")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Wok),
                            ) {
                                Text("OPEN COUNTER")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(RushPadSm))
            }
            Spacer(Modifier.height(RushPadSm))
            OutlinedButton(onClick = { repo.logout() }) { Text("Back to sign-in") }
        }
    }
}
