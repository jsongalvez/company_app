package com.companyb.companyapp.proto.compactlaptop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #778 — compact-laptop work screens: sessions, clients, finance. Fake data only.

@Composable
fun ClSessions(repo: CompactLaptopFakeRepo) {
    var filter by remember { mutableStateOf("ALL") }
    var openId by remember { mutableStateOf<String?>(null) }
    var walkName by remember { mutableStateOf("") }
    var walkTime by remember { mutableStateOf("16:30") }
    var voidReason by remember { mutableStateOf("") }

    if (repo.locked) {
        ClCard { ClTitle("Locked"); ClBody("ONBOARDING cannot open sessions.") }
        return
    }
    ClCard {
        ClMicro("Sessions · ${repo.selectedBranch.name} · ${repo.selectedDay.label}")
        ClTitle("Day roster (${repo.daySessions.size})")
        ClGap(4)
        ClChipRow(listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"), filter) { filter = it }
        ClGap(4)
        val rows = repo.daySessions.filter { filter == "ALL" || it.status.name == filter }
        if (rows.isEmpty()) ClMuted("Nothing under this filter.")
        rows.forEach { s ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickableNoRipple { openId = if (openId == s.id) null else s.id }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.time} · ${s.clientName}${if (s.walkIn) " · walk-in" else ""}${if (s.voided) " · VOID" else ""}",
                            color = ClColors.Ink,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text("P${s.price} · ${s.practitioners}", color = ClColors.Muted, fontSize = 10.sp)
                    }
                    Text(s.status.name, color = s.status.ink(), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                }
                if (openId == s.id) {
                    ClGap(2)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ClSessionStatus.entries.forEach { st ->
                            ClChip(st.name, s.status == st) {
                                repo.setSessionStatus(s.id, st)
                                logInfo("CompactLaptopProto", "session ${s.id} -> $st")
                            }
                        }
                    }
                    if (s.walkIn) {
                        ClGap(2)
                        ClNote("Walk-in rule: walk-in sessions cannot be NO_SHOW or CANCELLED — the move is refused.")
                    }
                    ClGap(2)
                    if (!s.voided) {
                        TextField(
                            value = voidReason,
                            onValueChange = { voidReason = it },
                            label = { Text("Void reason", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        ClGap(2)
                        ClBtn("Void with reason", danger = true) { repo.voidSession(s.id, voidReason); voidReason = "" }
                    } else {
                        ClNote("Voided: ${s.voidReason}")
                        ClGap(2)
                        ClGhostBtn("Unvoid") { repo.unvoidSession(s.id) }
                    }
                }
            }
        }
    }
    ClGap(6)
    ClCard {
        ClSection("Book walk-in (today, this branch)")
        ClGap(2)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(2f)) {
                TextField(value = walkName, onValueChange = { walkName = it }, label = { Text("Client name", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Box(Modifier.weight(1f)) {
                TextField(value = walkTime, onValueChange = { walkTime = it }, label = { Text("Time", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Box(Modifier.width(90.dp)) {
                if (walkName.isNotBlank()) ClBtn("Book", onClick = { repo.addWalkIn(walkName, walkTime); walkName = "" })
            }
        }
    }
}

@Composable
fun ClClients(repo: CompactLaptopFakeRepo) {
    var masked by remember { mutableStateOf(false) }
    if (repo.locked) {
        ClCard { ClTitle("Locked"); ClBody("ONBOARDING cannot open clients.") }
        return
    }
    ClCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ClMicro("Clients · global record, shared across branches")
                ClTitle("Clients (${repo.clients.size})")
            }
            ClChip(if (masked) "Masked" else "Full", masked) { masked = !masked }
        }
        ClGap(2)
        ClNote("At most one PENDING session per client at a time. Anonymize = soft-delete + PII nullification; gender + age kept for reporting.")
        ClGap(2)
        repo.clients.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val shown = if (masked || c.anonymized) "${c.name.first()}***** (masked)" else c.name
                    Text(
                        "$shown · ${c.gender}/${c.age}${if (c.anonymized) " · ANONYMIZED" else ""}",
                        color = ClColors.Ink,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                    Text("${repo.pendingCount(c.id)} PENDING", color = ClColors.Muted, fontSize = 10.sp)
                }
                if (!c.anonymized) ClGhostBtn("Anonymize") { repo.anonymize(c.id) }
            }
        }
    }
}

@Composable
fun ClFinance(repo: CompactLaptopFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    if (repo.locked) {
        ClCard { ClTitle("Locked"); ClBody("ONBOARDING cannot open finance.") }
        return
    }
    ClRemitKind.entries.forEach { kind ->
        ClCard {
            ClMicro("Remittance · ${kind.name} · ${repo.selectedBranch.name}")
            ClTitle("$kind · ${repo.selectedDay.label}")
            ClGap(2)
            val rows = repo.remits.filter { it.kind == kind && it.branchId == repo.selectedBranchId }
            if (rows.isEmpty()) ClMuted("No $kind drafts for this branch yet.")
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("P${r.total} · ${r.state}", color = ClColors.Ink, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        val dayLabel = repo.days.firstOrNull { it.id == r.dayId }?.label ?: r.dayId
                        Text(dayLabel, color = ClColors.Muted, fontSize = 10.sp)
                        if (r.snapshot.isNotBlank()) Text(r.snapshot, color = ClColors.Remitted, fontSize = 10.sp)
                    }
                    if (r.state == ClRemitState.DRAFT) {
                        ClBtn("Submit") { repo.submitRemit(r.id) }
                    } else {
                        ClGhostBtn("Undo 48h") { repo.undoRemit(r.id, undoReason); undoReason = "" }
                    }
                }
            }
            ClGap(2)
            TextField(
                value = undoReason,
                onValueChange = { undoReason = it },
                label = { Text("Undo reason (48h window)", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ClGap(2)
            ClNote("Submit seals an immutable snapshot. Undo reopens to DRAFT with a reason inside 48h.")
        }
        ClGap(6)
    }
    ClCard {
        ClSection("Commission split")
        ClBody("Pooled per branch day, split equally over staff clocked in at sold_at. Relief payouts come from the relief branch drawer.")
    }
}

// Small local clickable without ripple import weight.
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
