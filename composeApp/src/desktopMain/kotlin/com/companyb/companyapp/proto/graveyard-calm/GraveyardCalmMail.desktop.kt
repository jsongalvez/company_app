package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GraveyardCalmMailbox(repo: CalmRepo) {
    CalmSection("whisper mail", "${repo.unreadCount} unread · tap to hush")
    CalmTwoCol(
        left = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    CalmQuiet("hush them all") { repo.markAllRead() }
                }
                if (repo.notices.all { it.read }) {
                    CalmCard { CalmEmpty("silence. beautiful.") }
                }
                repo.notices.forEach { n ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { repo.markRead(n.id) }
                            .background(
                                if (n.read) GraveyardCalmPalette.Card else GraveyardCalmPalette.CardSoft,
                                RoundedCornerShape(10.dp),
                            ).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!n.read) CalmTag("● new", GraveyardCalmPalette.Lamp)
                                Spacer(Modifier.width(6.dp))
                                CalmTag(n.kind, GraveyardCalmPalette.Moon)
                                Spacer(Modifier.weight(1f))
                                Text(n.dayRef, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(n.title, style = GraveyardCalmType.bodyLarge, color = if (n.read) GraveyardCalmPalette.Dim else GraveyardCalmPalette.Ink)
                            Text(n.body, style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                        }
                    }
                }
            }
        },
        right = {
            CalmCard {
                CalmSection("audit log", "newest first · every tap leaves a print")
                repo.audits.forEach { a ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${a.seq} ${a.clock}", style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Lamp)
                            Spacer(Modifier.weight(1f))
                            Text(a.actor, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                        }
                        Text(a.action, style = GraveyardCalmType.bodyMedium, color = GraveyardCalmPalette.Ink)
                        Text(a.detail, style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                        CalmDivider()
                    }
                }
            }
        },
    )
}

@Composable
internal fun GraveyardCalmProfile(repo: CalmRepo) {
    CalmSection("night lamp", "you, in the dark")
    CalmTwoCol(
        left = {
            CalmCard {
                Text(repo.currentUser.name, style = GraveyardCalmType.titleLarge, color = GraveyardCalmPalette.Ink)
                Text(repo.currentUser.role, style = GraveyardCalmType.labelMedium, color = GraveyardCalmPalette.Lamp)
                Spacer(Modifier.height(6.dp))
                Text("capabilities:", style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                if (repo.currentUser.capabilities.isEmpty()) {
                    Text("none — ONBOARDING is locked.", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Violet)
                } else {
                    repo.currentUser.capabilities.forEach { cap ->
                        Text("· $cap", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("post: ${repo.currentBranch.name}", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                Text(
                    "lamp: ${if (repo.clockedIn) "lit (clocked in)" else "dim (clocked out)"}",
                    style = GraveyardCalmType.bodySmall,
                    color = GraveyardCalmPalette.Dim,
                )
            }
        },
        right = {
            CalmCard {
                CalmSection("end of night")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        CalmGhost("clock out") { repo.clockOut() }
                    } else {
                        CalmPrimary("clock in") { repo.clockIn() }
                    }
                    CalmGhost("blow out lamp + logout") { repo.logout() }
                }
                Spacer(Modifier.height(8.dp))
                CalmNote("Logging out returns to sign-in. Clock-out is recorded in the audit log.")
                Spacer(Modifier.height(6.dp))
                CalmQuiet(if (repo.shortcutsOpen) "hide shortcuts" else "show shortcuts (?)") {
                    repo.shortcutsOpen = !repo.shortcutsOpen
                }
                if (repo.shortcutsOpen) {
                    Spacer(Modifier.height(4.dp))
                    Text("1 night desk · 2 queue · 3 registry · 4 till · 5 crew · 6 mail · 7 lamp", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                }
            }
        },
    )
}
