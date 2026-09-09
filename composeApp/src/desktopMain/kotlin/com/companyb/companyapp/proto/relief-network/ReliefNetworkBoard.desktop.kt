package com.companyb.companyapp.proto.reliefnetwork

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.companyb.companyapp.util.logInfo

@Composable
fun RnLogin(
    repo: RnRepo,
    onPick: (RnUser) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RnBoard)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RELIEF NETWORK", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = RnVermilion)
        Text(
            "The night-market board for spare hands. Stalls shout, neighbors claim, one tap grants the day.",
            style = MaterialTheme.typography.bodyMedium,
            color = RnCream,
        )
        RnSectionTitle("Pick your stall pass")
        repo.users.forEach { user ->
            RnSlip {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Black, color = RnInk)
                        Text(
                            "${user.login} · ${user.role} · home ${repo.branchName(user.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = RnInkSoft,
                        )
                    }
                    if (user.locked) RnStamp("ONBOARDING", RnMarigold, dark = true)
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            logInfo("ReliefNetworkLogin", "pass picked ${user.login}")
                            onPick(user)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RnVermilion),
                    ) { Text("Enter") }
                }
            }
        }
    }
}

@Composable
fun RnLockedOut(
    user: RnUser,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(RnBoard).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RnStamp("STALL CLOSED", RnMarigold)
        Text("No pass yet, ${user.name}.", style = MaterialTheme.typography.headlineSmall, color = RnCream)
        Text(
            "ONBOARDING carries an empty capability bundle — locked out even with a branch assignment, until MANAGE_USERS grants a real role. Ask a MANAGER at the Team stall.",
            style = MaterialTheme.typography.bodyMedium,
            color = RnCream,
        )
        OutlinedButton(onClick = onBack) { Text("Back to passes", color = RnCream) }
    }
}

@Composable
fun RnBranchSelect(
    repo: RnRepo,
    user: RnUser,
    onPick: (RnBranch) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RnBoard)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Where to, ${user.name}?", style = MaterialTheme.typography.headlineSmall, color = RnCream)
        Text("Home stalls clock straight in. Other stalls start view-only — claim a grant on the board.", style = MaterialTheme.typography.bodyMedium, color = RnCream)
        repo.branches.forEach { branch ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RnSurface)
                        .clickable { onPick(branch) }
                        .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(branch.name, fontWeight = FontWeight.Black, color = RnCream)
                        Text(branch.kind, style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                    }
                    RnStamp(branch.dayState, rnDayColor(branch.dayState))
                }
            }
        }
        TextButton(onClick = onBack) { Text("Swap pass", color = RnMarigold) }
    }
}

@Composable
fun RnDayBanner(branch: RnBranch) {
    val note =
        when (branch.dayState) {
            "OPEN" -> "Editable by all on-duty hands until 04:00 Asia/Manila."
            "PAST" -> "Rolled past 04:00 Asia/Manila — Coordinator edits only."
            else -> "Covered by a submitted remittance — Coordinator edits only, flagged in audit."
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RnSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RnStamp(branch.dayState, rnDayColor(branch.dayState))
        Text(note, style = MaterialTheme.typography.bodySmall, color = RnCream, modifier = Modifier.weight(1f))
        Text("04:00 Manila boundary", style = MaterialTheme.typography.labelSmall, color = RnInkSoft)
    }
}

@Composable
fun RnClockStrip(
    repo: RnRepo,
    user: RnUser,
    branch: RnBranch,
) {
    val home = branch.id == user.homeBranchId
    val clocked = repo.clocked[branch.id] == true
    val grant = repo.grants.any { it.holder == user.login && it.branchId == branch.id }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RnSurface)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (clocked) "Clocked in · ${branch.name}" else "Off the floor · ${branch.name}",
                fontWeight = FontWeight.Bold,
                color = RnCream,
            )
            Text(
                when {
                    home -> "Home stall — full hands from clock-in."
                    grant -> "Relief duty granted — edit hands today, paid from this drawer."
                    else -> "Relief duty view-only until a grant lands. Expires 04:00 Manila next day."
                },
                style = MaterialTheme.typography.bodySmall,
                color = RnCream,
            )
        }
        if (clocked) {
            OutlinedButton(onClick = {
                repo.clocked[branch.id] = false
                logInfo("ReliefNetworkClock", "${user.login} clocked out ${branch.id}")
            }) { Text("Clock out", color = RnCream) }
        } else {
            Button(
                onClick = {
                    repo.clocked[branch.id] = true
                    logInfo("ReliefNetworkClock", "${user.login} clocked in ${branch.id}")
                },
                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
            ) { Text(if (home) "Clock in" else "Clock in (relief)") }
        }
    }
}

