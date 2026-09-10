package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
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
import com.companyb.companyapp.util.logInfo

// #819 — owner-desk shell: sign-in gate, ONBOARDING lock, branch gate, desk with
// morning-brief strip (money, people, risk) plus drawer nav.

enum class OdDest(val label: String) {
    DESK("Desk"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    PEOPLE("People"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoOwnerDeskApp(onBack: () -> Unit, repo: OwnerDeskRepo = remember { OwnerDeskRepo() }) {
    Box(Modifier.fillMaxSize().background(OdColors.Mahogany)) {
        val user = repo.currentUser
        when {
            user == null -> OdLoginGate(repo)
            user.locked -> OdLockedGate(repo)
            repo.currentBranchId.isBlank() -> OdBranchGate(repo)
            else -> OdShell(repo, onBack)
        }
    }
}

@Composable
private fun OdLoginGate(repo: OwnerDeskRepo) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OdMasthead("THE OWNER'S PRIVATE DESK", "Fake sign-in — no network, no backend.")
            OdHeadline("The morning brief awaits")
            OdNote("Single-owner view: money, people, risk. Staff particulars stay sealed.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                OdLedgerCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            OdLedgerTitle(u.name + if (u.locked) " — ONBOARDING, key withheld" else "")
                            OdLedgerFaint("${u.role} · home ${repo.branch(u.homeBranchId).name}")
                        }
                        Spacer(Modifier.width(10.dp))
                        OdLedgerButton("Take the seat") {
                            logInfo("owner-desk", "sign-in as ${u.id}")
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.clockedIn = false
                            repo.log("${u.name} took the owner's seat (fake sign-in)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OdLockedGate(repo: OwnerDeskRepo) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OdMasthead("KEY WITHHELD", "ONBOARDING is locked.")
            OdHeadline("The desk is locked")
            OdLedgerCard {
                OdLedgerTitle("Empty capability bundle")
                OdLedgerText("This seat holds no capabilities yet — no money, no branches, no sessions.")
                OdLedgerFaint("Ask the Owner to grant a role. Nothing here calls a network.")
            }
            OdGhostButton("Step away") {
                repo.currentUser = null
                repo.log("ONBOARDING seat released")
            }
        }
    }
}

@Composable
private fun OdBranchGate(repo: OwnerDeskRepo) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OdMasthead("CHOOSE A LEDGER", "Which branch book opens today?")
            OdHeadline("Open a branch book")
            repo.branches.forEach { b ->
                OdLedgerCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            OdLedgerTitle("${b.name} · ${b.kind}")
                            OdLedgerFaint("Day ${b.dayStatus.name} · takings ₱${b.takings}")
                        }
                        Spacer(Modifier.width(10.dp))
                        OdLedgerButton("Open") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} opened the ${b.name} book")
                        }
                    }
                }
            }
            OdGhostButton("Change seat") { repo.currentUser = null }
        }
    }
}

@Composable
private fun OdShell(repo: OwnerDeskRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(OdDest.DESK) }
    val branch = repo.currentBranch()
    val user = repo.currentUser!!
    Column(Modifier.fillMaxSize()) {
        OdNameplate(repo, branch, user, onBack)
        Row(Modifier.fillMaxSize().weight(1f)) {
            Column(
                Modifier.width(220.dp).fillMaxHeight()
                    .background(OdColors.DeskEdge)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OdDest.entries.forEach { d ->
                    val badge = if (d == OdDest.MAILBOX && repo.unreadCount() > 0) "${repo.unreadCount()}" else ""
                    OdNavItem(d.label, dest == d, badge) { dest = d }
                }
                Spacer(Modifier.height(8.dp))
                OdRule()
                Spacer(Modifier.height(8.dp))
                Text("SEALED", color = OdColors.Brass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Staff particulars hidden by design. Counts only.", color = OdColors.Faded, fontSize = 11.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                when (dest) {
                    OdDest.DESK -> OdDeskHome(repo)
                    OdDest.SESSIONS -> OdSessionsScreen(repo)
                    OdDest.CLIENTS -> OdClientsScreen(repo)
                    OdDest.FINANCE -> OdFinanceScreen(repo)
                    OdDest.PEOPLE -> OdPeopleScreen(repo)
                    OdDest.MAILBOX -> OdMailboxScreen(repo)
                    OdDest.AUDIT -> OdAuditScreen(repo)
                    OdDest.PROFILE -> OdProfileScreen(repo, onBack)
                }
            }
        }
    }
}

