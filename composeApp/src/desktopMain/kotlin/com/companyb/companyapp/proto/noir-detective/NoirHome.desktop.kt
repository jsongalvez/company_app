package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
internal fun NoirHome(repo: NoirRepo) {
    FolderTab(title = "Tonight's dossier", right = repo.currentBranch.name + " · " + repo.currentDay.date)
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("THE SHIFT", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (repo.clockedIn) {
                        "On the clock — the city is yours."
                    } else {
                        "Off duty — punch in to work the board."
                    },
                    style = NoirType.bodyLarge,
                )
                Spacer(Modifier.height(NoirPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                    if (repo.clockedIn) {
                        OutlinedButton(onClick = {
                            repo.clockedIn = false
                            repo.appendLog(repo.actor(), "CLOCK_OUT", repo.currentUser.name + " clocked out")
                        }) {
                            Text("Clock out", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
                        }
                    } else {
                        Button(
                            onClick = {
                                repo.clockedIn = true
                                repo.appendLog(repo.actor(), "CLOCK_IN", repo.currentUser.name + " clocked in")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.LampAmber),
                        ) {
                            Text("Clock in", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                        }
                    }
                }
            }
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("TONIGHT BY THE NUMBERS", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                NumbersGrid(repo)
            }
            if (repo.dayLocked) {
                DeskLampNote("Day is ${repo.currentDay.state.label} — Coordinator or MANAGER edits only.")
            }
        }
        Column(Modifier.width(NoirDetailWidth), verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RELIEF DUTY", style = NoirType.titleSmall)
                    Spacer(Modifier.weight(1f))
                    NoirBadge("expires 04:00", NoirPalette.NeonBlue)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "P. Gomez works Cebu Stakeout tonight on relief — view-only until a grant lands.",
                    style = NoirType.bodyMedium,
                )
                Spacer(Modifier.height(NoirPadSm))
                Button(
                    onClick = { repo.grantRelief() },
                    colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.NeonBlue),
                ) {
                    Text("Grant edit access", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                }
            }
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("RELIEF INVITE", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Malate invites J. Cruz for 2026-09-10. Accepting writes the day grant; " +
                        "the branch may revoke until clock-in.",
                    style = NoirType.bodyMedium,
                )
                Spacer(Modifier.height(NoirPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                    Button(
                        onClick = { repo.appendLog(repo.actor(), "RELIEF.ACCEPT", "invite accepted for 2026-09-10") },
                        colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.EvidenceGreen),
                    ) {
                        Text("Accept", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                    }
                    OutlinedButton(onClick = { repo.appendLog(repo.actor(), "RELIEF.DECLINE", "invite declined") }) {
                        Text("Decline", style = NoirType.labelLarge, color = NoirPalette.Dim)
                    }
                }
            }
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("RELIEF REQUEST", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Broadcast to the whole branch — names no individual. " +
                        "One live request per requester per branch per date.",
                    style = NoirType.bodyMedium,
                )
                Spacer(Modifier.height(NoirPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                    OutlinedButton(onClick = { repo.appendLog(repo.actor(), "RELIEF.DENY", "request denied") }) {
                        Text("Deny", style = NoirType.labelLarge, color = NoirPalette.Dim)
                    }
                    OutlinedButton(onClick = { repo.markRead("w-1") }) {
                        Text("Shelve wire", style = NoirType.labelLarge, color = NoirPalette.Dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumbersGrid(repo: NoirRepo) {
    val open = repo.cases.count { it.status == CaseStatus.PENDING && !it.voided }
    val closed = repo.cases.count { it.status == CaseStatus.COMPLETED && !it.voided }
    val voided = repo.cases.count { it.voided }
    val unread = repo.unreadCount
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
        NumberCell(open.toString(), "open cases", NoirPalette.LampAmber, Modifier.weight(1f))
        NumberCell(closed.toString(), "closed", NoirPalette.EvidenceGreen, Modifier.weight(1f))
        NumberCell(voided.toString(), "voided", NoirPalette.SirenRed, Modifier.weight(1f))
        NumberCell(unread.toString(), "unread wire", NoirPalette.NeonBlue, Modifier.weight(1f))
    }
}
