package com.companyb.companyapp.proto.client360

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #773 — client-360 prototype shell: login rail, branch select, dossier-first nav.

enum class C360Dest(val label: String) {
    RECORD("360 Record"),
    CLIENTS("Clients"),
    SESSIONS("Sessions"),
    HOME("Home & Clock"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoClient360App(onBack: () -> Unit, repo: Client360Repo = remember { Client360Repo() }) {
    C360Theme {
        val user = repo.currentUser
        when {
            user == null -> C360LoginGate(repo)
            user.locked -> C360LockedGate(repo)
            repo.currentBranchId.isBlank() -> C360BranchGate(repo)
            else -> C360Shell(repo, onBack)
        }
    }
}

@Composable
private fun C360LoginGate(repo: Client360Repo) {
    Box(Modifier.fillMaxSize().background(C360Colors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("THE CLIENT DOSSIER", color = C360Colors.BrassDeep, fontSize = 13.sp, fontWeight = FontWeight.Black)
            C360Headline("Client 360 — one record, every branch")
            C360Note("Pick a staffer to open the dossier room. Fake sign-in — no network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                C360Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        C360SealDot(if (u.locked) C360Colors.Slate else C360Colors.Teal)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = C360Colors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = C360Colors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        C360Primary("Sign in") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.log("${u.name} signed in (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun C360LockedGate(repo: Client360Repo) {
    Box(Modifier.fillMaxSize().background(C360Colors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            C360Headline("Locked out — ONBOARDING")
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    C360Body(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                            "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS.",
                    )
                    C360Note("Fake-data flow: the gate renders instead of any dossier content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C360Ghost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun C360BranchGate(repo: Client360Repo) {
    Box(Modifier.fillMaxSize().background(C360Colors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SIGNED IN AS ${repo.currentUser?.name?.uppercase()}", color = C360Colors.BrassDeep, fontSize = 12.sp, fontWeight = FontWeight.Black)
            C360Headline("Choose today's branch")
            repo.branches.forEach { b ->
                C360Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        C360SealDot(
                            when (b.dayStatus) {
                                C360DayStatus.OPEN -> C360Colors.Teal
                                C360DayStatus.PAST -> C360Colors.Brass
                                C360DayStatus.REMITTED -> C360Colors.Slate
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = C360Colors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · day ${b.dayStatus.name}", color = C360Colors.Faded, fontSize = 12.sp)
                        }
                        C360Primary("Open") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} opened branch ${b.name} (fake)")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C360Ghost("Switch user") {
                    repo.currentUser = null
                    repo.currentBranchId = ""
                }
            }
        }
    }
}

@Composable
private fun C360Shell(repo: Client360Repo, onBack: () -> Unit) {
    var dest by remember(repo.currentUser, repo.currentBranchId) { mutableStateOf(C360Dest.RECORD) }
    Row(Modifier.fillMaxSize().background(C360Colors.Paper)) {
        Column(
            Modifier.width(228.dp).fillMaxHeight().background(C360Colors.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("CLIENT 360", color = C360Colors.BrassDeep, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentUser?.name} · ${repo.currentUser?.role}",
                color = C360Colors.Faded,
                fontSize = 12.sp,
            )
            Text(
                if (repo.clockedIn) "● clocked in" else "○ clocked out",
                color = if (repo.clockedIn) C360Colors.Teal else C360Colors.Faded,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            C360Dest.entries.forEach { d ->
                val active = d == dest
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (active) C360Colors.BrassDeep else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { dest = d }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.label + if (d == C360Dest.MAILBOX && repo.notices.any { !it.read }) " (${repo.notices.count { !it.read }})" else "",
                            color = if (active) Color.White else C360Colors.Ink,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            C360Ghost("Exit prototype") { onBack() }
        }
        Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            C360DayBanner(repo.currentBranch().dayStatus, repo.currentBranch().name)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (dest) {
                    C360Dest.RECORD -> C360RecordPane(repo) { dest = C360Dest.SESSIONS }
                    C360Dest.CLIENTS -> C360ClientsPane(repo) { dest = C360Dest.RECORD }
                    C360Dest.SESSIONS -> C360SessionsPane(repo)
                    C360Dest.HOME -> C360HomePane(repo)
                    C360Dest.FINANCE -> C360FinancePane(repo)
                    C360Dest.TEAM -> C360TeamPane(repo)
                    C360Dest.MAILBOX -> C360MailboxPane(repo)
                    C360Dest.AUDIT -> C360AuditPane(repo)
                    C360Dest.PROFILE -> C360ProfilePane(repo) {
                        repo.log("${repo.currentUser?.name} signed out (fake)")
                        repo.currentUser = null
                        repo.currentBranchId = ""
                        repo.clockedIn = false
                    }
                }
            }
        }
    }
}
