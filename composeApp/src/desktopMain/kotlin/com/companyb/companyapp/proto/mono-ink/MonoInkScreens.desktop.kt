package com.companyb.companyapp.proto.monoink

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun MonoStatus.pressName(): String =
    when (this) {
        MonoStatus.PENDING -> "PENDING"
        MonoStatus.COMPLETED -> "COMPLETED"
        MonoStatus.NO_SHOW -> "NO-SHOW"
        MonoStatus.CANCELLED -> "CANCELLED"
    }

@Composable
fun MonoLogin(
    onEnter: (String) -> Unit,
    onPreviewOnboarding: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    PressPage(folio = "Front page · No. 805", title = "One ink. Every story.", deck = "The whole company printed in a single ink — weight and size carry the hierarchy. Nothing hides behind color.") {
        PressPlate {
            Kicker("Sign in")
            PressBody("Any work email pulls a proof. This press runs on fake copy — nothing leaves the machine.")
            PressField(value = address, onChange = { address = it }, label = "Work email")
            PressPrimary(label = "Pull the proof", onClick = { onEnter(address.ifBlank { "practitioner@company.app" }) })
        }
        PressSecondary(label = "Read the ONBOARDING notice", onClick = onPreviewOnboarding)
        PressSmall("Fake door — any email opens it. No network, no password, no server.")
    }
}

@Composable
fun MonoOnboardingLocked(onBack: () -> Unit) {
    PressPage(folio = "Notice · No. 805", title = "Onboarding stays in the gallery.", deck = "An ONBOARDING profile observes before it acts.", onBack = onBack) {
        PressPlate {
            Kicker("Capability bundle · empty")
            PressRow(label = "Sessions", value = "observe only")
            PressRow(label = "Clients", value = "observe only")
            PressRow(label = "Finance", value = "locked")
            PressRow(label = "Branch day", value = "locked")
            PressSmall("A coordinator grants Practitioner capabilities after orientation. Until then: read, watch, learn.")
        }
        InkSpacer()
        PressBody("The locked mat is deliberate — newcomers see the shape of the work before they touch the type.")
    }
}

@Composable
fun MonoBranchSelect(
    repo: MonoFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    PressPage(folio = "Bureaus · No. 805", title = "Choose your bureau.", deck = "One branch at a time. The others keep without you.", onBack = onBack) {
        repo.branches.forEachIndexed { n, branch ->
            PressPlate {
                Row(modifier = Modifier.fillMaxWidth().clickable { repo.branchId = branch.id; onPick() }.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    Text("0${n + 1}", fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Ink)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(branch.name.uppercase(), fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Ink)
                        Text("${branch.place} · DAY ${branch.day.name}", fontFamily = PressMono, fontSize = 12.sp, color = InkSoft)
                    }
                }
            }
        }
        PressSmall("Sunrise runs OPEN, Harbor rests PAST, Lingap stands REMITTED — one of each, for judging.")
    }
}

@Composable
fun MonoFront(
    repo: MonoFakeRepo,
    onOpenSessions: () -> Unit,
) {
    val branch = repo.currentBranch()
    var reliefNote by remember { mutableStateOf("") }
    PressPage(folio = "Front page · ${branch.name}", title = "Today's sheet.", deck = "${branch.name} · ${branch.place} — set the day in type, then work it.", topNote = { DayBanner(branch.name, branch.day) }) {
        PressSection("01", "Clock")
        PressPlate {
            PressRow(label = "State", value = if (repo.clockedIn) "CLOCKED IN" else "CLOCKED OUT")
            PressRow(label = "Desk", value = repo.email.ifBlank { "Practitioner" })
            if (repo.clockedIn) {
                PressPrimary(label = "Clock out", onClick = { repo.clock(false) })
            } else {
                PressPrimary(label = "Clock in", onClick = { repo.clock(true) })
            }
        }
        PressSection("02", "Relief duty")
        PressPlate {
            Kicker("On the stone")
            repo.relief.forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${if (r.kind == MonoReliefKind.INVITE) "INVITE" else "REQUEST"} · ${r.branch} · ${r.day}", fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp, color = Ink)
                    PressSmall("${r.note} — ${r.answered.ifBlank { "unanswered" }}")
                    if (r.answered.isBlank() && r.kind == MonoReliefKind.INVITE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ACCEPT", fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp, color = Ink, modifier = Modifier.border(1.dp, Ink).padding(horizontal = 10.dp, vertical = 6.dp).clickable { repo.answerRelief(r.id, "ACCEPTED") })
                            Text("DECLINE", fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp, color = Ink, modifier = Modifier.border(1.dp, Ink).padding(horizontal = 10.dp, vertical = 6.dp).clickable { repo.answerRelief(r.id, "DECLINED") })
                        }
                    }
                }
            }
            PressField(value = reliefNote, onChange = { reliefNote = it }, label = "Ask for relief (reason + day)")
            PressSecondary(label = "File relief request", onClick = { repo.askRelief(reliefNote); reliefNote = "" })
        }
        PressSection("03", "The day in figures")
        val mine = repo.branchSessions()
        PressPlate {
            TableHead("Measure" to 2f, "Figure" to 1f)
            TableRowLine(listOf("Sessions on sheet" to 2f, mine.size.toString() to 1f))
            TableRowLine(listOf("Awaiting (pending)" to 2f, mine.count { it.status == MonoStatus.PENDING }.toString() to 1f))
            TableRowLine(listOf("Set in type (completed)" to 2f, mine.count { it.status == MonoStatus.COMPLETED }.toString() to 1f))
            TableRowLine(listOf("Unread mail" to 2f, repo.notices.count { !it.read }.toString() to 1f))
            PressSecondary(label = "Open the session ledger", onClick = onOpenSessions)
        }
    }
}

