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
internal fun GraveyardCalmClients(repo: CalmRepo) {
    CalmSection("sleeping registry", "global — every branch sees the same faces")
    CalmTwoCol(
        left = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.clients.forEach { c ->
                    val selected = repo.selectedClientId == c.id
                    val pending = repo.pendingForClient(c.id)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { repo.selectedClientId = c.id }
                            .background(
                                if (selected) GraveyardCalmPalette.CardSoft else GraveyardCalmPalette.Card,
                                RoundedCornerShape(10.dp),
                            ).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (c.anonymized) "Guest ${c.id} (veiled)" else c.name,
                                    style = GraveyardCalmType.bodyLarge,
                                    color = GraveyardCalmPalette.Ink,
                                )
                                Text(
                                    "${c.gender} · ${c.age} · ${if (c.phone.isBlank()) "no phone on file" else c.phone}",
                                    style = GraveyardCalmType.labelSmall,
                                    color = GraveyardCalmPalette.Faint,
                                )
                            }
                            if (pending > 0) CalmTag("∗ PENDING", GraveyardCalmPalette.Lamp)
                        }
                    }
                }
            }
        },
        right = {
            val c = repo.clients.firstOrNull { it.id == repo.selectedClientId }
            CalmCard {
                if (c == null) {
                    CalmSection("face card")
                    CalmEmpty("choose a sleeper on the left")
                } else {
                    CalmSection("face card · ${c.id}")
                    Text(
                        if (c.anonymized) "Guest ${c.id}" else c.name,
                        style = GraveyardCalmType.titleLarge,
                        color = GraveyardCalmPalette.Ink,
                    )
                    Text(
                        "gender ${c.gender} · age ${c.age}",
                        style = GraveyardCalmType.bodySmall,
                        color = GraveyardCalmPalette.Dim,
                    )
                    Text(
                        if (c.anonymized) "phone: veiled" else "phone: ${c.phone}",
                        style = GraveyardCalmType.bodySmall,
                        color = GraveyardCalmPalette.Dim,
                    )
                    Spacer(Modifier.height(6.dp))
                    val pend = repo.pendingForClient(c.id)
                    CalmNote("Live PENDING sessions: $pend (house keeps at most one PENDING per client).")
                    Spacer(Modifier.height(6.dp))
                    if (!c.anonymized) {
                        CalmGhost("veil this face (anonymize)") { repo.anonymizeClient(c.id) }
                    } else {
                        Text("Anonymized view: name + phone nullified, gender/age kept for care.", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Faint)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            CalmNote("Clients are global, not per-branch. The night desk only ever reads them softly.")
        },
    )
}

@Composable
internal fun GraveyardCalmTeam(repo: CalmRepo) {
    CalmSection("skeleton crew", "ordered by slot · relief drifts last")
    val ordered = repo.staff.sortedWith(compareBy({ it.relief }, { it.slot }))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { m ->
            CalmCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .background(
                                if (m.clockedIn) GraveyardCalmPalette.Sage.copy(alpha = 0.2f) else GraveyardCalmPalette.Border,
                                RoundedCornerShape(20.dp),
                            ).padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            m.name.split(" ").map { it.first() }.joinToString(""),
                            style = GraveyardCalmType.labelMedium,
                            color = GraveyardCalmPalette.Ink,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = GraveyardCalmType.bodyLarge, color = GraveyardCalmPalette.Ink)
                        Text(
                            "${m.role} · slot ${m.slot} · home ${m.home}",
                            style = GraveyardCalmType.labelSmall,
                            color = GraveyardCalmPalette.Faint,
                        )
                    }
                    if (m.role == "ONBOARDING") CalmTag("locked", GraveyardCalmPalette.Violet)
                    if (m.relief) CalmTag("relief call", GraveyardCalmPalette.Moon)
                    if (m.clockedIn) CalmTag("in", GraveyardCalmPalette.Sage)
                    if (m.relief) {
                        Spacer(Modifier.width(6.dp))
                        CalmQuiet("call in") { repo.acceptRelief(m.id) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    CalmCard {
        CalmSection("role bundles")
        Text("Practitioner: session.write · relief.accept", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
        Text("Coordinator: + client.read · remittance.draft", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
        Text("MANAGER: + remittance.submit · remittance.undo · team.manage", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
        Text("Accountant: remittance.submit · night audit", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
        Text("ONBOARDING: nothing yet — locked until dawn review.", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Violet)
    }
}
