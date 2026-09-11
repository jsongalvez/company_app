package com.companyb.companyapp.proto.inboxzero

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #856 — inbox-zero shell. Notifications-first triage: the morning inbox is the
// home screen, every row triages into a real flow, and clearing the last row
// reveals the day board. Fake data only; no network.

@Composable
fun ProtoInboxZeroApp(onBack: () -> Unit) {
    val repo = remember { IzFakeRepo() }
    var sel by remember { mutableStateOf(IzSel("inbox")) }
    LaunchedEffect(Unit) { logInfo("InboxZeroProto", "inbox-zero prototype launched") }
    repo.version

    fun open(next: IzSel) {
        sel = next
    }

    Box(Modifier.fillMaxSize().background(IzColors.Paper)) {
        Column(Modifier.fillMaxSize()) {
            IzTopBar(repo, sel, onOpen = ::open, onBack = onBack)
            if (repo.actor == null) {
                IzLogin(repo)
            } else if (repo.actor?.role == IzRole.ONBOARDING) {
                IzOnboardingLock(repo)
            } else {
                IzStage(repo, sel, onOpen = ::open)
            }
        }
    }
}

@Composable
private fun IzTopBar(repo: IzFakeRepo, sel: IzSel, onOpen: (IzSel) -> Unit, onBack: () -> Unit) {
    val unread = repo.notices.count { !it.read }
    val inbox = if (repo.actor == null) emptyList() else repo.inbox()
    Column(
        Modifier.fillMaxWidth().background(IzColors.Panel)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("☀ INBOX ZERO", fontSize = 13.sp, fontWeight = FontWeight.Black, color = IzColors.DawnDeep, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            Text(
                "${repo.actorName()} · ${repo.currentBranch.name}",
                fontSize = 12.sp,
                color = IzColors.Dim,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            IzPill(repo.dayState.label, repo.dayState.tone())
            Spacer(Modifier.width(8.dp))
            Text("04:00 Asia/Manila", fontSize = 11.sp, color = IzColors.Faint, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            if (repo.actor != null) IzChip("Advance day", onClick = { repo.cycleDay() })
            Spacer(Modifier.width(8.dp))
            IzChip("exit", onClick = onBack)
        }
        if (repo.actor != null && repo.actor?.role != IzRole.ONBOARDING) {
            Spacer(Modifier.height(8.dp))
            IzInboxProgress(inbox.size)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IzTab("Inbox", inbox.size.toString(), sel.kind == "inbox") { onOpen(IzSel("inbox")) }
                IzTab("Home", null, sel.kind == "home") { onOpen(IzSel("home")) }
                IzTab("Sessions", null, sel.kind == "sessions" || sel.kind == "session") { onOpen(IzSel("sessions")) }
                IzTab("Clients", null, sel.kind == "clients" || sel.kind == "client") { onOpen(IzSel("clients")) }
                IzTab("Finance", null, sel.kind == "finance") { onOpen(IzSel("finance")) }
                IzTab("Team", null, sel.kind == "team") { onOpen(IzSel("team")) }
                IzTab("Notices", unread.toString(), sel.kind == "notices") { onOpen(IzSel("notices")) }
                IzTab("Audit", null, sel.kind == "audit") { onOpen(IzSel("audit")) }
                IzTab("Profile", null, sel.kind == "profile") { onOpen(IzSel("profile")) }
            }
        }
    }
}

@Composable
private fun IzInboxProgress(left: Int) {
    val done = left == 0
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (done) "Inbox zero — the day is yours" else "Morning triage · $left left",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (done) IzColors.Sage else IzColors.DawnDeep,
            )
            Spacer(Modifier.weight(1f))
            Text(if (done) "0 → 0" else "$left → 0", fontSize = 11.sp, color = IzColors.Faint, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)).background(IzColors.Wash)) {
            if (done) {
                Box(Modifier.fillMaxSize().background(IzColors.Sage))
            } else {
                val frac = (1f - left / 12f).coerceIn(0.08f, 0.95f)
                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(6.dp)).background(IzColors.Dawn))
            }
        }
    }
}

