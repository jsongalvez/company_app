package com.companyb.companyapp.proto.glasspanels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GpClients(repo: GlassPanelsFakeRepo) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassSheet(modifier = Modifier.weight(1.3f)) {
            Column {
                FaintLabel("clients / one global ocean")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Every file, everywhere", "Clients are global across branches — at most one PENDING session each.")
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(repo.clients) { c ->
                        GlassSheet(radius = 16.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (c.anonymized) "File ${c.id.uppercase()} · anonymized" else c.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = FrostText,
                                    )
                                    Text(text = c.detail, fontSize = 12.sp, color = FrostDim)
                                    if (c.hasPending) {
                                        Text(text = "◆ one PENDING session (max)", fontSize = 12.sp, color = PeachGlow)
                                    }
                                }
                                GhostButton(
                                    if (c.anonymized) "Reveal" else "Anonymize",
                                    { repo.toggleAnonymized(c.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        GlassSheet(modifier = Modifier.weight(1f)) {
            Column {
                FaintLabel("privacy pane")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Frost on demand", "Anonymized files keep only what reports need.")
                Spacer(modifier = Modifier.height(10.dp))
                PaneRow("Kept", "File id, age band, coarse note")
                PaneRow("Hidden", "Name, contact, visit detail")
                PaneRow("Rule", "Reveal stamps the audit log")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Toggle any card to watch the frost slide on and off.",
                    fontSize = 12.sp,
                    color = FrostDim,
                )
            }
        }
    }
}

@Composable
fun GpFinance(repo: GlassPanelsFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var undoId by remember { mutableStateOf<String?>(null) }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassSheet(modifier = Modifier.weight(1.2f)) {
            Column {
                FaintLabel("finance / remittance panes")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Seal the day", "SESSION and PRODUCT drafts submit into frozen snapshots.")
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(repo.remittances) { r ->
                        GlassSheet(radius = 16.dp) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "${r.kind} · ${r.id} · ${r.dayLabel}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FrostText)
                                        Text(
                                            text = if (r.status == GpRemitStatus.SUBMITTED) "Snapshot ${r.snapshot} · ${r.submittedAt}" else "Draft · P${r.amount}",
                                            fontSize = 12.sp,
                                            color = FrostDim,
                                        )
                                    }
                                    StatusPill(r.status.name, if (r.status == GpRemitStatus.SUBMITTED) MintGlow else AquaGlow)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (r.status == GpRemitStatus.DRAFT) {
                                    GlassButton(
                                        "Submit + freeze snapshot",
                                        { repo.submitRemittance(r.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    if (undoId == r.id) {
                                        GlassField(undoReason, { undoReason = it }, "Undo reason (within 48h)")
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GhostButton(
                                                "Confirm undo",
                                                {
                                                    if (undoReason.isBlank()) return@GhostButton
                                                    repo.undoRemittance(r.id, undoReason)
                                                    undoReason = ""
                                                    undoId = null
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            GhostButton("Keep sealed", { undoId = null }, modifier = Modifier.weight(1f))
                                        }
                                    } else {
                                        GhostButton("Undo within 48h", { undoId = r.id }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        GlassSheet(modifier = Modifier.weight(1f)) {
            Column {
                FaintLabel("commission pane")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Split light", "Commission splits ride on sealed snapshots.")
                Spacer(modifier = Modifier.height(10.dp))
                PaneRow("Practitioner", "60% of completed SESSION value")
                PaneRow("Branch pool", "25% held for the branch day")
                PaneRow("House", "15% to the house pane")
                PaneRow("Undo rule", "Undo reopens the split for 48h")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Undo a submitted draft to watch its snapshot melt back to DRAFT.",
                    fontSize = 12.sp,
                    color = FrostDim,
                )
            }
        }
    }
}

@Composable
fun GpTeam(repo: GlassPanelsFakeRepo) {
    GlassSheet(modifier = Modifier.fillMaxSize()) {
        Column {
            FaintLabel("team / souls behind the glass")
            Spacer(modifier = Modifier.height(8.dp))
            SheetTitle("Who floats here", "${repo.users.size} users · roles shape every pane")
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(repo.users) { u ->
                    GlassSheet(radius = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FrostText)
                                Text(text = "${u.role} · home ${repo.branchName(u.homeBranchId)}", fontSize = 12.sp, color = FrostDim)
                                if (u.onboarding) {
                                    Text(text = "Locked — empty capability bundle", fontSize = 12.sp, color = PeachGlow)
                                }
                            }
                            StatusPill(u.role.uppercase(), if (u.onboarding) FrostDim else VioletGlow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpMail(repo: GlassPanelsFakeRepo) {
    val unread = repo.notes.count { !it.read }
    GlassSheet(modifier = Modifier.fillMaxSize()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    FaintLabel("notifications / drifting mail")
                    Spacer(modifier = Modifier.height(8.dp))
                    SheetTitle("Mailbox", if (unread == 0) "All read — clear water" else "$unread unread pane${if (unread == 1) "" else "s"}")
                }
                GhostButton("Mark all read", { repo.markAllRead() })
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(repo.notes) { n ->
                    GlassSheet(radius = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (n.read) "○" else "●",
                                        fontSize = 12.sp,
                                        color = if (n.read) FrostDim else AquaGlow,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FrostText)
                                }
                                Text(text = n.body, fontSize = 12.sp, color = FrostDim)
                                Text(text = n.day, fontSize = 11.sp, color = FrostDim)
                            }
                            if (!n.read) {
                                GhostButton("Read", { repo.markRead(n.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpAudit(repo: GlassPanelsFakeRepo) {
    GlassSheet(modifier = Modifier.fillMaxSize()) {
        Column {
            FaintLabel("audit log / ripples kept")
            Spacer(modifier = Modifier.height(8.dp))
            SheetTitle("Everything leaves a ring", "${repo.audits.size} entries — clock, void, remit, relief")
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repo.audits) { a ->
                    GlassSheet(radius = 14.dp) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(a.action, VioletGlow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = a.record, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FrostText)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = a.whenText, fontSize = 11.sp, color = FrostDim)
                            }
                            Text(text = "${a.actor} — ${a.reason}", fontSize = 12.sp, color = FrostDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpProfile(repo: GlassPanelsFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassSheet {
            Column {
                FaintLabel("profile / your pane")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle(repo.me.value, "Practitioner · home ${repo.branchName(repo.branchId.value)}")
                Spacer(modifier = Modifier.height(10.dp))
                PaneRow("Branch day", repo.dayStatus.value.name)
                PaneRow("Boundary", "Days flip at 04:00 Asia/Manila")
                PaneRow("Clock", if (repo.clockedIn.value) "In — warm" else "Out — cool")
                Spacer(modifier = Modifier.height(12.dp))
                FaintLabel("branch-day remote control (fake)")
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Open", { repo.dayStatus.value = GpDayStatus.OPEN }, modifier = Modifier.weight(1f))
                    GhostButton("Past", { repo.dayStatus.value = GpDayStatus.PAST }, modifier = Modifier.weight(1f))
                    GhostButton("Remitted", { repo.dayStatus.value = GpDayStatus.REMITTED }, modifier = Modifier.weight(1f))
                }
            }
        }
        GlassSheet {
            Column {
                FaintLabel("leave the water")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (repo.clockedIn.value) {
                        GhostButton("Clock out", { repo.clockOut() }, modifier = Modifier.weight(1f))
                    }
                    GlassButton("Log out", onLogout, modifier = Modifier.weight(1f), accent = RoseGlow)
                }
            }
        }
    }
}
