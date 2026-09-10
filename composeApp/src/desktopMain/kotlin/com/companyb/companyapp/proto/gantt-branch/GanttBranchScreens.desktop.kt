package com.companyb.companyapp.proto.ganttbranch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #815 — gantt-branch screens. Branch week on a time axis: practitioner rows,
// session blocks, amber 04:00 day-boundary line. All prototype flows run on
// fake data: login, clock-in home, relief, sessions, clients, branch-day
// banner, finance, commission note, team, mailbox, audit, profile.

@Composable
fun GbLoginScreen(repo: GbFakeRepo) {
    Box(Modifier.fillMaxSize().background(GbBlockInk).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("GANTT BRANCH", fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 3.sp, color = GbSignalAmber)
            Text("Branch week as a dispatch strip. Practitioner rows, session blocks, one amber boundary.", fontFamily = GbSans, fontSize = 13.sp, color = GbTextDim)
            GbCard {
                GbSectionLabel("Crew login")
                Spacer(Modifier.height(8.dp))
                GbTextInput(repo.loginName, "login e.g. maria.coord") { repo.loginName = it }
                if (repo.loginError.isNotEmpty()) {
                    Text(repo.loginError, fontFamily = GbSans, fontSize = 12.sp, color = GbCancelled)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GbButton("Sign in", { repo.tryLogin() })
                    GbButton("Fill demo login", { repo.loginName = "maria.coord" }, primary = false)
                }
            }
            GbCard {
                Text("ONBOARDING accounts stay locked here — activation happens outside this strip.", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
            }
        }
    }
}

@Composable
fun GbTextInput(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(GbBlockInk).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = GbSans, fontSize = 13.sp, color = GbText),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, fontFamily = GbSans, fontSize = 13.sp, color = GbAxisInk)
                inner()
            },
        )
    }
}

@Composable
fun GbDayBanner(repo: GbFakeRepo) {
    val branch = repo.currentBranch()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(GbBlockInk).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(GbSignalAmber).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("BRANCH DAY", fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GbBlockInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${branch.name} — ${branch.dayStatus}", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GbText)
            Text("Boundary 04:00 Asia/Manila — OPEN rolls to PAST, REMITTED locks the books.", fontFamily = GbSans, fontSize = 11.sp, color = GbTextDim)
        }
        Spacer(Modifier.width(12.dp))
        GbChip(branch.dayStatus.name, GbDayColor(branch.dayStatus), if (branch.dayStatus == GbDayStatus.REMITTED) GbBlockInk else Color.White)
    }
}

@Composable
fun GbTimeAxis() {
    Row(Modifier.fillMaxWidth().padding(start = 148.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (h in listOf("08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00")) {
            Text(h, fontFamily = GbMono, fontSize = 10.sp, color = GbAxisInk)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun GbGanttBlock(repo: GbFakeRepo, s: GbSession) {
    val span = (GbAxisEnd - GbAxisStart).toFloat()
    val startW = ((s.startMin - GbAxisStart).coerceAtLeast(0) / span).coerceIn(0f, 0.97f)
    val durW = (s.durMin / span).coerceIn(0.03f, 1f)
    val endW = (1f - startW - durW).coerceAtLeast(0.005f)
    val selected = repo.selectedSessionId == s.id
    Row(Modifier.fillMaxWidth().height(46.dp)) {
        Spacer(Modifier.weight(startW))
        Box(
            modifier = Modifier
                .weight(durW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(if (s.voided) GbVoid else GbStatusColor(s.status))
                .clickable { repo.selectedSessionId = if (selected) null else s.id }
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Column {
                Text(
                    "${s.label()} ${s.clientName}${if (s.voided) " VOID" else ""}",
                    fontFamily = GbSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    s.status.name,
                    fontFamily = GbMono,
                    fontSize = 9.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.weight(endW))
    }
}

@Composable
fun GbGanttStrip(repo: GbFakeRepo) {
    GbCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GbSectionLabel("Branch week gantt — ${repo.currentBranch().name}")
            Spacer(Modifier.weight(1f))
            Text("amber rail = 04:00 Manila boundary", fontFamily = GbMono, fontSize = 10.sp, color = GbSignalAmber)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in GbWeekDays.indices) {
                val on = repo.ganttDay == d
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (on) GbSignalAmber else GbPanelEdge)
                        .clickable { repo.ganttDay = d }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(GbWeekDays[d], fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) GbBlockInk else GbText)
                }
            }
            Spacer(Modifier.weight(1f))
            GbChip("${GbWeekDays[repo.ganttDay]} strip", GbGrid, GbText)
        }
        Spacer(Modifier.height(10.dp))
        GbTimeAxis()
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(6.dp).height(220.dp).background(GbSignalAmber, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in repo.practitioners) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(132.dp)) {
                            Text(p, fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GbText, maxLines = 1)
                            Text("${repo.ganttSessions().count { it.practitioner == p }} blocks", fontFamily = GbMono, fontSize = 10.sp, color = GbAxisInk)
                        }
                        Box(Modifier.weight(1f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val blocks = repo.ganttSessions().filter { it.practitioner == p }
                                if (blocks.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(5.dp)).background(GbDarkRow)) {
                                        Text("open lane", fontFamily = GbMono, fontSize = 10.sp, color = GbAxisInk, modifier = Modifier.align(Alignment.Center))
                                    }
                                } else {
                                    for (s in blocks) GbGanttBlock(repo, s)
                                }
                            }
                        }
                    }
                    Divider(color = GbGrid)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Sessions crossing 04:00 belong to the prior Branch Day. Tap a block to open its detail below.", fontFamily = GbSans, fontSize = 11.sp, color = GbTextDim)
    }
}

