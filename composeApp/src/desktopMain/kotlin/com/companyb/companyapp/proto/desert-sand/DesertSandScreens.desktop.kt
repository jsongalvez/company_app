package com.companyb.companyapp.proto.desertsand

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #808 — desert-sand screens. Full fake-data flows for every prototype stop:
// onboarding lock, login, branch select, clock-in home, relief, sessions,
// clients, branch-day banner, finance, commission note, team, mail, audit,
// profile with logout and clock-out.

@Composable
fun DsLoginScreen(repo: DsFakeRepo) {
    Box(Modifier.fillMaxSize().background(DsInk).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DESERT SAND", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 4.sp, color = DsSunGold)
            Text("High-noon clarity for branch ops. Sign in with fake crew.", fontFamily = DsSans, fontSize = 13.sp, color = DsAdobeEdge)
            DsAdobeCard(accent = DsSunGold) {
                DsSectionLabel("Crew login")
                Spacer(Modifier.height(8.dp))
                DsTextInput(repo.loginName, "login e.g. maria.coord") { repo.loginName = it }
                if (repo.loginError.isNotEmpty()) {
                    Text(repo.loginError, fontFamily = DsSans, fontSize = 12.sp, color = DsDanger)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DsButton("Sign in", { repo.tryLogin() })
                    DsButton("Fill demo login", { repo.loginName = "maria.coord" }, primary = false)
                }
            }
            DsAdobeCard(accent = DsTurquoise) {
                Text("ONBOARDING accounts stay locked here — activation happens outside this board.", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
            }
        }
    }
}

@Composable
fun DsTextInput(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = DsSans, fontSize = 13.sp, color = DsInk),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, fontFamily = DsSans, fontSize = 13.sp, color = DsInkSoft)
                inner()
            },
        )
    }
}

@Composable
fun DsDayBanner(repo: DsFakeRepo) {
    val branch = repo.currentBranch()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DsInk).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DsSunGold).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("BRANCH DAY", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DsInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${branch.name} — ${branch.dayStatus}", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text("Boundary 04:00 Asia/Manila — OPEN rolls to PAST, REMITTED locks the books.", fontFamily = DsSans, fontSize = 11.sp, color = DsAdobeEdge)
        }
        Spacer(Modifier.width(12.dp))
        DsChip(branch.dayStatus.name, DsDayColor(branch.dayStatus))
    }
}

@Composable
fun DsHomeScreen(repo: DsFakeRepo) {
    val me = repo.loggedInUser
    DsDayBanner(repo)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            DsAdobeCard(accent = if (repo.clockedIn) DsPalm else DsInkSoft) {
                DsSectionLabel("Shift")
                Text(if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Not clocked in", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DsInk)
                Text("Signed in as ${me?.name} (${me?.role})", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!repo.clockedIn) {
                        DsButton("Clock in", {
                            repo.clockedIn = true
                            repo.auditAs(me?.login ?: "?", "clocked in at ${repo.currentBranch().name}")
                        })
                    } else {
                        DsButton("Clock out", {
                            repo.clockedIn = false
                            repo.auditAs(me?.login ?: "?", "clocked out")
                        }, primary = false)
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            DsAdobeCard(accent = DsTurquoise) {
                DsSectionLabel("Branch select")
                for (b in repo.branches) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (b.id == repo.currentBranchId) DsTurquoisePale else Color.Transparent)
                            .clickable {
                                repo.currentBranchId = b.id
                                repo.auditAs(me?.login ?: "?", "switched branch to ${b.name}")
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.name, fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DsInk, modifier = Modifier.weight(1f))
                        DsChip(b.dayStatus.name, DsDayColor(b.dayStatus))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    DsSectionLabel("Relief board — duty, invites, requests")
    Spacer(Modifier.height(6.dp))
    for (item in repo.relief) {
        DsAdobeCard(accent = DsSunGold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.kind.uppercase() + " — " + item.detail, fontFamily = DsSans, fontSize = 13.sp, color = DsInk)
                    Text("state: ${item.state}", fontFamily = DsSans, fontSize = 11.sp, color = DsInkSoft)
                }
                if (item.state == "open") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DsButton("Accept", {
                            item.state = "accepted"
                            repo.auditAs(me?.login ?: "?", "accepted relief ${item.id}")
                        })
                        DsButton("Decline", {
                            item.state = "declined"
                            repo.auditAs(me?.login ?: "?", "declined relief ${item.id}")
                        }, primary = false)
                    }
                } else {
                    DsButton("Reopen", { item.state = "open" }, primary = false)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DsSessionsScreen(repo: DsFakeRepo) {
    val filters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in filters) {
            val on = repo.sessionFilter == f
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) DsInk else Color.White)
                    .clickable { repo.sessionFilter = f }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(f, fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (on) Color.White else DsInk) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Walk-in rule: walk-ins never take NO_SHOW or CANCELLED — they simply leave no booking behind.", fontFamily = DsSans, fontSize = 11.sp, color = DsMesa)
    Spacer(Modifier.height(8.dp))
    val selected = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
    if (selected != null) {
        DsSessionDetail(repo, selected)
        Spacer(Modifier.height(8.dp))
    }
    for (s in repo.branchSessions()) {
        DsAdobeCard(accent = DsStatusColor(s.status), onClick = { repo.selectedSessionId = s.id }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.time} — ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk)
                    Text("${s.kind} · ${s.practitioners} · P${s.price}${if (s.voided) " · VOIDED: ${s.voidReason}" else ""}", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                }
                DsChip(s.status.name, DsStatusColor(s.status))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DsSessionDetail(repo: DsFakeRepo, s: DsSession) {
    val me = repo.loggedInUser?.login ?: "?"
    DsAdobeCard(accent = DsInk) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Session ${s.id} detail", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DsDuneDeep).clickable { repo.selectedSessionId = null }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("close", fontFamily = DsSans, fontSize = 11.sp, color = DsInk)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Move status:", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (st in DsSessionStatus.values()) {
                val banned = s.walkIn && (st == DsSessionStatus.NO_SHOW || st == DsSessionStatus.CANCELLED)
                if (banned) continue
                DsButton(st.name, {
                    s.status = st
                    repo.auditAs(me, "moved session ${s.id} to $st")
                }, primary = st == s.status)
            }
        }
        Spacer(Modifier.height(8.dp))
        DsTextInput(repo.voidDraft, "void reason (required to void)") { repo.voidDraft = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.voided) {
                DsButton("Void with reason", {
                    if (repo.voidDraft.isNotBlank()) {
                        s.voided = true
                        s.voidReason = repo.voidDraft.trim()
                        repo.voidDraft = ""
                        repo.auditAs(me, "voided session ${s.id} (${s.voidReason})")
                    }
                })
            } else {
                DsButton("Unvoid", {
                    s.voided = false
                    repo.auditAs(me, "unvoided session ${s.id}")
                }, primary = false)
            }
        }
    }
}