@Composable
fun MonoSessions(repo: MonoFakeRepo) {
    var filter by remember { mutableStateOf<MonoStatus?>(null) }
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    PressPage(folio = "Ledger · ${branch.name}", title = "Session ledger.", deck = "PENDING → COMPLETED / NO-SHOW / CANCELLED. Walk-ins never take NO-SHOW or CANCELLED — they are here or they are not.", topNote = { DayBanner(branch.name, branch.day) }) {
        PressSection("04", "Filter the ledger")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "ALL", MonoStatus.PENDING to "PEND", MonoStatus.COMPLETED to "DONE", MonoStatus.NO_SHOW to "N-SHOW", MonoStatus.CANCELLED to "CXLD").forEach { (s, label) ->
                val on = filter == s
                Text(
                    label,
                    fontFamily = PressSans,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = if (on) Paper else Ink,
                    modifier = Modifier.border(1.dp, Ink).padding(horizontal = 8.dp, vertical = 6.dp).clickable { filter = s },
                )
            }
        }
        val rows = repo.branchSessions().filter { filter == null || it.status == filter }
        PressPlate {
            TableHead("Client" to 2f, "Hour" to 1.4f, "State" to 1.2f)
            if (rows.isEmpty()) PressSmall("No sessions under this heading — the column is empty, not missing.")
            rows.forEach { s ->
                Column(modifier = Modifier.fillMaxWidth().clickable { openId = if (openId == s.id) null else s.id }) {
                    TableRowLine(listOf(s.client to 2f, s.time.substringAfter("· ").ifBlank { s.time } to 1.4f, s.status.pressName() to 1.2f), boldFirst = true)
                }
                if (openId == s.id) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PressSmall("${s.service} · ${if (s.kind == MonoKind.WALK_IN) "WALK-IN" else "BOOKED"} · ₱${"%,.2f".format(s.amount)} · ${s.practitioner}")
                        if (s.voidReason.isNotBlank()) PressSmall("Void note: ${s.voidReason}")
                        if (s.status == MonoStatus.PENDING) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("COMPLETE", fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, color = Ink, modifier = Modifier.border(1.dp, Ink).padding(horizontal = 8.dp, vertical = 6.dp).clickable { repo.setStatus(s.id, MonoStatus.COMPLETED) })
                                if (s.kind == MonoKind.BOOKED) {
                                    Text("NO-SHOW", fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = Ink, modifier = Modifier.border(1.dp, Ink).padding(horizontal = 8.dp, vertical = 6.dp).clickable { repo.setStatus(s.id, MonoStatus.NO_SHOW) })
                                }
                            }
                            PressField(value = reason, onChange = { reason = it }, label = "Void reason (required to void)")
                            PressSecondary(label = "Void with reason", onClick = { if (reason.isNotBlank()) { repo.voidSession(s.id, reason); reason = "" } })
                        } else if (s.status == MonoStatus.CANCELLED) {
                            PressField(value = reason, onChange = { reason = it }, label = "Unvoid reason (required)")
                            PressSecondary(label = "Unvoid back to PENDING", onClick = { if (reason.isNotBlank()) { repo.unvoidSession(s.id, reason); reason = "" } })
                        } else {
                            PressSmall("Closed states stand as printed. Void path runs through PENDING.")
                        }
                    }
                }
            }
        }
        PressSection("05", "Walk-in entry")
        PressPlate {
            PressSmall("Walk-ins enter as PENDING and can only complete — the NO-SHOW / CANCELLED stamps do not apply to them.")
            PressField(value = name, onChange = { name = it }, label = "Walk-in name (blank = numbered)")
            PressField(value = service, onChange = { service = it }, label = "Service (blank = Chair 20)")
            PressPrimary(label = "Set walk-in line", onClick = { repo.addWalkIn(name, service); name = ""; service = "" })
        }
    }
}