@Composable
fun RnBoard(
    repo: RnRepo,
    user: RnUser,
    branch: RnBranch,
) {
    var shoutDate by remember { mutableStateOf("Sat Sep 12") }
    var invitee by remember { mutableStateOf("ana") }
    var inviteDate by remember { mutableStateOf("Sun Sep 13") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RnSectionTitle("Relief board · ${branch.name}")
        RnBoardColumn(
            title = "REQUESTS — outsiders shout",
            hint = "Broadcast names no individual. One live request per requester per branch per date.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shoutDate,
                    onValueChange = { shoutDate = it },
                    label = { Text("Day") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { repo.broadcastRequest(user.login, branch.id, shoutDate) },
                    colors = ButtonDefaults.buttonColors(containerColor = RnVermilion),
                ) { Text("Shout") }
            }
            repo.requests.filter { it.branchId == branch.id }.forEach { req ->
                RnSlip {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${repo.userName(req.requester)} → ${req.date}", fontWeight = FontWeight.Black, color = RnInk)
                            Text("one-live-per-date · locks at clock-in", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                        }
                        RnStamp(req.state, if (req.state == "LIVE") RnMarigold else RnTeal, dark = req.state != "LIVE")
                    }
                    if (req.state == "LIVE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { repo.grantRequest(req.id, user.login) },
                                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
                            ) { Text("Grant · one tap") }
                            OutlinedButton(onClick = { repo.denyRequest(req.id, user.login) }) { Text("Deny", color = RnInk) }
                            if (req.requester == user.login) {
                                TextButton(onClick = { repo.withdrawRequest(req.id) }) { Text("Withdraw", color = RnInkSoft) }
                            }
                        }
                    }
                }
            }
        }
        RnBoardColumn(
            title = "INVITES — branch calls out",
            hint = "Any branch member invites any active user for one future day. Revoke frees them again.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = invitee, onValueChange = { invitee = it }, label = { Text("Login") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = inviteDate, onValueChange = { inviteDate = it }, label = { Text("Day") }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { repo.sendInvite(invitee, branch.id, inviteDate, user.login) },
                    colors = ButtonDefaults.buttonColors(containerColor = RnVermilion),
                ) { Text("Invite") }
            }
            repo.invites.filter { it.branchId == branch.id }.forEach { inv ->
                RnSlip {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${repo.userName(inv.invitee)} · ${inv.date}", fontWeight = FontWeight.Black, color = RnInk)
                            Text("branch-initiated · single future day", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                        }
                        RnStamp(inv.state, if (inv.state == "PENDING") RnMarigold else RnTeal, dark = inv.state != "PENDING")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (inv.invitee == user.login && inv.state == "PENDING") {
                            Button(
                                onClick = { repo.acceptInvite(inv.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
                            ) { Text("Accept") }
                            OutlinedButton(onClick = { repo.declineInvite(inv.id) }) { Text("Decline", color = RnInk) }
                        }
                        if (inv.state == "ACCEPTED" || inv.state == "PENDING") {
                            TextButton(onClick = { repo.revokeInvite(inv.id) }) { Text("Revoke", color = RnDanger) }
                        }
                    }
                }
            }
        }
        RnBoardColumn(
            title = "GRANTS — who holds the day",
            hint = "Multiple relief workers may hold edit access at one branch on the same day.",
        ) {
            val day = repo.grants.filter { it.branchId == branch.id }
            if (day.isEmpty()) {
                Text("No grants pinned yet — grant a shout above.", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
            }
            day.forEach { grant ->
                RnSlip {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${repo.userName(grant.holder)} · ${grant.date}", fontWeight = FontWeight.Black, color = RnInk)
                            Text("via ${grant.source} · comp from this drawer", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                        }
                        RnStamp("GRANT", RnTeal, dark = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RnBoardColumn(
    title: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RnSurface)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Black, color = RnMarigold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = RnCream)
        content()
    }
}

@Composable
fun RnRailButton(
    label: String,
    badge: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) RnVermilion else RnSurface
    val fg = if (selected) RnBoard else RnCream
    Box(
        modifier =
            Modifier
                .width(148.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.weight(1f))
            if (badge > 0) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) RnBoard else RnMarigold).padding(horizontal = 6.dp, vertical = 2.dp),
                ) { Text("$badge", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (selected) RnCream else RnBoard) }
            }
        }
    }
}

@Composable
fun RnShellChrome(
    repo: RnRepo,
    user: RnUser,
    branch: RnBranch,
    rail: String,
    onRail: (String) -> Unit,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val unread = repo.notices.count { !it.read }
    Column(modifier = Modifier.fillMaxSize().background(RnBoard)) {
        RnTickerTape(repo.ticker())
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("RN", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, color = RnVermilion)
                Text("${user.name}\n${user.role}", style = MaterialTheme.typography.bodySmall, color = RnCream)
                Spacer(Modifier.height(4.dp))
                RnRailButton("Board", repo.requests.count { it.branchId == branch.id && it.state == "LIVE" }, rail == "board") { onRail("board") }
                RnRailButton("Sessions", repo.sessions.count { it.branchId == branch.id && it.status == "PENDING" }, rail == "sessions") { onRail("sessions") }
                RnRailButton("Clients", 0, rail == "clients") { onRail("clients") }
                RnRailButton("Finance", 0, rail == "finance") { onRail("finance") }
                RnRailButton("Team", 0, rail == "team") { onRail("team") }
                RnRailButton("Mail", unread, rail == "mail") { onRail("mail") }
                RnRailButton("Audit", 0, rail == "audit") { onRail("audit") }
                RnRailButton("Profile", 0, rail == "profile") { onRail("profile") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBranchChange) { Text("Swap branch", color = RnMarigold) }
                TextButton(onClick = onLogout) { Text("Log out", color = RnInkSoft) }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RnDayBanner(branch)
                RnClockStrip(repo, user, branch)
                content()
            }
        }
    }
}
