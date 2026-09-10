package com.companyb.companyapp.proto.mindmapnav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapFinanceDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    DetailScroll("Finance nebula", crumb, onZoomOut) {
        val sealed = repo.drafts.filter { it.submitted }.sumOf { it.amount * it.qty }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("sealed", "₱$sealed", Leaf)
            StatCard("open drafts", repo.drafts.count { !it.submitted }.toString(), Honey)
        }
        NoteCard("SESSION drafts carry net income; PRODUCT drafts price × quantity. Submit seals a snapshot; Undo reopens within 48h with a reason. Commission splits 60/40 Branch/practitioner after sealing.")
        repo.drafts.forEach { d ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, if (d.submitted) Leaf else Honey, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.id, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NodeInk, modifier = Modifier.weight(1f))
                    Text(d.kind.name, fontFamily = MapMono, fontSize = 10.sp, color = Brook)
                    Text(if (d.submitted) "SEALED" else if (d.undone) "REOPENED" else "DRAFT", fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (d.submitted) Leaf else Honey)
                }
                Text(d.label, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NodeInk)
                Text("₱${d.amount} × ${d.qty} = ₱${d.amount * d.qty}${if (d.snapshot.isNotBlank()) " · ${d.snapshot}" else ""}${if (d.undone) " · undone: ${d.undoReason}" else ""}", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim)
                if (!d.submitted && !d.undone) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardBtn("submit ✦", onClick = { repo.submitDraft(d.id) })
                    }
                } else if (d.submitted) {
                    if (undoFor == d.id) {
                        MapField(undoReason, { undoReason = it }, "Undo reason (within 48h)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardBtn("confirm undo", onClick = { repo.undoDraft(d.id, undoReason.ifBlank { "sealed in error" }); undoReason = ""; undoFor = null })
                            CardBtn("keep sealed", onClick = { undoFor = null })
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardBtn("undo 48h…", onClick = { undoFor = d.id })
                        }
                    }
                } else {
                    NoteCard("Reopened: ${d.undoReason}", Honey)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardBtn("submit ✦", onClick = { repo.submitDraft(d.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun MapTeamDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    DetailScroll("Team cluster", crumb, onZoomOut) {
        NoteCard("Roles bundle capabilities. MANAGER = Coordinator + user management. Accountant reads everything, edits nothing. ONBOARDING nodes stay grey until granted a role.")
        repo.mates.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, if (m.locked) Honey else PanelEdge, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.name, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (m.locked) NodeDim else NodeInk)
                    Text(m.note, fontFamily = MapSans, fontSize = 12.sp, color = NodeDim)
                }
                Text(m.role, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (m.locked) Honey else Moss)
            }
        }
        NoteCard("Capability glance: Practitioner logs Sessions · Coordinator edits PAST/REMITTED · MANAGER assigns delegates · Relief grants expire 04:00 Manila.", Vine)
    }
}

@Composable
fun MapInboxDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    DetailScroll("Mailbox moons", crumb, onZoomOut) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${repo.unreadCount()} unread", fontFamily = MapMono, fontSize = 12.sp, color = Coral, modifier = Modifier.weight(1f))
            MapGhost("mark all read", onClick = { repo.markAllRead() })
        }
        if (repo.notices.isEmpty()) NoteCard("Mailbox empty. Quiet orbit.")
        repo.notices.forEach { n ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, if (n.read) PanelEdge else Coral, RoundedCornerShape(12.dp)).clickable { repo.toggleNotice(n.id) }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (n.read) "○" else "●", fontFamily = MapMono, fontSize = 12.sp, color = if (n.read) NodeDim else Coral)
                    Text(n.title, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (n.read) NodeDim else NodeInk, modifier = Modifier.weight(1f))
                    Text(n.day, fontFamily = MapMono, fontSize = 10.sp, color = NodeDim)
                }
                Text(n.body, fontFamily = MapSans, fontSize = 12.sp, color = NodeDim)
                Text(n.branch, fontFamily = MapMono, fontSize = 11.sp, color = Moss)
            }
        }
    }
}

@Composable
fun MapAuditDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    DetailScroll("Audit trail", crumb, onZoomOut) {
        NoteCard("Every void, unvoid, submit, undo, clock and relief writes here with actor, action and reason. Newest first.")
        if (repo.audits.isEmpty()) NoteCard("No entries yet — act in any orbit and watch this trail grow.")
        repo.audits.forEach { a ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.action, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Mist, modifier = Modifier.weight(1f))
                    Text(a.time, fontFamily = MapMono, fontSize = 10.sp, color = NodeDim)
                }
                Text(a.detail, fontFamily = MapSans, fontSize = 13.sp, color = NodeInk)
                Text("by ${a.actor}", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim)
            }
        }
    }
}

@Composable
fun MapProfileDetail(
    repo: MapFakeRepo,
    crumb: String,
    onZoomOut: () -> Unit,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    DetailScroll("Your star", crumb, onZoomOut) {
        NoteCard("Signed in as ${repo.email}. Home Branch: Makati Branch. Clock state, Branch Day and demo data all live here.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("shift", if (repo.clockedIn) "IN" else "OUT", Leaf)
            StatCard("branch day", repo.currentBranch().day.name, repo.currentBranch().day.ring())
        }
        if (repo.clockedIn) MapGhost("clock out ○", onClick = { repo.clock(false) }) else MapButton("clock in ●", onClick = { repo.clock(true) })
        Text("BRANCH DAY", fontFamily = MapMono, fontSize = 11.sp, letterSpacing = 3.sp, color = NodeDim)
        NoteCard("${repo.currentBranch().name} is ${repo.currentBranch().day.name}. Boundary 04:00 Asia/Manila. Advancing cycles OPEN → PAST → REMITTED → OPEN.")
        MapGhost("advance branch day →", onClick = { repo.advanceDay() })
        Text("DEMO", fontFamily = MapMono, fontSize = 11.sp, letterSpacing = 3.sp, color = NodeDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardBtn("switch branch", onClick = onSwitchBranch)
            CardBtn("reset demo", onClick = onReset)
        }
        MapGhost("log out ⟨", onClick = onLogout)
    }
}
