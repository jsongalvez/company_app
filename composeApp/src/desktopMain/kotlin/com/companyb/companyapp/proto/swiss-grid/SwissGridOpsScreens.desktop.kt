package com.companyb.companyapp.proto.swissgrid

import androidx.compose.foundation.background
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

// #793 — swiss-grid operations screens: onboarding, login, branches, home, sessions, clients.

@Composable
fun SgOnboarding(onContinue: () -> Unit, onPreviewLogin: () -> Unit) {
    SgPage(index = "00", title = "New here. Read first.", kicker = "ONBOARDING") {
        SgSplit(
            left = {
                SgNumeral("00", red = true)
                Box(Modifier.height(8.dp))
                SgBody("A freshly registered profile is functionally locked. The role bundle is empty, so nothing derives — even after a branch assignment.")
                Box(Modifier.height(10.dp))
                SgRow("Sessions", "observe only")
                SgRow("Clients", "observe only")
                SgRow("Finance", "locked")
                SgRow("Branch day", "locked")
                Box(Modifier.height(10.dp))
                SgPrimary("Continue to login", onClick = onContinue)
                SgGhost("Preview login directly", onClick = onPreviewLogin)
            },
            right = {
                SgSection("A", "The rule")
                SgBody("ONBOARDING holds zero capabilities until MANAGE_USERS grants a real role.")
                SgSection("B", "What happens next")
                SgQuiet("01 — Coordinator verifies orientation.\n02 — MANAGER assigns Practitioner.\n03 — Grid unlocks at clock-in.")
            },
        )
    }
}

@Composable
fun SgLogin(repo: SgFakeRepo, onEnter: () -> Unit, onBackOnboarding: () -> Unit) {
    var address by remember { mutableStateOf(repo.email) }
    SgPage(index = "01", title = "Login.", kicker = "ENTRY", onBack = onBackOnboarding) {
        SgSplit(
            left = {
                SgNumeral("01", red = true)
                Box(Modifier.height(8.dp))
                SgBody("Any work email opens the fake door. Nothing is sent anywhere.")
                Box(Modifier.height(8.dp))
                SgField(value = address, onChange = { address = it }, label = "Work email")
                SgPrimary("Enter the grid", red = true, onClick = { repo.login(address); onEnter() })
                SgQuiet("Fake auth — offline, local state only.")
            },
            right = {
                SgSection("C", "Convention")
                SgRow("Type", "Helvetica-voice sans")
                SgRow("Accent", "Red used once per screen")
                SgRow("Rules", "Black 3dp, hairlines 1dp")
                SgRow("Grid", "12 columns, whitespace first")
            },
        )
    }
}

@Composable
fun SgBranches(repo: SgFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    SgPage(index = "02", title = "Pick a branch.", kicker = "PLACE", onBack = onBack) {
        SgLabel("Three branch kinds · one operational day each")
        Box(Modifier.height(8.dp))
        repo.branches.forEachIndexed { i, b ->
            Column(
                Modifier.fillMaxWidth().clickable {
                    repo.branchId = b.id
                    onPick()
                }.padding(vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "0${i + 1}", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SgRed)
                        Text(text = b.name, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SgInk)
                    }
                    SgLabel(b.day.name)
                }
                Box(Modifier.height(4.dp))
                SgQuiet("${b.kind} · ${b.place} · tap to enter")
            }
            SgHairRule()
        }
        Box(Modifier.height(10.dp))
        SgQuiet("Branch is a CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION. Each holds its own inventory, sessions, and records.")
    }
}

