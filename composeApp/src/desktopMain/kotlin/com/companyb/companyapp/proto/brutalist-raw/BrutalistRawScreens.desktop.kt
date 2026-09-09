package com.companyb.companyapp.proto.brutalistraw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun RawButton(text: String, onClick: () -> Unit, invert: Boolean = false, hazard: Boolean = false) {
    val bg = when {
        hazard -> RawHazard
        invert -> RawInk
        else -> RawWhite
    }
    val fg = when {
        hazard -> RawWhite
        invert -> RawWhite
        else -> RawInk
    }
    Box(
        modifier = Modifier.border(BorderStroke(3.dp, RawInk)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Black, color = fg)
    }
}

@Composable
private fun RawStamp(text: String, hazard: Boolean = false) {
    Box(
        modifier = Modifier.border(BorderStroke(2.dp, if (hazard) RawHazard else RawInk))
            .background(if (hazard) RawHazardSoft else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            color = if (hazard) RawHazard else RawInk,
        )
    }
}

@Composable
private fun RawCard(title: String, stamp: String = "", stampHazard: Boolean = false, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().border(BorderStroke(3.dp, RawInk)).background(RawWhite).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (stamp.isNotEmpty()) RawStamp(stamp, stampHazard)
        }
        Spacer(Modifier.height(10.dp))
        body()
    }
}

@Composable
private fun RawHead(num: String, title: String, sub: String = "") {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = num, fontSize = 20.sp, fontWeight = FontWeight.Black, color = RawHazard)
            Spacer(Modifier.width(12.dp))
            Text(text = title, fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        if (sub.isNotEmpty()) Text(text = sub, fontWeight = FontWeight.Bold, color = RawDark)
    }
}

@Composable
private fun RawField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(text = label, fontWeight = FontWeight.Bold) },
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun RawLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().background(RawInk).padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.background(RawHazard).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(text = "PROTOTYPE // FAKE DATA", fontWeight = FontWeight.Black, color = RawWhite)
            }
            Spacer(Modifier.height(16.dp))
            Text(text = "POURED,", fontSize = 72.sp, fontWeight = FontWeight.Black, color = RawWhite)
            Text(text = "NOT", fontSize = 72.sp, fontWeight = FontWeight.Black, color = RawGravel)
            Text(text = "POLISHED.", fontSize = 72.sp, fontWeight = FontWeight.Black, color = RawHazard)
            Spacer(Modifier.height(16.dp))
            Text(text = "BRUTALIST-RAW DESKTOP DASHBOARD. NO NETWORK. NO MERCY.", color = RawWhite)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().background(RawPaper).padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "01 / LOGIN", fontWeight = FontWeight.Black, color = RawHazard, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            RawField("EMAIL", email, { email = it })
            Spacer(Modifier.height(8.dp))
            RawField("PASSWORD", pass, { pass = it })
            Spacer(Modifier.height(16.dp))
            RawButton("STAMP IN >", onLogin, invert = true)
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.clickable(onClick = onOnboarding)) {
                Text(text = "NEW HAND? VIEW ONBOARDING SLAB >", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "ANY INPUT WORKS. NOTHING LEAVES THIS MACHINE.", fontSize = 12.sp, color = RawDark)
        }
    }
}

@Composable
fun RawOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RawPaper).padding(48.dp)) {
        RawHead("00", "ONBOARDING")
        Spacer(Modifier.height(8.dp))
        RawCard("FRESH POUR / NO LOAD", "LOCKED", true) {
            Text(text = "ROLE: ONBOARDING. CAPABILITY BUNDLE: EMPTY.")
            Text(text = "EVEN WITH A BRANCH ASSIGNMENT, NOTHING DERIVES.")
            Text(text = "A MANAGER MUST GRANT A REAL ROLE VIA MANAGE_USERS.")
            Spacer(Modifier.height(12.dp))
            RawButton("< BACK TO GATE", onBack)
        }
    }
}