@Composable
fun DsClientsScreen(repo: DsFakeRepo) {
    DsTextInput(repo.clientQuery, "search clients (global)") { repo.clientQuery = it }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Clients are global across branches. At most one PENDING session per client.", fontFamily = DsSans, fontSize = 11.sp, color = DsMesa, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { repo.showAnonymized = !repo.showAnonymized }) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (repo.showAnonymized) DsTurquoise else DsAdobeEdge).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (repo.showAnonymized) "ON" else "OFF", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Text("anonymized only", fontFamily = DsSans, fontSize = 11.sp, color = DsInk)
        }
    }
    Spacer(Modifier.height(8.dp))
    for (c in repo.filteredClients()) {
        DsAdobeCard(accent = if (c.anonymized) DsTurquoise else DsScorch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (c.anonymized) "Anonymized record ${c.id}" else c.name, fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk)
                    Text(if (c.anonymized) "contact withheld · privacy view" else "${c.contact} · ${c.gender}/${c.age}", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                }
                if (c.pendingCount > 0) DsChip("PENDING x${c.pendingCount}", DsSunGold, DsInk)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DsFinanceScreen(repo: DsFakeRepo) {
    val me = repo.loggedInUser?.login ?: "?"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (flow in listOf("SESSION", "PRODUCT")) {
            val on = repo.financeFlow == flow
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) DsScorch else Color.White)
                    .clickable { repo.financeFlow = flow }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(flow, fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) Color.White else DsInk) }
        }
    }
    Spacer(Modifier.height(8.dp))
    for (r in repo.remittances.filter { it.flow == repo.financeFlow }) {
        DsAdobeCard(accent = DsTurquoise) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.id} · ${r.branchName} · P${r.amount}", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk)
                    Text("stage: ${r.stage}" + (r.ageHours?.let { " · snapshot age ${it}h" } ?: ""), fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                }
                DsChip(r.stage, if (r.stage == "snapshot") DsMesa else DsTurquoise)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.stage == "draft") DsButton("Submit", {
                    r.stage = "submitted"
                    repo.auditAs(me, "submitted remittance ${r.id}")
                })
                if (r.stage == "submitted") DsButton("Snapshot", {
                    r.stage = "snapshot"
                    repo.auditAs(me, "took snapshot for ${r.id}")
                })
                if (r.stage == "snapshot") {
                    val undoable = (r.ageHours ?: 0) <= 48
                    if (undoable) DsButton("Undo (48h)", {
                        r.stage = "draft"
                        repo.auditAs(me, "undid snapshot ${r.id} within 48h")
                    }, primary = false)
                    else Text("Undo expired — older than 48h.", fontFamily = DsSans, fontSize = 11.sp, color = DsDanger)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    DsAdobeCard(accent = DsSunGold) {
        DsSectionLabel("Commission split note")
        Text("Completed sessions split commission across listed practitioners at payout; voided sessions pay nothing.", fontFamily = DsSans, fontSize = 12.sp, color = DsInk)
    }
}

