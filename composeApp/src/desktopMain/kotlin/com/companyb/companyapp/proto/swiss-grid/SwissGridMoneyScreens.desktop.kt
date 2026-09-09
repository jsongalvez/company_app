package com.companyb.companyapp.proto.swissgrid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

// #793 — swiss-grid money and system screens: finance, team, mailbox, audit, profile.

@Composable
fun SgFinance(repo: SgFakeRepo) {
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    SgPage(index = "06", title = "Finance.", kicker = "MONEY") {
        SgSplit(
            left = {
                SgLabel("Remittance · SESSION + PRODUCT · drafts overlap freely")
                Box(Modifier.height(6.dp))
                repo.drafts.forEach { d ->
                    SgSheet {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SgLabel("${d.kind.name} · ${d.id}")
                            SgLabel(if (d.submitted) "SUBMITTED" else "DRAFT", red = !d.submitted)
                        }
                        Text(text = d.label, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SgInk)
                        SgQuiet("₱${d.amount}${if (d.kind == SgDraftKind.PRODUCT) " · qty ${d.qty} (price × qty)" else " · net after compensation + expenses"}")
                        if (d.snapshot.isNotEmpty()) SgQuiet("Snapshot: ${d.snapshot} · immutable.")
                        if (d.undone) SgQuiet("Undone <48h: ${d.undoReason}")
                        Box(Modifier.height(4.dp))
                        if (!d.submitted) {
                            SgPrimary("Submit · freeze snapshot", onClick = { repo.submitDraft(d.id) })
                        } else if (undoTarget == d.id) {
                            SgField(value = undoReason, onChange = { undoReason = it }, label = "Undo reason (required)")
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(text = "CONFIRM UNDO", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SgRed, modifier = Modifier.clickable {
                                    repo.undoDraft(d.id, undoReason)
                                    undoTarget = null
                                    undoReason = ""
                                }.padding(6.dp))
                                Text(text = "CANCEL", fontFamily = SgSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = SgGrey, modifier = Modifier.clickable { undoTarget = null; undoReason = "" }.padding(6.dp))
                            }
                        } else {
                            SgGhost(label = "Undo within 48h", onClick = { undoTarget = d.id })
                        }
                    }
                }
            },
            right = {
                SgSection("6.1", "Snapshot")
                SgBody("Submission freezes an immutable P&L snapshot. Later edits never rewrite it.")
                SgSection("6.2", "Undo")
                SgQuiet("Undo returns to draft, unlocks days, deletes the snapshot. Needs a reason, logged to audit. After 48h the snapshot is permanent.")
                SgSection("6.3", "Commission split")
                SgQuiet("Product commissions pool per branch day and split equally among practitioners + coordinators clocked in at sold_at. Manual include/exclude can override. Separate from compensation, outside remittance.")
            },
        )
    }
}

@Composable
fun SgTeam(repo: SgFakeRepo) {
    SgPage(index = "07", title = "Team.", kicker = "ROLES") {
        SgSplit(
            left = {
                SgLabel("Users · roles are capability bundles")
                Box(Modifier.height(6.dp))
                repo.mates.forEachIndexed { i, m ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "0${i + 1}", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SgRed)
                                Text(text = m.name, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SgInk)
                            }
                            SgLabel(m.role, red = m.locked)
                        }
                        SgQuiet(m.capability)
                    }
                    SgHairRule()
                }
            },
            right = {
                SgSection("7.1", "Capabilities")
                SgRow("Practitioner", "sessions + clients")
                SgRow("Coordinator", "finance + remittance")
                SgRow("MANAGER", "users + delegates")
                SgRow("Accountant", "read-only, all branches")
                SgRow("ONBOARDING", "empty bundle · locked")
                SgSection("7.2", "Note")
                SgQuiet("Runtime checks read capabilities, never role names. Relief and delegate remain direct grants.")
            },
        )
    }
}

@Composable
fun SgMailbox(repo: SgFakeRepo) {
    SgPage(index = "08", title = "Mailbox.", kicker = "SIGNALS") {
        SgSplit(
            left = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SgLabel("${repo.notices.count { !it.read }} unread · read rows kept forever")
                    Text(text = "MARK ALL READ", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = SgInk, modifier = Modifier.clickable { repo.markAllRead() }.padding(6.dp))
                }
                Box(Modifier.height(6.dp))
                repo.notices.forEach { n ->
                    Column(
                        Modifier.fillMaxWidth().clickable { repo.toggleNotice(n.id) }.padding(vertical = 10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(vertical = 2.dp)) {
                                Text(text = if (n.read) "○" else "●", fontSize = 12.sp, color = if (n.read) SgGrey else SgRed)
                            }
                            SgLabel("${n.branch} · ${n.day}")
                        }
                        Text(text = n.title, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SgInk)
                        SgQuiet(n.body + " Tap toggles read.")
                    }
                    SgHairRule()
                }
            },
            right = {
                SgSection("8.1", "Contract")
                SgQuiet("Relief events name the branch and the day, ride the same transaction as the change, and tap through to that branch day. Reminders point at one session.")
            },
        )
    }
}

@Composable
fun SgAuditList(repo: SgFakeRepo) {
    SgPage(index = "09", title = "Audit.", kicker = "TRACE") {
        SgLabel("Immutable · who changed what, when, why")
        Box(Modifier.height(6.dp))
        repo.audits.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SgLabel("${a.time} · ${a.actor}")
                    SgLabel(a.action, red = a.action == "VOID" || a.action == "UNDO")
                }
                SgBody(a.detail)
            }
            SgHairRule()
        }
    }
}

@Composable
fun SgProfile(repo: SgFakeRepo, onLogout: () -> Unit, onExit: () -> Unit) {
    var dayPick by remember { mutableStateOf(repo.currentBranch().day) }
    SgPage(index = "10", title = "Profile.", kicker = "SELF") {
        SgSplit(
            left = {
                SgNumeral("YOU", red = false)
                Box(Modifier.height(6.dp))
                SgBody(repo.userName + " · Practitioner")
                SgQuiet(repo.email.ifBlank { "practitioner@company.app" })
                Box(Modifier.height(8.dp))
                SgRow("Clock", if (repo.clockedIn) "IN" else "OUT")
                SgRow("Branch", repo.currentBranch().name)
                SgRow("Branch day", repo.currentBranch().day.name)
                Box(Modifier.height(8.dp))
                if (repo.clockedIn) SgPrimary("Clock out", onClick = { repo.clock(false) }) else SgPrimary("Clock in", red = true, onClick = { repo.clock(true) })
                SgPrimary("Log out", onClick = { repo.logout(); onLogout() })
                SgGhost(label = "Exit prototype", onClick = onExit)
            },
            right = {
                SgSection("10.1", "Move the day")
                SgQuiet("Demo control for the OPEN / PAST / REMITTED banner.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SgDay.entries.forEach { d ->
                        Column(
                            Modifier.weight(1f).clickable {
                                dayPick = d
                                repo.moveDay(d)
                            }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = d.name,
                                fontFamily = SgSans,
                                fontWeight = if (dayPick == d) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                color = if (dayPick == d) SgRed else SgGrey,
                            )
                        }
                    }
                }
                SgSection("10.2", "Reset")
                SgQuiet("Restore fake seed. Logs a RESET audit row.")
                SgGhost(label = "Reset demo data", onClick = { repo.reset() })
                SgSection("10.3", "Boundary")
                SgQuiet("04:00 Asia/Manila. OPEN stays editable until then; PAST and REMITTED need coordinator hands.")
            },
        )
    }
}
