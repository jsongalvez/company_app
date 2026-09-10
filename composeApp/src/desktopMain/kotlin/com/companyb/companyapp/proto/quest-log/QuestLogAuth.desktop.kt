package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogAuthGate(repo: QuestLogRepo) {
    var callsign by remember { mutableStateOf("") }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(QuestLogPalette.Night)
                .padding(QuestPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuestLogPalette.Tavern)
                    .border(1.dp, QuestLogPalette.GoldDeep, RoundedCornerShape(12.dp))
                    .padding(QuestPadLg),
            verticalArrangement = Arrangement.spacedBy(QuestPadSm),
        ) {
            Text("THE GUILD HALL DOOR", style = QuestLogType.labelSmall)
            Text("Sign the quest ledger", style = QuestLogType.displaySmall)
            Text(
                "Pick a traveler, speak a callsign, and step inside. Every name below is inked locally — " +
                    "no courier, no network, only this board.",
                style = QuestLogType.bodyMedium,
            )
            repo.users.forEach { user ->
                val picked = repo.currentUserId == user.id
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (picked) {
                                    QuestLogPalette.Gold.copy(alpha = 0.16f)
                                } else {
                                    QuestLogPalette.TavernRaised
                                },
                            )
                            .border(
                                1.dp,
                                if (picked) QuestLogPalette.Gold else QuestLogPalette.Border,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { repo.currentUserId = user.id }
                            .padding(horizontal = QuestPadMd, vertical = QuestPadSm),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.name, style = QuestLogType.titleMedium)
                            Spacer(Modifier.width(QuestPadSm))
                            QuestBadge(user.role, if (user.locked) QuestLogPalette.Faint else QuestLogPalette.ManaBlue)
                        }
                        Text(
                            if (user.locked) {
                                "ONBOARDING — the trial is not yet sworn"
                            } else {
                                user.capabilities.joinToString(" · ").ifEmpty { "no capabilities yet" }
                            },
                            style = QuestLogType.bodySmall,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it },
                label = { Text("Callsign (any words will do)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            QuestPrimaryButton("Cross the threshold") {
                repo.audit("gate", "AUTH.SIGN_IN", "${repo.currentUser.name} signed the ledger")
                repo.authed = true
            }
        }
    }
}

@Composable
internal fun QuestLogOnboardingLock(repo: QuestLogRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(QuestLogPalette.Night)
                .padding(QuestPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuestLogPalette.Tavern)
                    .border(1.dp, QuestLogPalette.Border, RoundedCornerShape(12.dp))
                    .padding(QuestPadLg),
            verticalArrangement = Arrangement.spacedBy(QuestPadSm),
        ) {
            Text("THE SQUIRE'S BENCH", style = QuestLogType.labelSmall)
            Text("${repo.currentUser.name} is ONBOARDING", style = QuestLogType.displaySmall)
            Text(
                "This traveler has not sworn the trial, so the board stays locked. An ONBOARDING hero " +
                    "may read this bench and nothing more — no quests, no vault, no guild hall.",
                style = QuestLogType.bodyMedium,
            )
            QuestCard {
                Text("Trial checklist (locked)", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("1. Swear the oath before a MANAGER", style = QuestLogType.bodyMedium)
                Text("2. Shadow one full day chain", style = QuestLogType.bodyMedium)
                Text("3. Earn a role: Practitioner, Coordinator, or beyond", style = QuestLogType.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                QuestGhostButton("Choose another traveler") {
                    repo.authed = false
                }
                QuestLinkButton("Clock the bench warm") {
                    repo.audit("gate", "ONBOARDING.WAIT", "${repo.currentUser.name} waited on the bench")
                }
            }
        }
    }
}

@Composable
internal fun QuestLogBranchSelect(repo: QuestLogRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(QuestLogPalette.Night)
                .padding(QuestPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QuestPadSm),
        ) {
            Text("CHOOSE YOUR OUTPOST", style = QuestLogType.labelSmall)
            Text("Where does this tale unfold?", style = QuestLogType.displaySmall)
            Text(
                " Hail, ${repo.currentUser.name} (${repo.currentUser.role}). " +
                    "Every outpost below keeps its own day chain of quests.",
                style = QuestLogType.bodyMedium,
            )
            repo.branches.forEach { branch ->
                val picked = repo.branchId == branch.id
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (picked) {
                                    QuestLogPalette.Gold.copy(alpha = 0.14f)
                                } else {
                                    QuestLogPalette.Tavern
                                },
                            )
                            .border(
                                1.dp,
                                if (picked) QuestLogPalette.Gold else QuestLogPalette.Border,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { repo.branchId = branch.id }
                            .padding(QuestPadMd),
                ) {
                    Column {
                        Text(branch.name, style = QuestLogType.titleLarge)
                        Text(branch.kind, style = QuestLogType.bodySmall)
                    }
                }
            }
            QuestPrimaryButton("Ride out") {
                repo.audit("gate", "BRANCH.SELECT", "${repo.currentUser.name} rode to ${repo.currentBranch.name}")
                repo.branchPicked = true
            }
        }
    }
}