@Composable
private fun OdNameplate(repo: OwnerDeskRepo, branch: OdBranch, user: OdUser, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(OdColors.DeskEdge)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "THE OWNER'S DESK — ${branch.name.uppercase()}",
                    color = OdColors.Brass,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OdSerif,
                )
                Text(
                    "Day ${branch.dayStatus.name} · boundary 04:00 Asia/Manila · seated: ${user.name} (${user.role})",
                    color = OdColors.Faded,
                    fontSize = 12.sp,
                )
            }
            val dayTag = when (branch.dayStatus) {
                OdDayStatus.OPEN -> "OPEN"
                OdDayStatus.PAST -> "PAST"
                OdDayStatus.REMITTED -> "REMITTED"
            }
            OdGhostButton("Books") { repo.currentBranchId = "" }
            Spacer(Modifier.width(8.dp))
            OdGhostButton("Exit desk") { onBack() }
            Spacer(Modifier.width(8.dp))
            Text(
                dayTag,
                color = OdColors.Mahogany,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.background(OdColors.Brass).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        if (branch.dayStatus != OdDayStatus.OPEN) {
            Text(
                "Branch-day banner: ${branch.dayStatus.name} — books past the 04:00 Manila boundary read-only until remitted.",
                color = OdColors.Brass,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun OdDeskHome(repo: OwnerDeskRepo) {
    val branch = repo.currentBranch()
    val daySessions = repo.branchSessions(branch.id)
    val done = daySessions.count { it.status == OdSessionStatus.COMPLETED }
    val pending = daySessions.count { it.status == OdSessionStatus.PENDING }
    val headcount = repo.users.count { !it.locked }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OdHeadline("Morning brief")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { OdMoneyCell("Takings · ${branch.name}", "₱${branch.takings}") }
            Box(Modifier.weight(1f)) {
                OdMoneyCell("Sessions done / open", "$done / $pending")
            }
            Box(Modifier.weight(1f)) { OdMoneyCell("People on books", "$headcount") }
        }
        Text("RISK — needs the owner's eye", color = OdColors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val risks = repo.riskFlags()
        if (risks.isEmpty()) {
            OdNote("No open risks. The desk is quiet.")
        } else {
            risks.forEach { OdRiskCell(it) }
        }
        OdLedgerCard {
            OdLedgerTitle(if (repo.clockedIn) "Clocked in at ${branch.name}" else "Not yet clocked in")
            OdLedgerFaint("Home book: ${repo.branch(repo.currentUser!!.homeBranchId).name}. Relief covers below never move money.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    OdLedgerButton("Clock out") {
                        repo.clockedIn = false
                        repo.log("${repo.currentUser?.name} clocked out at ${branch.name}")
                    }
                } else {
                    OdLedgerButton("Clock in") {
                        repo.clockedIn = true
                        repo.log("${repo.currentUser?.name} clocked in at ${branch.name}")
                    }
                }
            }
        }
        OdLedgerCard {
            OdLedgerTitle("Relief — duty, invites, requests")
            OdLedgerFaint("One live request per date; invites accept/decline; duty covers expire at 04:00 Manila.")
            repo.relief.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OdLedgerText("${r.kind}: ${r.detail}")
                        OdLedgerFaint(r.state)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (r.kind == "Invite" && r.state.startsWith("Pending")) {
                        OdLedgerButton("Accept") {
                            r.state = "Accepted — cover Saturday"
                            repo.log("Relief invite ${r.id} accepted")
                        }
                        Spacer(Modifier.width(6.dp))
                        OdLedgerGhost("Decline") {
                            r.state = "Declined"
                            repo.log("Relief invite ${r.id} declined")
                        }
                    }
                }
            }
        }
    }
}