@Composable
fun SgHome(repo: SgFakeRepo, onOpenSessions: () -> Unit) {
    val branch = repo.currentBranch()
    SgPage(
        index = "03",
        title = branch.name + ".",
        kicker = "HOME",
        topSlot = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    SgLabel("Pending here")
                    SgNumeral(repo.pendingCount().toString().padStart(2, '0'), red = true)
                }
                Column(horizontalAlignment = Alignment.End) {
                    SgLabel("Completed total")
                    Text(text = "₱${repo.completedTotal()}", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = SgInk)
                }
                Column(horizontalAlignment = Alignment.End) {
                    SgLabel("Clock")
                    Text(
                        text = if (repo.clockedIn) "IN" else "OUT",
                        fontFamily = SgSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = if (repo.clockedIn) SgInk else SgGrey,
                    )
                }
            }
        },
    ) {
        SgSplit(
            left = {
                SgSection("3.1", "Arrival")
                if (repo.clockedIn) {
                    SgBody("Clocked in. The day has you.")
                    SgPrimary("Clock out", onClick = { repo.clock(false) })
                } else {
                    SgBody("Not clocked in yet. The grid waits.")
                    SgPrimary("Clock in", red = true, onClick = { repo.clock(true) })
                }
                SgSection("3.2", "Relief duty", "Non-home clock-in starts view-only; a grant adds edit.")
                repo.relief.forEach { r ->
                    SgSheet {
                        SgLabel("${r.kind.name} · ${r.branch} · ${r.day}")
                        Box(Modifier.height(4.dp))
                        SgBody(r.note)
                        if (r.answered.isEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(text = "ACCEPT", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = SgRed, modifier = Modifier.clickable { repo.answerRelief(r.id, "Accepted") }.padding(6.dp))
                                Text(text = "DECLINE", fontFamily = SgSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp, color = SgGrey, modifier = Modifier.clickable { repo.answerRelief(r.id, "Declined") }.padding(6.dp))
                            }
                        } else {
                            SgQuiet("Marked: ${r.answered}.")
                        }
                    }
                }
                var askBranch by remember { mutableStateOf("") }
                var askNote by remember { mutableStateOf("") }
                SgSheet {
                    SgLabel("Ask for relief")
                    SgField(value = askBranch, onChange = { askBranch = it }, label = "Branch (blank = here)")
                    SgField(value = askNote, onChange = { askNote = it }, label = "What do you need?")
                    SgGhost(
                        label = "Broadcast request",
                        onClick = {
                            repo.askRelief(askBranch.trim(), askNote.trim())
                            askBranch = ""
                            askNote = ""
                        },
                    )
                    SgQuiet("One live request per requester per branch per date. Any active branch member grants, denies, or cancels.")
                }
            },
            right = {
                SgSection("3.3", "Today")
                repo.branchSessions().take(4).forEach { s ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SgLabel("${s.time} · ${s.kind.name}")
                            SgLabel(s.status.name, red = s.status == SgStatus.PENDING)
                        }
                        Text(text = s.client, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SgInk)
                        SgQuiet("${s.service} · ₱${s.amount} · ${s.practitioner}${if (s.voided) " · VOIDED" else ""}")
                    }
                    SgHairRule()
                }
                SgGhost(label = "Open all sessions", onClick = onOpenSessions)
                SgSection("3.4", "Boundary")
                SgQuiet("Branch day stays editable until 04:00 Asia/Manila next morning, then turns PAST lazily.")
            },
        )
    }
}