@Composable
fun RawBranchSelect(repo: BrutalistRawFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RawPaper).padding(48.dp)) {
        RawHead("02", "PICK YOUR SLAB", "ONE BRANCH PER POUR")
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { b ->
            val on = repo.branchId.value == b.id
            Box(
                modifier = Modifier.fillMaxWidth()
                    .border(BorderStroke(if (on) 5.dp else 3.dp, RawInk))
                    .background(if (on) RawInk else RawWhite)
                    .clickable { repo.branchId.value = b.id }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = b.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = if (on) RawWhite else RawInk,
                        )
                        Text(text = "${b.kind} / ${b.slab}", color = if (on) RawSlab else RawDark)
                    }
                    if (on) RawStamp("SET", true)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row {
            RawButton("POUR >", onPick, invert = true)
            Spacer(Modifier.width(12.dp))
            RawButton("< GATE", onBack)
        }
    }
}

@Composable
fun RawDayBanner(repo: BrutalistRawFakeRepo) {
    val s = repo.dayStatus.value
    val copy = when (s) {
        RawDayStatus.OPEN -> "OPEN / POUR TODAY. ALL ON-DUTY HANDS MAY EDIT."
        RawDayStatus.PAST -> "PAST / STRUCK AT 04:00. COORDINATOR EDITS ONLY, FLAGGED."
        RawDayStatus.REMITTED -> "REMITTED / SEALED UNDER SNAPSHOT. COORDINATOR EDITS ONLY, FLAGGED."
    }
    val bg = when (s) {
        RawDayStatus.OPEN -> RawInk
        RawDayStatus.PAST -> RawDark
        RawDayStatus.REMITTED -> RawHazard
    }
    Box(modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column {
            Text(text = "BRANCH DAY: ${s.name} / ${repo.branchName(repo.branchId.value)}", fontWeight = FontWeight.Black, color = RawWhite)
            Text(text = "$copy BOUNDARY 04:00 ASIA/MANILA.", color = RawWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RawHome(repo: BrutalistRawFakeRepo) {
    var inviteWho by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("03", "CLOCK-IN", "HOME: ${repo.branchName(repo.branchId.value)}")
            Spacer(Modifier.height(12.dp))
            RawCard("SHIFT SLAB", if (repo.clockedIn.value) "IN" else "OUT", !repo.clockedIn.value) {
                Text(text = if (repo.clockedIn.value) "ON THE SLAB. LOG SESSIONS." else "OFF THE SLAB. STAMP IN TO WORK.")
                Spacer(Modifier.height(10.dp))
                Row {
                    if (repo.clockedIn.value) RawButton("CLOCK OUT", { repo.clockOut() }, hazard = true)
                    else RawButton("CLOCK IN", { repo.clockIn() }, invert = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            RawHead("04", "RELIEF", "VIEW-ONLY UNTIL GRANTED / EXPIRES 04:00 MANILA")
            Spacer(Modifier.height(12.dp))
            RawCard("ASK FOR RELIEF") {
                Text(text = "BROADCAST REQUEST TO THE WHOLE BRANCH. NO NAMES. ONE LIVE REQUEST PER BRANCH PER DAY.")
                Spacer(Modifier.height(10.dp))
                Row {
                    RawButton("REQUEST RELIEF @ HERE", {
                        repo.reliefBoard.add(0, RawReliefItem("r${repo.reliefBoard.size + 1}", "REQUEST", "J. REYES", repo.branchName(repo.branchId.value), "TODAY", "COVER NEEDED"))
                        repo.stamp("J. REYES", "INSERT", "relief request", "BROADCAST TO BRANCH")
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            RawCard("INVITE HELP") {
                RawField("WHO (NAME)", inviteWho, { inviteWho = it })
                Spacer(Modifier.height(10.dp))
                RawButton("SEND INVITE / SAT", {
                    val who = inviteWho.ifBlank { "OPEN HAND" }
                    repo.reliefBoard.add(0, RawReliefItem("r${repo.reliefBoard.size + 1}", "INVITE", who, repo.branchName(repo.branchId.value), "SAT", "INVITED BY BRANCH"))
                    repo.stamp("S. VILLANUEVA", "INSERT", "relief invite", "INVITED $who")
                    inviteWho = ""
                }, invert = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(text = "RELIEF BOARD", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(repo.reliefBoard, key = { it.id }) { r ->
            RawCard("${r.kind} / ${r.who}", r.day) {
                Text(text = "${r.branch} / ${r.note}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row {
                    RawButton("GRANT", { repo.dropRelief(r.id, "GRANTED EDIT ACCESS") }, invert = true)
                    Spacer(Modifier.width(8.dp))
                    RawButton("DENY", { repo.dropRelief(r.id, "DENIED") })
                    Spacer(Modifier.width(8.dp))
                    RawButton("CUT", { repo.dropRelief(r.id, "REVOKED / WITHDRAWN") }, hazard = true)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawSessions(repo: BrutalistRawFakeRepo) {
    var filter by remember { mutableStateOf("ALL") }
    var openId by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    val shown = repo.sessions.filter { filter == "ALL" || it.status.name == filter }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("05", "SESSIONS", "PENDING > COMPLETED / NO_SHOW / CANCELLED")
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.border(BorderStroke(3.dp, RawHazard)).background(RawHazardSoft).padding(12.dp)) {
                Text(
                    text = "RULE STAMPED IN CONCRETE: WALK-IN SESSIONS CANNOT BE MARKED NO_SHOW OR CANCELLED.",
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
                    Box(
                        modifier = Modifier.border(BorderStroke(3.dp, RawInk))
                            .background(if (filter == f) RawInk else RawWhite)
                            .clickable { filter = f }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = f, fontWeight = FontWeight.Black, color = if (filter == f) RawWhite else RawInk, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            RawCard("ADD WALK-IN") {
                RawField("CLIENT TAG", walkName, { walkName = it })
                Spacer(Modifier.height(10.dp))
                RawButton("LOG WALK-IN", {
                    repo.addWalkIn(walkName.ifBlank { "WALK-IN ${repo.sessions.size + 1}" }, "CHECKUP", 500)
                    walkName = ""
                }, invert = true)
            }
            Spacer(Modifier.height(12.dp))
        }
        items(shown, key = { it.id }) { s ->
            val voidTag = if (s.voided) "VOID" else s.status.name
            RawCard("${s.time} / ${s.clientName}", voidTag, s.voided || s.status != RawSessionStatus.PENDING) {
                Text(text = "${s.type} / PHP ${s.price} / ${if (s.walkIn) "WALK-IN" else "BOOKED"}", fontWeight = FontWeight.Bold)
                if (s.voided) Text(text = "VOID REASON: ${s.voidReason}", fontWeight = FontWeight.Black, color = RawHazard)
                if (openId == s.id) {
                    Spacer(Modifier.height(10.dp))
                    RawField("VOID REASON", voidReason, { voidReason = it })
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (s.status == RawSessionStatus.PENDING && !s.voided) {
                            RawButton("DONE", { repo.updateSession(s.id, { it.copy(status = RawSessionStatus.COMPLETED) }, "session ${s.id}", "MARKED COMPLETED") }, invert = true)
                            Spacer(Modifier.width(8.dp))
                            if (!s.walkIn) {
                                RawButton("NO-SHOW", { repo.updateSession(s.id, { it.copy(status = RawSessionStatus.NO_SHOW) }, "session ${s.id}", "MARKED NO_SHOW") })
                                Spacer(Modifier.width(8.dp))
                                RawButton("CANCEL", { repo.updateSession(s.id, { it.copy(status = RawSessionStatus.CANCELLED) }, "session ${s.id}", "MARKED CANCELLED") })
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        if (!s.voided) {
                            RawButton("VOID", {
                                repo.updateSession(s.id, { it.copy(voided = true, voidReason = voidReason.ifBlank { "NO REASON GIVEN" }) }, "session ${s.id}", "VOIDED: ${voidReason.ifBlank { "NO REASON GIVEN" }}")
                                voidReason = ""
                            }, hazard = true)
                        } else {
                            RawButton("UNVOID", { repo.updateSession(s.id, { it.copy(voided = false, voidReason = "") }, "session ${s.id}", "UNVOIDED") }, invert = true)
                        }
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.clickable { openId = s.id }) {
                        Text(text = "OPEN FILE >", fontWeight = FontWeight.Black)
                    }
                }
                if (openId == s.id) {
                    Box(modifier = Modifier.clickable { openId = "" }) {
                        Text(text = "< CLOSE", fontWeight = FontWeight.Bold, color = RawDark)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawClients(repo: BrutalistRawFakeRepo) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("06", "CLIENTS", "GLOBAL FILE / SHARED ACROSS ALL BRANCHES")
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.border(BorderStroke(3.dp, RawInk)).background(RawWhite).padding(12.dp)) {
                Text(text = "ONE RULE: AT MOST ONE PENDING SESSION PER CLIENT. ANONYMIZED FILES KEEP GENDER + AGE ONLY.", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
        }
        items(repo.clients, key = { it.id }) { c ->
            RawCard(if (c.anonymized) "FILE ${c.id.uppercase()}" else c.name, if (c.anonymized) "MASKED" else "OPEN", c.anonymized) {
                Text(text = c.detail, fontWeight = FontWeight.Bold)
                Text(text = if (c.hasPending) "PENDING: 1 (MAX REACHED)" else "PENDING: 0", color = if (c.hasPending) RawHazard else RawDark, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                RawButton(if (c.anonymized) "REVEAL" else "ANONYMIZE", { repo.toggleAnonymized(c.id) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawFinance(repo: BrutalistRawFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("07", "FINANCE", "SESSION + PRODUCT / DRAFT > SUBMIT > SNAPSHOT > UNDO-48H")
            Spacer(Modifier.height(12.dp))
            Row {
                RawButton("+ SESSION DRAFT", {
                    repo.remittances.add(RawRemittance("R${repo.remittances.size + 1}", RawRemitKind.SESSION, RawRemitStatus.DRAFT, 9000, "TODAY"))
                    repo.stamp("M. CRUZ", "INSERT", "remittance draft", "SESSION DRAFT OPENED")
                }, invert = true)
                Spacer(Modifier.width(8.dp))
                RawButton("+ PRODUCT DRAFT", {
                    repo.remittances.add(RawRemittance("R${repo.remittances.size + 1}", RawRemitKind.PRODUCT, RawRemitStatus.DRAFT, 1500, "TODAY"))
                    repo.stamp("M. CRUZ", "INSERT", "remittance draft", "PRODUCT DRAFT OPENED")
                })
            }
            Spacer(Modifier.height(12.dp))
        }
        items(repo.remittances, key = { it.id }) { r ->
            RawCard("${r.kind} / ${r.id} / PHP ${r.amount}", r.status.name, r.status == RawRemitStatus.SUBMITTED) {
                Text(text = "DAY: ${r.dayLabel}", fontWeight = FontWeight.Bold)
                if (r.status == RawRemitStatus.SUBMITTED) {
                    Text(text = "FROZEN: ${r.snapshot} @ ${r.submittedAt}", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    RawField("UNDO REASON (REQUIRED)", undoReason, { undoReason = it })
                    Spacer(Modifier.height(10.dp))
                    RawButton("UNDO < 48H", {
                        repo.undoRemittance(r.id, undoReason.ifBlank { "ERROR POUR" })
                        undoReason = ""
                    }, hazard = true)
                } else {
                    Spacer(Modifier.height(10.dp))
                    RawButton("SUBMIT + SEAL", { repo.submitRemittance(r.id) }, invert = true)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.border(BorderStroke(3.dp, RawInk)).background(RawInk).padding(12.dp)) {
                Text(
                    text = "COMMISSION SPLIT: PRODUCT COMMISSIONS POOLED PER BRANCH DAY, SPLIT EQUALLY AMONG PRACTITIONERS + COORDINATORS CLOCKED IN AT SOLD_AT. MANUAL IN/OUT OVERRIDES ALLOWED. SEPARATE FROM COMPENSATION. NEVER REMITTED.",
                    color = RawWhite,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun RawTeam(repo: BrutalistRawFakeRepo) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("08", "TEAM", "USERS / ROLES / SLOTS")
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.border(BorderStroke(3.dp, RawInk)).background(RawWhite).padding(12.dp)) {
                Text(
                    text = "ROLE GLANCE: PRACTITIONER LOGS + EDITS HOME SLAB. COORDINATOR OWNS FINANCE + PAST DAYS. MANAGER = COORDINATOR + USERS + DELEGATES. ACCOUNTANT READS ALL, EDITS NOTHING. ONBOARDING = EMPTY BUNDLE, LOCKED.",
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        items(repo.users.withIndex().toList(), key = { it.value.id }) { (slot, u) ->
            RawCard("${slot + 1}. ${u.name}", u.role, u.onboarding) {
                Text(text = "HOME: ${repo.branchName(u.homeBranchId)}", fontWeight = FontWeight.Bold)
                if (u.onboarding) Text(text = "LOCKED: ZERO CAPABILITIES UNTIL MANAGE_USERS GRANTS A ROLE.", fontWeight = FontWeight.Black, color = RawHazard)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawMailbox(repo: BrutalistRawFakeRepo) {
    val unread = repo.notes.count { !it.read }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                RawHead("09", "MAILBOX")
                Spacer(Modifier.width(16.dp))
                RawStamp(if (unread == 0) "ZERO" else "$unread UNREAD", unread > 0)
            }
            Spacer(Modifier.height(12.dp))
            RawButton("MARK ALL READ", { repo.markAllRead() })
            Spacer(Modifier.height(12.dp))
        }
        items(repo.notes, key = { it.id }) { n ->
            RawCard(n.title, if (n.read) "READ" else "FRESH", !n.read) {
                Text(text = n.body)
                Text(text = "DAY: ${n.day}", fontWeight = FontWeight.Bold, color = RawDark)
                if (!n.read) {
                    Spacer(Modifier.height(10.dp))
                    RawButton("READ >", { repo.markRead(n.id) }, invert = true)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawAuditLog(repo: BrutalistRawFakeRepo) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("10", "AUDIT LOG", "IMMUTABLE / WHO DID WHAT + WHY")
            Spacer(Modifier.height(12.dp))
        }
        items(repo.audits, key = { it.id }) { a ->
            Box(modifier = Modifier.fillMaxWidth().border(BorderStroke(2.dp, RawInk)).background(RawWhite).padding(12.dp)) {
                Column {
                    Text(text = "${a.action} / ${a.record}", fontWeight = FontWeight.Black)
                    Text(text = "${a.actor} @ ${a.whenText}")
                    if (a.reason.isNotEmpty()) Text(text = "WHY: ${a.reason}", fontWeight = FontWeight.Bold, color = RawDark)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun RawProfile(repo: BrutalistRawFakeRepo, onLogout: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            RawHead("11", "PROFILE", "J. REYES / PRACTITIONER")
            Spacer(Modifier.height(12.dp))
            RawCard("BRANCH-DAY REMOTE") {
                Row {
                    RawDayStatus.entries.forEach { s ->
                        val on = repo.dayStatus.value == s
                        Box(
                            modifier = Modifier.border(BorderStroke(3.dp, RawInk))
                                .background(if (on) RawHazard else RawWhite)
                                .clickable {
                                    repo.dayStatus.value = s
                                    repo.stamp("J. REYES", "UPDATE", "branch day", "SET ${s.name}")
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(text = s.name, fontWeight = FontWeight.Black, color = if (on) RawWhite else RawInk, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RawCard("SHIFT") {
                Row {
                    if (repo.clockedIn.value) RawButton("CLOCK OUT", { repo.clockOut() }, hazard = true)
                    else RawButton("CLOCK IN", { repo.clockIn() }, invert = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            RawCard("EXIT") {
                RawButton("LOG OUT >", onLogout)
            }
        }
    }
}