@Composable
private fun IzLogin(repo: IzFakeRepo) {
    var picked by remember { mutableStateOf("U-ANN") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Text("Good morning.", fontSize = 34.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
        Text(
            "Your inbox already knows what needs you. Sign in and triage it to zero.",
            fontSize = 13.sp,
            color = IzColors.Dim,
        )
        Spacer(Modifier.height(18.dp))
        IzSection(title = "Who is on shift?") {
            repo.users.forEach { u ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (picked == u.id) "◉" else "○",
                        fontSize = 14.sp,
                        color = IzColors.DawnDeep,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                        Text("${u.role.label} · home ${u.homeBranch}", fontSize = 11.sp, color = IzColors.Dim)
                    }
                    IzChip(if (picked == u.id) "Picked" else "Pick", onClick = { picked = u.id })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        IzChip("Sign in & open inbox", onClick = { repo.login(picked) }, primary = true)
        Spacer(Modifier.height(8.dp))
        Text("Tip: sign in as Sam Rivera to see the ONBOARDING lock.", fontSize = 11.sp, color = IzColors.Faint)
    }
}

@Composable
private fun IzOnboardingLock(repo: IzFakeRepo) {
    val me = repo.actor ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Text("Locked — ONBOARDING", fontSize = 30.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
        Text(
            "Fresh accounts hold zero capabilities, even with a branch assignment. Nothing derives until MANAGE_USERS grants a real role.",
            fontSize = 13.sp,
            color = IzColors.Dim,
        )
        Spacer(Modifier.height(14.dp))
        IzCard {
            IzKey("User", me.name)
            IzKey("Home branch", me.homeBranch)
            IzKey("Capabilities", "none — role bundle is empty")
        }
        Spacer(Modifier.height(14.dp))
        IzChip("Grant Practitioner (simulates MANAGE_USERS)", onClick = { repo.grantPractitioner(me.id) }, primary = true)
        Spacer(Modifier.height(8.dp))
        IzChip("Sign out", onClick = { repo.logout() })
    }
}

@Composable
private fun IzStage(repo: IzFakeRepo, sel: IzSel, onOpen: (IzSel) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        when (sel.kind) {
            "inbox" -> IzInboxView(repo, onOpen)
            "home" -> IzHome(repo, sel.id, onOpen)
            "sessions" -> IzSessions(repo, onOpen)
            "session" -> repo.sessions.firstOrNull { it.id == sel.id }?.let {
                IzSessionDetail(repo, it, onOpenClient = { cid -> onOpen(IzSel("client", cid)) })
            } ?: Text("Session gone.", fontSize = 13.sp, color = IzColors.Dim)
            "clients" -> IzClients(repo, onOpen)
            "client" -> repo.clients.firstOrNull { it.id == sel.id }?.let {
                IzClientDetail(repo, it, onOpenSession = { sid -> onOpen(IzSel("session", sid)) })
            } ?: Text("Client gone.", fontSize = 13.sp, color = IzColors.Dim)
            "finance" -> IzMoney(repo, sel.id)
            "team" -> IzTeam(repo, sel.id, onOpen)
            "notices" -> IzNotices(repo, sel.id)
            "audit" -> IzAudit(repo)
            "profile" -> IzProfile(repo)
            else -> IzInboxView(repo, onOpen)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IzInboxView(repo: IzFakeRepo, onOpen: (IzSel) -> Unit) {
    val items = repo.inbox()
    if (items.isEmpty()) {
        Text("Inbox zero.", fontSize = 34.sp, fontWeight = FontWeight.Black, color = IzColors.Sage)
        Text("Nothing needs you. The day below is fully revealed — pick anywhere to work it.", fontSize = 13.sp, color = IzColors.Dim)
        Spacer(Modifier.height(14.dp))
        IzDayBoard(repo, onOpen)
        return
    }
    Text("Clear the inbox.", fontSize = 30.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Text("Triage opens the flow; the named button resolves it in one tap. The day reveals itself at zero.", fontSize = 13.sp, color = IzColors.Dim)
    Spacer(Modifier.height(12.dp))
    items.forEach { item ->
        IzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IzPill(item.kind, IzKindTone(item.kind))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text(item.sub, fontSize = 11.sp, color = IzColors.Dim)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                IzChip("Triage", onClick = { onOpen(item.target) }, primary = true)
                Spacer(Modifier.width(8.dp))
                IzChip(item.clearLabel, onClick = { repo.clear(item) })
                Spacer(Modifier.width(8.dp))
                if (item.clearLabel != "Snooze") IzChip("Snooze", onClick = { repo.snooze(item) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun IzKindTone(kind: String): IzTone = when (kind) {
    "Clock in" -> IzTone.BLUE
    "Notice" -> IzTone.VIOLET
    "Relief invite", "Relief request" -> IzTone.BLUE
    "Onboarding" -> IzTone.RED
    "Walk-in" -> IzTone.BLUE
    "Remittance" -> IzTone.GOLD
    else -> IzTone.ORANGE
}

@Composable
private fun IzDayBoard(repo: IzFakeRepo, onOpen: (IzSel) -> Unit) {
    val pending = repo.sessions.filter { it.status == IzSessionStatus.PENDING && it.branch == repo.currentBranch.name }
    val drafts = repo.remittances.filter { it.state == IzRemitState.DRAFT }
    IzCard {
        IzKey("Branch day", "${repo.currentBranch.name} · ${repo.dayState.label} · boundary 04:00 Asia/Manila")
        IzKey("Pending sessions here", pending.size.toString())
        IzKey("Open remittance drafts", drafts.size.toString())
        IzKey("Clock", if (repo.clockedIn) "on shift" else "off shift")
        Spacer(Modifier.height(8.dp))
        Row {
            IzChip("Sessions", onClick = { onOpen(IzSel("sessions")) }, primary = true)
            Spacer(Modifier.width(8.dp))
            IzChip("Finance", onClick = { onOpen(IzSel("finance")) })
            Spacer(Modifier.width(8.dp))
            IzChip("Team", onClick = { onOpen(IzSel("team")) })
        }
    }
}

@Composable
private fun IzHome(repo: IzFakeRepo, focusId: String?, onOpen: (IzSel) -> Unit) {
    var askBranch by remember { mutableStateOf("Laguna Tour Stop 3") }
    Text("Home · clock & relief", fontSize = 24.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Spacer(Modifier.height(10.dp))
    IzSection(title = "Shift") {
        IzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (repo.clockedIn) "Clocked in at ${repo.currentBranch.name}" else "Off shift",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = IzColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                IzPill(if (repo.clockedIn) "on shift" else "off shift", if (repo.clockedIn) IzTone.GREEN else IzTone.GREY)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                if (!repo.clockedIn) {
                    IzChip("Clock in", onClick = { repo.setClock(true) }, primary = true)
                } else {
                    IzChip("Clock out", onClick = { repo.setClock(false) })
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    IzSection(title = "Branches", note = "pick where the day happens") {
        repo.branches.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("${b.kind} · ${b.dayDate}", fontSize = 11.sp, color = IzColors.Dim)
                }
                if (b.id == repo.branchId) IzPill("current", IzTone.GREEN)
                else IzChip("Switch", onClick = { repo.switchBranch(b.id) })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    IzSection(title = "Relief invites", note = "branch-initiated · accept writes the day grant") {
        if (repo.invites.none { it.state == "PENDING" }) Text("No pending invites.", fontSize = 12.sp, color = IzColors.Dim)
        repo.invites.forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${i.branch} · ${i.day}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("state ${i.state}", fontSize = 11.sp, color = IzColors.Dim)
                }
                if (i.state == "PENDING" || focusId == i.id) {
                    IzChip("Accept", onClick = { repo.acceptInvite(i) }, primary = true)
                    Spacer(Modifier.width(8.dp))
                    IzChip("Decline", onClick = { repo.declineInvite(i) })
                } else {
                    IzPill(i.state.lowercase(), if (i.state == "ACCEPTED") IzTone.GREEN else IzTone.GREY)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Relief duty starts view-only; a grant (or an accepted invite) unlocks edits until 04:00 Manila next day. Paid from the relief drawer.",
            fontSize = 11.sp,
            color = IzColors.Faint,
        )
    }
    Spacer(Modifier.height(12.dp))
    IzSection(title = "Relief requests", note = "outsider-initiated · broadcast to the branch") {
        repo.asks.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${q.branch} · ${q.day}${if (q.mine) " · mine" else ""}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("state ${q.state}", fontSize = 11.sp, color = IzColors.Dim)
                }
                if (q.state == "PENDING") {
                    if (!q.mine) {
                        IzChip("Grant", onClick = { repo.grantAsk(q) }, primary = true)
                        Spacer(Modifier.width(8.dp))
                        IzChip("Deny", onClick = { repo.denyAsk(q) })
                    } else {
                        IzChip("Withdraw", onClick = { repo.withdrawAsk(q) })
                    }
                } else {
                    IzPill(q.state.lowercase(), if (q.state == "GRANTED") IzTone.GREEN else IzTone.GREY)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextField(value = askBranch, onValueChange = { askBranch = it }, label = { Text("Branch to ask") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        IzChip("Send relief request", onClick = { repo.newAsk(askBranch) })
    }
    Spacer(Modifier.height(4.dp))
    Row {
        IzChip("Back to inbox", onClick = { onOpen(IzSel("inbox")) }, primary = true)
    }
}

@Composable
private fun IzSessions(repo: IzFakeRepo, onOpen: (IzSel) -> Unit) {
    var name by remember { mutableStateOf("") }
    Text("Sessions", fontSize = 24.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-ins refuse NO_SHOW and CANCELLED.", fontSize = 12.sp, color = IzColors.Dim)
    Spacer(Modifier.height(10.dp))
    repo.sessions.forEach { s ->
        IzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.id} · ${s.client} · ${s.service}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("${s.branch} · ${s.practitioner} · ${izPeso(s.amount)}", fontSize = 11.sp, color = IzColors.Dim)
                }
                IzPill(s.status.label, s.status.tone())
                if (s.walkIn) {
                    Spacer(Modifier.width(6.dp))
                    IzPill("walk-in", IzTone.BLUE)
                }
                if (s.voided) {
                    Spacer(Modifier.width(6.dp))
                    IzPill("voided", IzTone.RED)
                }
                Spacer(Modifier.width(8.dp))
                IzChip("Open", onClick = { onOpen(IzSel("session", s.id)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    IzSection(title = "Walk-in express", note = "registers a global client + PENDING session") {
        TextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        IzChip("Book walk-in now", onClick = {
            val c = repo.addWalkInClient(name)
            repo.bookSession(c.id, "Walk-in consult", true)
            name = ""
        }, primary = true)
    }
}

@Composable
private fun IzClients(repo: IzFakeRepo, onOpen: (IzSel) -> Unit) {
    Text("Clients · global", fontSize = 24.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text("One shared record across branches · at most one PENDING each · anonymize keeps gender + age.", fontSize = 12.sp, color = IzColors.Dim)
    Spacer(Modifier.height(10.dp))
    repo.clients.forEach { c ->
        val shown = if (c.anonymized) "Client ${c.id}" else c.name
        IzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$shown · ${c.gender} · ${c.age}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("${c.id} · ${repo.pendingCount(c.id)} pending", fontSize = 11.sp, color = IzColors.Dim)
                }
                if (c.anonymized) {
                    IzPill("anonymized", IzTone.GREY)
                    Spacer(Modifier.width(8.dp))
                }
                IzChip("Open", onClick = { onOpen(IzSel("client", c.id)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IzTeam(repo: IzFakeRepo, focusId: String?, onOpen: (IzSel) -> Unit) {
    Text("Team · users & roles", fontSize = 24.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text("MANAGER superset of Coordinator · Accountant read-only · ONBOARDING locked.", fontSize = 12.sp, color = IzColors.Dim)
    Spacer(Modifier.height(10.dp))
    repo.users.forEach { u ->
        IzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("home ${u.homeBranch} · ${if (u.clockedIn) "on shift" else "off shift"}", fontSize = 11.sp, color = IzColors.Dim)
                }
                IzPill(u.role.label, if (u.role == IzRole.ONBOARDING) IzTone.RED else IzTone.BLUE)
                if (u.role == IzRole.ONBOARDING || focusId == u.id) {
                    Spacer(Modifier.width(8.dp))
                    if (u.role == IzRole.ONBOARDING) IzChip("Grant Practitioner", onClick = { repo.grantPractitioner(u.id) }, primary = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(4.dp))
    IzChip("Back to inbox", onClick = { onOpen(IzSel("inbox")) })
}

@Composable
private fun IzNotices(repo: IzFakeRepo, focusId: String?) {
    val ordered = remember(repo.version) {
        val focused = repo.notices.firstOrNull { it.id == focusId }
        if (focused == null) repo.notices.toList() else listOf(focused) + repo.notices.filter { it.id != focusId }
    }
    IzSection(
        title = "Notifications mailbox",
        note = "${repo.notices.count { !it.read }} unread",
        actions = { IzChip("Mark all read", onClick = { repo.markAllRead() }, primary = true) },
    ) {
        ordered.forEach { n ->
            IzCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontSize = 13.sp, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold, color = IzColors.Ink)
                        Text(n.body, fontSize = 11.sp, color = IzColors.Dim)
                    }
                    Spacer(Modifier.width(8.dp))
                    IzPill(if (n.read) "read" else "unread", if (n.read) IzTone.GREY else IzTone.VIOLET)
                    Spacer(Modifier.width(8.dp))
                    IzChip(if (n.read) "Mark unread" else "Mark read", onClick = {
                        n.read = !n.read
                        repo.audit(if (n.read) "MARK_READ" else "MARK_UNREAD", n.id)
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IzAudit(repo: IzFakeRepo) {
    IzSection(title = "Audit log", note = "newest first · every triage action lands here") {
        repo.audits.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("#${a.seq} · ${a.stamp}", fontSize = 11.sp, color = IzColors.Faint, fontFamily = FontFamily.Monospace, modifier = Modifier.width(110.dp))
                Column(Modifier.weight(1f)) {
                    Text("${a.action} · ${a.target}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
                    Text("by ${a.who}${if (a.reason != null) " · ${a.reason}" else ""}", fontSize = 11.sp, color = IzColors.Dim)
                }
            }
            IzLine()
        }
    }
}

@Composable
private fun IzProfile(repo: IzFakeRepo) {
    val me = repo.actor ?: return
    Text("Profile", fontSize = 24.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
    Spacer(Modifier.height(10.dp))
    IzCard {
        IzKey("Name", me.name)
        IzKey("Role", me.role.label)
        IzKey("Home branch", me.homeBranch)
        IzKey("Working branch", repo.currentBranch.name)
        IzKey("Clock", if (repo.clockedIn) "on shift" else "off shift")
        Spacer(Modifier.height(8.dp))
        Row {
            if (!repo.clockedIn) {
                IzChip("Clock in", onClick = { repo.setClock(true) }, primary = true)
            } else {
                IzChip("Clock out", onClick = { repo.setClock(false) })
            }
            Spacer(Modifier.width(8.dp))
            IzChip("Log out", onClick = { repo.logout() })
        }
    }
}