@Composable
fun DsTeamScreen(repo: DsFakeRepo) {
    for (u in repo.users) {
        DsAdobeCard(accent = if (u.locked) DsInkSoft else DsPalm) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${u.name} — ${u.role}", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk)
                    Text("${u.login} · home ${u.homeBranchId}${if (u.locked) " · LOCKED" else ""}", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                    Text("can: " + (u.capabilities.ifEmpty { listOf("none until activation") }.joinToString(", ")), fontFamily = DsSans, fontSize = 11.sp, color = DsInkSoft)
                }
                DsChip(u.role, if (u.locked) DsInkSoft else DsPalm)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DsMailScreen(repo: DsFakeRepo) {
    val me = repo.loggedInUser?.login ?: "?"
    for (n in repo.notices) {
        DsAdobeCard(accent = if (n.read) DsAdobeEdge else DsScorch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsInk)
                    Text(n.body, fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
                }
                if (!n.read) {
                    DsButton("Mark read", {
                        n.read = true
                        repo.auditAs(me, "read notice ${n.id}")
                    }, primary = false)
                } else {
                    DsChip("read", DsAdobeEdge, DsInk)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DsAuditScreen(repo: DsFakeRepo) {
    DsSectionLabel("Audit log — newest first")
    Spacer(Modifier.height(6.dp))
    for (e in repo.audit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DsInk).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("#${e.seq}", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DsSunGold)
            }
            Spacer(Modifier.width(10.dp))
            Text("${e.actor} — ${e.action}", fontFamily = DsSans, fontSize = 12.sp, color = DsInk)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun DsProfileScreen(repo: DsFakeRepo) {
    val me = repo.loggedInUser
    DsAdobeCard(accent = DsScorch) {
        DsSectionLabel("Profile")
        Text(me?.name ?: "—", fontFamily = DsSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DsInk)
        Text("${me?.role} · ${me?.login} · home ${me?.homeBranchId}", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
        Text("Shift: " + if (repo.clockedIn) "clocked in" else "clocked out", fontFamily = DsSans, fontSize = 12.sp, color = DsInkSoft)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) DsButton("Clock out", {
                repo.clockedIn = false
                repo.auditAs(me?.login ?: "?", "clocked out")
            }, primary = false)
            DsButton("Log out", {
                repo.auditAs(me?.login ?: "?", "logged out")
                repo.loggedInUser = null
                repo.clockedIn = false
                repo.screen = DsScreen.HOME
            })
        }
    }
    Spacer(Modifier.height(12.dp))
    Divider(color = DsAdobeEdge)
    Spacer(Modifier.height(12.dp))
    Text("Fake-data prototype — desert-sand. No network, no backend, nothing leaves this window.", fontFamily = DsSans, fontSize = 11.sp, color = DsMesa)
}

@Composable
fun DsShell(repo: DsFakeRepo) {
    if (repo.loggedInUser == null) {
        DsLoginScreen(repo)
        return
    }
    Row(Modifier.fillMaxSize()) {
        DsNavRail(
            items = listOf(
                DsScreen.HOME to "Home & clock",
                DsScreen.SESSIONS to "Sessions",
                DsScreen.CLIENTS to "Clients",
                DsScreen.FINANCE to "Finance",
                DsScreen.TEAM to "Team",
                DsScreen.MAIL to "Mailbox",
                DsScreen.AUDIT to "Audit log",
                DsScreen.PROFILE to "Profile",
            ),
            current = repo.screen,
            unread = repo.unreadCount(),
            onPick = { repo.screen = it },
        )
        Column(
            Modifier.weight(1f).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            when (repo.screen) {
                DsScreen.HOME -> DsHomeScreen(repo)
                DsScreen.SESSIONS -> DsSessionsScreen(repo)
                DsScreen.CLIENTS -> DsClientsScreen(repo)
                DsScreen.FINANCE -> DsFinanceScreen(repo)
                DsScreen.TEAM -> DsTeamScreen(repo)
                DsScreen.MAIL -> DsMailScreen(repo)
                DsScreen.AUDIT -> DsAuditScreen(repo)
                DsScreen.PROFILE -> DsProfileScreen(repo)
            }
        }
    }
}
