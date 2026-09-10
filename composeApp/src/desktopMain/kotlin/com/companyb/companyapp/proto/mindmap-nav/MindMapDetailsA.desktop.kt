package com.companyb.companyapp.proto.mindmapnav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
fun MapHomeDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit, onOpen: (MapNode) -> Unit) {
    var askBranch by remember { mutableStateOf("Cebu Tour") }
    var askDay by remember { mutableStateOf("Friday") }
    var askNote by remember { mutableStateOf("") }
    DetailScroll("Home orbit", crumb, onZoomOut) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("pending today", repo.pendingCount().toString(), Honey)
            StatCard("unread mail", repo.unreadCount().toString(), Coral)
            StatCard("shift", if (repo.clockedIn) "IN" else "OUT", Leaf)
        }
        NoteCard(if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}. Relief duty at a non-home Branch starts view-only; edit needs a relief grant." else "Off shift. Clock in to start the day at ${repo.currentBranch().name}.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) {
                Box(Modifier.weight(1f)) { MapGhost("clock out ○", onClick = { repo.clock(false) }) }
            } else {
                Box(Modifier.weight(1f)) { MapButton("clock in ●", onClick = { repo.clock(true) }) }
            }
            Box(Modifier.weight(1f)) { MapGhost("open sessions →", onClick = { onOpen(MapNode.SESSIONS) }) }
        }
        Text("RELIEF ORBITS", fontFamily = MapMono, fontSize = 11.sp, letterSpacing = 3.sp, color = NodeDim)
        if (repo.relief.isEmpty()) NoteCard("No relief threads. Ask for cover or answer an invite to grow this orbit.")
        repo.relief.forEach { r ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (r.kind == MapReliefKind.INVITE) "◈ INVITE" else "◇ REQUEST", fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Moss, modifier = Modifier.weight(1f))
                    Text(if (r.answered.isBlank()) "UNANSWERED" else r.answered.uppercase(), fontFamily = MapMono, fontSize = 11.sp, color = if (r.answered.isBlank()) Honey else Leaf)
                }
                Text("${r.branch} · ${r.day}", fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NodeInk)
                Text(r.note, fontFamily = MapSans, fontSize = 12.sp, color = NodeDim)
                if (r.answered.isBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardBtn("accept", onClick = { repo.answerRelief(r.id, "accepted") })
                        CardBtn("decline", onClick = { repo.answerRelief(r.id, "declined") })
                        CardBtn("withdraw", onClick = { repo.answerRelief(r.id, "withdrawn") })
                    }
                }
            }
        }
        Text("ASK FOR RELIEF", fontFamily = MapMono, fontSize = 11.sp, letterSpacing = 3.sp, color = NodeDim)
        MapField(askBranch, { askBranch = it }, "Branch")
        MapField(askDay, { askDay = it }, "Day")
        MapField(askNote, { askNote = it }, "Note (optional)")
        MapButton("broadcast request ◇", onClick = { repo.askRelief(askBranch.ifBlank { "Cebu Tour" }, askDay.ifBlank { "Friday" }, askNote.ifBlank { "Cover my evening orbit." }) })
        NoteCard("Relief expires 04:00 Manila next day. Pay comes from the relief Branch drawer. One live request per requester per Branch per date.")
    }
}