@Composable
fun MonoClients(repo: MonoFakeRepo) {
    var veiled by remember { mutableStateOf(true) }
    PressPage(folio = "Directory · global", title = "Client directory.", deck = "Clients are global — every bureau reads the same directory. At most one PENDING session per client; the book refuses a second.") {
        PressSection("06", "Reading rule")
        PressPlate {
            Kicker(if (veiled) "Anonymized view" else "Named view")
            PressSmall("Codes print by default; names stay veiled until a practitioner lifts the veil for the job at hand.")
            PressSecondary(label = if (veiled) "Lift the veil" else "Lower the veil", onClick = { veiled = !veiled })
        }
        PressPlate {
            TableHead("Client" to 2f, "Pend" to 0.7f, "Visits" to 0.8f)
            repo.clients.forEach { c ->
                TableRowLine(listOf((if (veiled) c.code else c.name) to 2f, c.pending.toString() to 0.7f, c.visits.toString() to 0.8f), boldFirst = !veiled)
                if (!veiled) PressSmall("${c.code} · ${c.note}")
            }
        }
        PressSmall("Booking rule: a client with one PENDING line cannot take another until the first is COMPLETED, NO-SHOW, or CANCELLED.")
    }
}

@Composable
fun MonoFinance(repo: MonoFakeRepo) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var kind by remember { mutableStateOf(MonoDraftKind.SESSION) }
    var undoReason by remember { mutableStateOf("") }
    PressPage(folio = "Counting room", title = "Counting room.", deck = "SESSION drafts carry net income; PRODUCT drafts carry price × quantity. Submit seals a snapshot; undo runs 48 hours with a reason.") {
        PressSection("07", "New draft")
        PressPlate {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MonoDraftKind.SESSION to "SESSION", MonoDraftKind.PRODUCT to "PRODUCT").forEach { (k, t) ->
                    Text(
                        t,
                        fontFamily = PressSans,
                        fontWeight = if (kind == k) FontWeight.Black else FontWeight.Normal,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        color = if (kind == k) Paper else Ink,
                        modifier = Modifier.border(1.dp, Ink).padding(horizontal = 10.dp, vertical = 6.dp).clickable { kind = k },
                    )
                }
            }
            PressField(value = label, onChange = { label = it }, label = "Draft label")
            PressField(value = amount, onChange = { amount = it }, label = if (kind == MonoDraftKind.SESSION) "Net income" else "Unit price")
            if (kind == MonoDraftKind.PRODUCT) PressField(value = qty, onChange = { qty = it }, label = "Quantity")
            PressPrimary(label = "File draft", onClick = { repo.addDraft(kind, label, amount.toDoubleOrNull() ?: 0.0, qty.toIntOrNull() ?: 1); label = ""; amount = ""; qty = "1" })
        }
        PressSection("08", "Drafts & snapshots")
        repo.drafts.forEach { d ->
            PressPlate {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(d.label.uppercase(), fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Ink)
                        Text(
                            "${d.kind.name} · ₱${"%,.2f".format(d.amount)}${if (d.kind == MonoDraftKind.PRODUCT) " × ${d.qty} = ₱${"%,.2f".format(d.amount * d.qty)}" else ""}",
                            fontFamily = PressMono,
                            fontSize = 12.sp,
                            color = InkSoft,
                        )
                    }
                    Stamp(if (d.undone) "UNDONE" else if (d.submitted) "SEALED" else "DRAFT")
                }
                if (d.snapshot.isNotBlank()) PressSmall("Snapshot ${d.snapshot} on file.")
                if (d.undoReason.isNotBlank()) PressSmall("Undo note: ${d.undoReason}")
                if (!d.submitted) {
                    PressSecondary(label = "Submit & seal snapshot", onClick = { repo.submitDraft(d.id) })
                } else if (!d.undone) {
                    PressField(value = undoReason, onChange = { undoReason = it }, label = "Undo reason (within 48h)")
                    PressSecondary(label = "Undo within 48h", onClick = { if (undoReason.isNotBlank()) { repo.undoDraft(d.id, undoReason); undoReason = "" } })
                }
            }
        }
        PressSmall("Commission split: the house and the hands divide SESSION net per the posted table; PRODUCT margin follows the counter sheet. The split prints on the snapshot.")
    }
}

