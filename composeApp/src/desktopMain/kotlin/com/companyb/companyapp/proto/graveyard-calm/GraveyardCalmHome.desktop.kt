package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GraveyardCalmHome(repo: CalmRepo) {
    CalmSection("night desk", "${repo.currentBranch.name} · ${repo.currentDay.date}")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        CalmStat(repo.pendingCount.toString(), "awake in queue")
        CalmStat(repo.unreadCount.toString(), "unread whispers")
        CalmStat(repo.handovers.size.toString(), "handover notes")
        CalmStat(if (repo.clockedIn) "in" else "out", "your lamp")
    }
    Spacer(Modifier.height(10.dp))
    CalmTwoCol(
        left = {
            CalmCard {
                CalmSection("your shift")
                Text(
                    if (repo.clockedIn) "Clocked in — the lamp is lit. Move softly." else "Clocked out — the desk keeps your chair warm.",
                    style = GraveyardCalmType.bodyMedium,
                    color = GraveyardCalmPalette.Dim,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        CalmGhost("clock out") { repo.clockOut() }
                    } else {
                        CalmPrimary("clock in quietly") { repo.clockIn() }
                    }
                }
                CalmDivider()
                CalmSection("relief duty")
                val relief = repo.staff.filter { it.relief }
                if (relief.isEmpty()) {
                    Text("No one is calling for backup. The night holds.", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Faint)
                } else {
                    relief.forEach { mate ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(mate.name, style = GraveyardCalmType.bodyMedium, color = GraveyardCalmPalette.Ink)
                                Text(
                                    "${mate.role} · ${mate.home} · 01:00–03:00",
                                    style = GraveyardCalmType.labelSmall,
                                    color = GraveyardCalmPalette.Faint,
                                )
                            }
                            CalmQuiet("answer +") { repo.acceptRelief(mate.id) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                CalmDivider()
                CalmNote("Invite: Mara invited Teo (Laguna Night Van) for 01:00–03:00 — pending his nod. Request: Teo requested one pair of hands — see whisper mail.")
            }
        },
        right = {
            CalmCard {
                CalmSection("handover log", "the dawn crew reads this first")
                repo.handovers.forEach { h ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(GraveyardCalmPalette.CardSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.clock, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Lamp)
                                Text("  ·  ${h.author}", style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                            }
                            Text(h.text, style = GraveyardCalmType.bodyMedium, color = GraveyardCalmPalette.Ink)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                TextField(
                    value = repo.handoverDraft,
                    onValueChange = { repo.handoverDraft = it },
                    placeholder = { Text("leave a note for dawn…", style = GraveyardCalmType.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = GraveyardCalmPalette.CardSoft,
                            unfocusedContainerColor = GraveyardCalmPalette.CardSoft,
                            focusedTextColor = GraveyardCalmPalette.Ink,
                            unfocusedTextColor = GraveyardCalmPalette.Ink,
                        ),
                )
                Spacer(Modifier.height(6.dp))
                CalmPrimary("pin note to log") { repo.addHandover() }
            }
        },
    )
    Spacer(Modifier.height(10.dp))
    CalmNote("Skeleton crew tonight: ${repo.staff.count { it.clockedIn }} clocked in, ${repo.staff.count { it.relief }} on relief call. Quiet alerts only — nothing here rings.")
}