@Composable
fun GbSessionDetail(repo: GbFakeRepo, s: GbSession) {
    val me = repo.loggedInUser?.login ?: "?"
    GbCard(edge = GbSignalAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Session ${s.id} — ${s.label()} ${GbWeekDays[s.day]}", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(GbPanelEdge).clickable { repo.selectedSessionId = null }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("close", fontFamily = GbSans, fontSize = 11.sp, color = GbText)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${s.clientName}${if (s.walkIn) " (walk-in)" else ""} · ${s.kind} · ${s.practitioner} · P${s.price}${if (s.voided) " · VOIDED: ${s.voidReason}" else ""}", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
        Spacer(Modifier.height(8.dp))
        Text("Move status:", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (st in GbSessionStatus.values()) {
                val banned = s.walkIn && (st == GbSessionStatus.NO_SHOW || st == GbSessionStatus.CANCELLED)
                if (banned) continue
                GbButton(st.name, {
                    s.status = st
                    repo.auditAs(me, "moved session ${s.id} to $st")
                }, primary = st == s.status)
            }
        }
        Spacer(Modifier.height(8.dp))
        GbTextInput(repo.voidDraft, "void reason (required to void)") { repo.voidDraft = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.voided) {
                GbButton("Void with reason", {
                    if (repo.voidDraft.isNotBlank()) {
                        s.voided = true
                        s.voidReason = repo.voidDraft.trim()
                        repo.voidDraft = ""
                        repo.auditAs(me, "voided session ${s.id} (${s.voidReason})")
                    }
                })
            } else {
                GbButton("Unvoid", {
                    s.voided = false
                    repo.auditAs(me, "unvoided session ${s.id}")
                }, primary = false)
            }
        }
    }
}