@Composable
fun MonoTeam(repo: MonoFakeRepo) {
    PressPage(folio = "Staff box", title = "Staff box.", deck = "Users and roles, set in a single column — including the locked ONBOARDING row.") {
        PressSection("09", "Masthead")
        PressPlate {
            TableHead("Name" to 1.4f, "Role" to 1.2f, "Note" to 2f)
            repo.mates.forEach { m ->
                TableRowLine(listOf((m.name + if (m.locked) " (locked)" else "") to 1.4f, m.role to 1.2f, m.note to 2f), boldFirst = true)
            }
        }
        PressSmall("Capability glance: Practitioner works sessions; Coordinator owns the book; MANAGER signs; Accountant reconciles; ONBOARDING observes.")
    }
}

@Composable
fun MonoMailbox(repo: MonoFakeRepo) {
    PressPage(folio = "Letters", title = "Letters.", deck = "Notifications arrive as letters — unopened ones print bold, opened ones whisper.") {
        PressPlate {
            PressRow(label = "Unopened", value = repo.notices.count { !it.read }.toString())
            PressSecondary(label = "Mark all opened", onClick = { repo.markAllRead() })
        }
        repo.notices.forEach { n ->
            PressPlate {
                Column(modifier = Modifier.fillMaxWidth().clickable { repo.toggleNotice(n.id) }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (n.read) "○" else "●", fontFamily = PressSans, fontSize = 14.sp, color = Ink)
                        Text(n.day.uppercase(), fontFamily = PressMono, fontSize = 11.sp, color = InkSoft)
                    }
                    Text(n.title, fontFamily = PressSerif, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Black, fontSize = 17.sp, color = Ink)
                    Text(n.body, fontFamily = PressSerif, fontStyle = if (n.read) FontStyle.Normal else FontStyle.Italic, fontSize = 14.sp, color = if (n.read) InkSoft else Ink)
                    PressSmall("${n.branch} · tap to ${if (n.read) "reseal as unopened" else "open"}")
                }
            }
        }
    }
}

@Composable
fun MonoAudit(repo: MonoFakeRepo) {
    PressPage(folio = "Press log", title = "Press log.", deck = "Every impression leaves a mark — voids, unvoids, submits, undos, clocks, relief.") {
        PressPlate {
            TableHead("Hour" to 1.2f, "Act" to 1.6f, "Mark" to 2.2f)
            repo.audits.forEach { a ->
                TableRowLine(listOf(a.time.take(9) to 1.2f, "${a.actor} ${a.action}" to 1.6f, a.detail to 2.2f))
            }
        }
        PressSmall("Newest impressions print first. Reasons ride with void, unvoid, and undo marks.")
    }
}

@Composable
fun MonoProfile(
    repo: MonoFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
) {
    val branch = repo.currentBranch()
    PressPage(folio = "Colophon", title = "Colophon.", deck = "The practitioner, the day, and the way out.") {
        PressPlate {
            Kicker("Practitioner")
            PressRow(label = "Desk", value = repo.email.ifBlank { "Practitioner" })
            PressRow(label = "State", value = if (repo.clockedIn) "CLOCKED IN" else "CLOCKED OUT")
            if (repo.clockedIn) PressSecondary(label = "Clock out", onClick = { repo.clock(false) }) else PressPrimary(label = "Clock in", onClick = { repo.clock(true) })
        }
        PressPlate {
            Kicker("Move the day · ${branch.name}")
            Text("Now printing: ${branch.day.name}", fontFamily = PressSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoDay.entries.forEach { d ->
                    Text(
                        d.name,
                        fontFamily = PressSans,
                        fontWeight = if (branch.day == d) FontWeight.Black else FontWeight.Normal,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (branch.day == d) Paper else Ink,
                        modifier = Modifier.border(1.dp, Ink).padding(horizontal = 8.dp, vertical = 6.dp).clickable { repo.moveDay(d) },
                    )
                }
            }
            PressSmall("Days turn at 04:00 Asia/Manila.")
        }
        PressPlate {
            Kicker("Edition")
            PressSecondary(label = "Reset demo copy", onClick = onReset)
            PressPrimary(label = "Log out & lock the forme", onClick = onLogout)
        }
        InkSpacer()
        Text("Set in one ink on paper. No color was harmed — none was used.", fontFamily = PressSerif, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = InkSoft)
    }
}