@Composable
fun SgSessions(repo: SgFakeRepo) {
    var filter by remember { mutableStateOf<SgStatus?>(null) }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkInName by remember { mutableStateOf("") }
    SgPage(index = "04", title = "Sessions.", kicker = "WORK") {
        Row(Modifier.fillMaxWidth().background(SgWash).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SgFilterTab("All", filter == null) { filter = null }
            SgStatus.entries.forEach { st ->
                SgFilterTab(st.name, filter == st) { filter = if (filter == st) null else st }
            }
        }
        Box(Modifier.height(6.dp))
        SgQuiet("Walk-in sessions cannot be marked NO_SHOW or CANCELLED. One visit, one client, one branch.")
        val list = repo.branchSessions().filter { filter == null || it.status == filter }
        list.forEach { s ->
            SgSheet {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SgLabel("${s.time} · ${s.kind.name} · ${s.id}")
                    SgLabel(if (s.voided) "VOIDED" else s.status.name, red = s.status == SgStatus.PENDING || s.voided)
                }
                Text(text = s.client, fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SgInk)
                SgQuiet("${s.service} · ₱${s.amount} · ${s.practitioner}")
                if (s.voided) SgQuiet("Void reason: ${s.voidReason}")
                Box(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SgStatus.entries.forEach { st ->
                        val blocked = s.kind == SgKind.WALK_IN && (st == SgStatus.NO_SHOW || st == SgStatus.CANCELLED)
                        if (!blocked) {
                            Text(
                                text = st.name,
                                fontFamily = SgSans,
                                fontWeight = if (s.status == st) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                color = if (s.status == st) SgRed else SgGrey,
                                modifier = Modifier.clickable { repo.setStatus(s.id, st) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                if (s.kind == SgKind.WALK_IN) SgQuiet("Walk-in rule: NO_SHOW / CANCELLED not offered.")
                if (voidTarget == s.id) {
                    SgField(value = voidReason, onChange = { voidReason = it }, label = if (s.voided) "Unvoid reason" else "Void reason")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "CONFIRM", fontFamily = SgSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SgRed, modifier = Modifier.clickable {
                            if (s.voided) repo.unvoidSession(s.id, voidReason) else repo.voidSession(s.id, voidReason)
                            voidTarget = null
                            voidReason = ""
                        }.padding(6.dp))
                        Text(text = "CANCEL", fontFamily = SgSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = SgGrey, modifier = Modifier.clickable { voidTarget = null; voidReason = "" }.padding(6.dp))
                    }
                } else {
                    SgGhost(label = if (s.voided) "Unvoid with reason" else "Void with reason", onClick = { voidTarget = s.id })
                }
            }
        }
        if (list.isEmpty()) SgQuiet("No sessions under this filter.")
        SgSection("4.9", "Walk-in")
        SgField(value = walkInName, onChange = { walkInName = it }, label = "Walk-in name (blank = auto)")
        SgPrimary("Add walk-in session", onClick = { repo.addWalkIn(walkInName.trim()); walkInName = "" })
    }
}

@Composable
private fun SgFilterTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).background(if (active) SgBlack else SgWash).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = SgSans,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = if (active) SgPaper else SgGrey,
        )
    }
}

@Composable
fun SgClients(repo: SgFakeRepo) {
    var veiled by remember { mutableStateOf(true) }
    SgPage(index = "05", title = "Clients.", kicker = "PEOPLE") {
        SgSplit(
            left = {
                SgLabel("Global record · at most one PENDING each")
                Box(Modifier.height(6.dp))
                repo.clients.forEach { c ->
                    SgSheet {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (veiled && !c.anonymized) "···· ${c.code}" else c.name,
                                fontFamily = SgSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SgInk,
                            )
                            SgLabel("${c.pending} PENDING", red = c.pending > 0)
                        }
                        SgQuiet("${c.code} · ${c.gender} · ${c.age}y · ${c.visits} visits${if (c.anonymized) " · ANONYMIZED" else ""}")
                        if (!c.anonymized) {
                            SgGhost(label = "Anonymize (keep gender + age)", onClick = { repo.anonymize(c.id) })
                        }
                    }
                }
            },
            right = {
                SgSection("5.1", "View")
                SgBody(if (veiled) "Names veiled. Codes lead." else "Names revealed. Handle with care.")
                SgGhost(label = if (veiled) "Reveal names" else "Veil names", onClick = { veiled = !veiled })
                SgSection("5.2", "Rules")
                SgRow("Scope", "global, all branches")
                SgRow("Pending cap", "at most one")
                SgRow("Delete", "anonymize, never erase")
                SgQuiet("Anonymized rows keep gender and age for reporting; PII is nulled.")
            },
        )
    }
}