@Composable
fun GbHomeScreen(repo: GbFakeRepo) {
    val me = repo.loggedInUser
    GbDayBanner(repo)
    Spacer(Modifier.height(12.dp))
    GbGanttStrip(repo)
    Spacer(Modifier.height(12.dp))
    val selected = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
    if (selected != null) {
        GbSessionDetail(repo, selected)
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            GbCard {
                GbSectionLabel("Shift")
                Text(if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Not clocked in", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GbText)
                Text("Signed in as ${me?.name} (${me?.role})", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!repo.clockedIn) {
                        GbButton("Clock in", {
                            repo.clockedIn = true
                            repo.auditAs(me?.login ?: "?", "clocked in at ${repo.currentBranch().name}")
                        })
                    } else {
                        GbButton("Clock out", {
                            repo.clockedIn = false
                            repo.auditAs(me?.login ?: "?", "clocked out")
                        }, primary = false)
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            GbCard {
                GbSectionLabel("Branch select")
                for (b in repo.branches) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (b.id == repo.currentBranchId) GbSignalAmberPale else Color.Transparent)
                            .clickable {
                                repo.currentBranchId = b.id
                                repo.auditAs(me?.login ?: "?", "switched branch to ${b.name}")
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.name, fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GbText, modifier = Modifier.weight(1f))
                        GbChip(b.dayStatus.name, GbDayColor(b.dayStatus), if (b.dayStatus == GbDayStatus.REMITTED) GbBlockInk else Color.White)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    GbSectionLabel("Relief board — duty, invites, requests")
    Spacer(Modifier.height(6.dp))
    for (item in repo.relief) {
        GbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.kind.uppercase() + " — " + item.detail, fontFamily = GbSans, fontSize = 13.sp, color = GbText)
                    Text("state: ${item.state}", fontFamily = GbMono, fontSize = 11.sp, color = GbTextDim)
                }
                if (item.state == "open") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GbButton("Accept", {
                            item.state = "accepted"
                            repo.auditAs(me?.login ?: "?", "accepted relief ${item.id}")
                        })
                        GbButton("Decline", {
                            item.state = "declined"
                            repo.auditAs(me?.login ?: "?", "declined relief ${item.id}")
                        }, primary = false)
                    }
                } else {
                    GbButton("Reopen", { item.state = "open" }, primary = false)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GbSessionsScreen(repo: GbFakeRepo) {
    val filters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in filters) {
            val on = repo.sessionFilter == f
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) GbSignalAmber else GbPanelEdge)
                    .clickable { repo.sessionFilter = f }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(f, fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (on) GbBlockInk else GbText) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Walk-in rule: walk-ins never take NO_SHOW or CANCELLED — they simply leave no booking behind.", fontFamily = GbSans, fontSize = 11.sp, color = GbSignalAmber)
    Spacer(Modifier.height(8.dp))
    val selected = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
    if (selected != null) {
        GbSessionDetail(repo, selected)
        Spacer(Modifier.height(8.dp))
    }
    for (s in repo.branchSessions()) {
        GbCard(onClick = { repo.selectedSessionId = s.id }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).height(40.dp).background(if (s.voided) GbVoid else GbStatusColor(s.status), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${GbWeekDays[s.day]} ${s.label()} — ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText)
                    Text("${s.kind} · ${s.practitioner} · P${s.price}${if (s.voided) " · VOIDED: ${s.voidReason}" else ""}", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                }
                GbChip(s.status.name, GbStatusColor(s.status))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GbClientsScreen(repo: GbFakeRepo) {
    GbTextInput(repo.clientQuery, "search clients (global)") { repo.clientQuery = it }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Clients are global across branches. At most one PENDING session per client.", fontFamily = GbSans, fontSize = 11.sp, color = GbSignalAmber, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { repo.showAnonymized = !repo.showAnonymized }) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (repo.showAnonymized) GbPending else GbPanelEdge).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (repo.showAnonymized) "ON" else "OFF", fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Text("anonymized only", fontFamily = GbSans, fontSize = 11.sp, color = GbText)
        }
    }
    Spacer(Modifier.height(8.dp))
    for (c in repo.filteredClients()) {
        GbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (c.anonymized) "Anonymized record ${c.id}" else c.name, fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText)
                    Text(if (c.anonymized) "contact withheld · privacy view" else "${c.contact} · ${c.gender}/${c.age}", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                }
                if (c.pendingCount > 0) GbChip("PENDING x${c.pendingCount}", GbSignalAmber, GbBlockInk)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GbFinanceScreen(repo: GbFakeRepo) {
    val me = repo.loggedInUser?.login ?: "?"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (flow in listOf("SESSION", "PRODUCT")) {
            val on = repo.financeFlow == flow
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) GbSignalAmber else GbPanelEdge)
                    .clickable { repo.financeFlow = flow }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(flow, fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) GbBlockInk else GbText) }
        }
    }
    Spacer(Modifier.height(8.dp))
    for (r in repo.remittances.filter { it.flow == repo.financeFlow }) {
        GbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.id} · ${r.branchName} · P${r.amount}", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText)
                    Text("stage: ${r.stage}" + (r.ageHours?.let { " · snapshot age ${it}h" } ?: ""), fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                }
                GbChip(r.stage, if (r.stage == "snapshot") GbSignalAmber else GbPending, if (r.stage == "snapshot") GbBlockInk else Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.stage == "draft") GbButton("Submit", {
                    r.stage = "submitted"
                    repo.auditAs(me, "submitted remittance ${r.id}")
                })
                if (r.stage == "submitted") GbButton("Snapshot", {
                    r.stage = "snapshot"
                    repo.auditAs(me, "took snapshot for ${r.id}")
                })
                if (r.stage == "snapshot") {
                    val undoable = (r.ageHours ?: 0) <= 48
                    if (undoable) GbButton("Undo (48h)", {
                        r.stage = "draft"
                        repo.auditAs(me, "undid snapshot ${r.id} within 48h")
                    }, primary = false)
                    else Text("Undo expired — older than 48h.", fontFamily = GbSans, fontSize = 11.sp, color = GbCancelled)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    GbCard {
        GbSectionLabel("Commission split note")
        Text("Completed sessions split commission across listed practitioners at payout; voided sessions pay nothing.", fontFamily = GbSans, fontSize = 12.sp, color = GbText)
    }
}