@Composable
fun MapSessionsDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    var filter by remember { mutableStateOf<MapStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidFor by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var nc by remember { mutableStateOf("") }
    var ns by remember { mutableStateOf("") }
    var na by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(true) }
    val list = repo.branchSessions().filter { filter == null || it.status == filter }
    DetailScroll("Session constellation", crumb, onZoomOut) {
        NoteCard("Walk-in Sessions can never be NO_SHOW or CANCELLED — they are already here. PENDING → COMPLETED / NO_SHOW / CANCELLED.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapChip("ALL", filter == null, { filter = null })
            MapStatus.entries.forEach { s -> MapChip(s.name, filter == s, { filter = if (filter == s) null else s }, s.tint()) }
        }
        list.forEach { s ->
            val open = expanded == s.id
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, s.status.tint(), RoundedCornerShape(12.dp)).clickable { expanded = if (open) null else s.id }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.id, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NodeInk, modifier = Modifier.weight(1f))
                    Text(if (s.kind == MapKind.WALK_IN) "WALK-IN" else "BOOKED", fontFamily = MapMono, fontSize = 10.sp, color = Brook)
                    Text(s.status.name, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = s.status.tint())
                    if (s.voided) Text("VOID", fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Coral)
                }
                Text("${s.client} · ${s.service}", fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NodeInk)
                Text("${s.time} · ${s.practitioner} · ₱${s.amount}", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim)
                if (open) {
                    if (s.voided) {
                        NoteCard("Voided: ${s.voidReason}", Coral)
                        MapField(reason, { reason = it }, "Restore reason")
                        MapButton("unvoid ✦", onClick = { repo.unvoidSession(s.id, reason.ifBlank { "entered in error" }); reason = "" })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.status == MapStatus.PENDING) {
                                CardBtn("complete", onClick = { repo.setStatus(s.id, MapStatus.COMPLETED) })
                                if (s.kind == MapKind.BOOKED) {
                                    CardBtn("no-show", onClick = { repo.setStatus(s.id, MapStatus.NO_SHOW) })
                                    CardBtn("cancel", onClick = { repo.setStatus(s.id, MapStatus.CANCELLED) })
                                }
                            } else {
                                CardBtn("reopen", onClick = { repo.setStatus(s.id, MapStatus.PENDING) })
                            }
                        }
                        if (voidFor == s.id) {
                            MapField(reason, { reason = it }, "Void reason (required)")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardBtn("confirm void", onClick = { repo.voidSession(s.id, reason.ifBlank { "duplicate entry" }); reason = ""; voidFor = null })
                                CardBtn("keep", onClick = { voidFor = null })
                            }
                        } else {
                            MapGhost("void with reason…", onClick = { voidFor = s.id })
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) NoteCard("This orbit is empty for the current filter.")
        Text("NEW STAR", fontFamily = MapMono, fontSize = 11.sp, letterSpacing = 3.sp, color = NodeDim)
        MapField(nc, { nc = it }, "Client name")
        MapField(ns, { ns = it }, "Service")
        MapField(na, { na = it }, "Amount")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapChip("WALK-IN", walkIn, { walkIn = true }, Brook)
            MapChip("BOOKED", !walkIn, { walkIn = false }, Moss)
        }
        MapButton("plot session ✦", onClick = { repo.addSession(nc.ifBlank { "Walk-in Guest" }, ns.ifBlank { "Assessment 30" }, if (walkIn) MapKind.WALK_IN else MapKind.BOOKED, na.toDoubleOrNull() ?: 500.0); nc = ""; ns = ""; na = "" })
    }
}

@Composable
fun MapClientsDetail(repo: MapFakeRepo, crumb: String, onZoomOut: () -> Unit) {
    DetailScroll("Client galaxy", crumb, onZoomOut) {
        NoteCard("Clients are global — shared across every Branch. At most one PENDING Session per Client at a time. Codes stay anonymized until you lift the veil.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (repo.anonymized) "Veil: DOWN (codes only)" else "Veil: LIFTED (names shown)", fontFamily = MapMono, fontSize = 12.sp, color = Honey, modifier = Modifier.weight(1f))
            MapGhost(if (repo.anonymized) "lift veil" else "drop veil", onClick = { repo.anonymized = !repo.anonymized })
        }
        repo.clients.forEach { c ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (c.pending > 0) Honey else Vine).padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (repo.anonymized) "◍" else c.name.take(1), fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Abyss)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (repo.anonymized) c.code else c.name, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NodeInk)
                    Text(if (repo.anonymized) "anonymized · ${c.visits} visits" else "${c.code} · ${c.visits} visits", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim)
                    if (!repo.anonymized) Text(c.note, fontFamily = MapSans, fontSize = 12.sp, color = NodeDim)
                }
                Text(if (c.pending > 0) "● PENDING" else "○ CLEAR", fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (c.pending > 0) Honey else Leaf)
            }
        }
    }
}