@Composable
fun GbTeamScreen(repo: GbFakeRepo) {
    for (u in repo.users) {
        GbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${u.name} — ${u.role}", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText)
                    Text("${u.login} · home ${u.homeBranchId}${if (u.locked) " · LOCKED" else ""}", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                    Text("can: " + (u.capabilities.ifEmpty { listOf("none until activation") }.joinToString(", ")), fontFamily = GbMono, fontSize = 11.sp, color = GbTextDim)
                }
                GbChip(u.role, if (u.locked) GbNoShow else GbCompleted)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GbMailScreen(repo: GbFakeRepo) {
    val me = repo.loggedInUser?.login ?: "?"
    for (n in repo.notices) {
        GbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GbText)
                    Text(n.body, fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
                }
                if (!n.read) {
                    GbButton("Mark read", {
                        n.read = true
                        repo.auditAs(me, "read notice ${n.id}")
                    }, primary = false)
                } else {
                    GbChip("read", GbPanelEdge, GbText)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GbAuditScreen(repo: GbFakeRepo) {
    GbSectionLabel("Audit log — newest first")
    Spacer(Modifier.height(6.dp))
    for (e in repo.audit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(GbPanel).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(GbBlockInk).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("#${e.seq}", fontFamily = GbMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GbSignalAmber)
            }
            Spacer(Modifier.width(10.dp))
            Text("${e.actor} — ${e.action}", fontFamily = GbSans, fontSize = 12.sp, color = GbText)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun GbProfileScreen(repo: GbFakeRepo, onBack: () -> Unit) {
    val me = repo.loggedInUser
    GbCard {
        GbSectionLabel("Profile")
        Text(me?.name ?: "—", fontFamily = GbSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GbText)
        Text("${me?.role} · ${me?.login} · home ${me?.homeBranchId}", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
        Text("Shift: " + if (repo.clockedIn) "clocked in" else "clocked out", fontFamily = GbSans, fontSize = 12.sp, color = GbTextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) GbButton("Clock out", {
                repo.clockedIn = false
                repo.auditAs(me?.login ?: "?", "clocked out")
            }, primary = false)
            GbButton("Log out", {
                repo.auditAs(me?.login ?: "?", "logged out")
                repo.loggedInUser = null
                repo.clockedIn = false
                repo.screen = GbScreen.HOME
            })
        }
    }
    Spacer(Modifier.height(12.dp))
    Divider(color = GbGrid)
    Spacer(Modifier.height(12.dp))
    Text("Fake-data prototype — gantt-branch. No network, no backend, nothing leaves this window.", fontFamily = GbSans, fontSize = 11.sp, color = GbTextDim)
    Spacer(Modifier.height(8.dp))
    GbButton("Exit prototype", onBack, primary = false)
}

@Composable
fun GbShell(repo: GbFakeRepo, onBack: () -> Unit) {
    if (repo.loggedInUser == null) {
        GbLoginScreen(repo)
        return
    }
    Row(Modifier.fillMaxSize()) {
        GbNavRail(
            items = listOf(
                GbScreen.HOME to "Week gantt",
                GbScreen.SESSIONS to "Sessions",
                GbScreen.CLIENTS to "Clients",
                GbScreen.FINANCE to "Finance",
                GbScreen.TEAM to "Team",
                GbScreen.MAIL to "Mailbox",
                GbScreen.AUDIT to "Audit log",
                GbScreen.PROFILE to "Profile",
            ),
            current = repo.screen,
            unread = repo.unreadCount(),
            onPick = { repo.screen = it },
        )
        Column(
            Modifier.weight(1f).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            when (repo.screen) {
                GbScreen.HOME -> GbHomeScreen(repo)
                GbScreen.SESSIONS -> GbSessionsScreen(repo)
                GbScreen.CLIENTS -> GbClientsScreen(repo)
                GbScreen.FINANCE -> GbFinanceScreen(repo)
                GbScreen.TEAM -> GbTeamScreen(repo)
                GbScreen.MAIL -> GbMailScreen(repo)
                GbScreen.AUDIT -> GbAuditScreen(repo)
                GbScreen.PROFILE -> GbProfileScreen(repo, onBack)
            }
        }
    }
}
