package com.companyb.companyapp.proto.pixelretro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PxLogin(
    repo: PixelRetroFakeRepo,
    onLogin: () -> Unit,
    onOnboarding: () -> Unit,
) {
    var name by remember { mutableStateOf(repo.me.value) }
    var mail by remember { mutableStateOf("cadet@pixel.clinic") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = "▓▓▓ PIXEL CLINIC ▓▓▓",
            fontFamily = PxMono,
            fontWeight = FontWeight.Black,
            fontSize = 30.sp,
            letterSpacing = 3.sp,
            color = PxYellow,
        )
        Spacer(Modifier.height(4.dp))
        PxDim("INSERT COIN · 1 CREDIT · PRESS START TO HEAL")
        Spacer(Modifier.height(20.dp))
        PxPanelBox(border = PxYellow, modifier = Modifier.fillMaxWidth(0.6f)) {
            PxSectionHead("Player login", "Any name + mail starts the demo run")
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = mail,
                onValueChange = { mail = it },
                label = { Text("Mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PxButton(
                "▶ Press start",
                onClick = {
                    repo.me.value = name.ifBlank { "Pixel Cadet" }
                    repo.audit(repo.me.value, "login", "Pressed start at the arcade cab")
                    onLogin()
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .border(2.dp, PxDim, RectangleShape)
                        .clickable(onClick = onOnboarding)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "ONBOARDING? VIEW THE LOCKED DOOR",
                    fontFamily = PxMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PxDim,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PxDim("♥ HI-SCORE 999999 · NO COINS NEEDED · FAKE DATA ONLY ♥")
    }
}

@Composable
fun PxOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(48.dp)) {
        Spacer(Modifier.height(40.dp))
        PxTitle("🔒 Stage locked: onboarding", size = 20, color = PxRed)
        Spacer(Modifier.height(8.dp))
        PxPanelBox(border = PxRed) {
            PxBody("This cadet wears the ONBOARDING tag. Capability bundle is sealed: no quests, no coffers, no mailbox. Finish the tutorial (outside this demo) to unlock the arcade floor.")
            Spacer(Modifier.height(6.dp))
            PxBody("Locked doors: Home base · Quests · Party list · Coffers · Guild · Mailbox · Quest log.")
        }
        Spacer(Modifier.height(12.dp))
        PxButton("◀ Back to title", onClick = onBack, bg = PxCyan)
    }
}

@Composable
fun PxBranchSelect(
    repo: PixelRetroFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        PxTitle("Select your stage", size = 20)
        Spacer(Modifier.height(2.dp))
        PxDim("▓ 3 ZONES · PICK ONE TO CONTINUE")
        Spacer(Modifier.height(12.dp))
        repo.branches.forEach { b ->
            val picked = repo.branchId.value == b.id
            PxPanelBox(border = if (picked) PxGreen else PxDim) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                repo.branchId.value = b.id
                                repo.audit(repo.me.value, "branch-select", b.name)
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (picked) "▶" else "▷",
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (picked) PxGreen else PxDim,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = b.name.uppercase(),
                            fontFamily = PxMono,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = PxInk,
                        )
                        PxDim("${b.zone} · ${b.flavor}")
                    }
                    PxTag(if (picked) "SELECTED" else b.zone, if (picked) PxGreen else PxCyan)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row {
            PxButton(
                "Enter ${repo.branchName(repo.branchId.value)}",
                onClick = {
                    repo.audit(repo.me.value, "enter", repo.branchName(repo.branchId.value))
                    onPick()
                },
            )
            Spacer(Modifier.width(10.dp))
            PxButton("◀ Title", onClick = onBack, bg = PxDim)
        }
    }
}

@Composable
fun PxHomeScreen(repo: PixelRetroFakeRepo) {
    var note by remember { mutableStateOf("") }
    PxSectionHead("Home base", "Clock in, then rally the relief party")
    PxBanner(repo)
    PxPanelBox(border = PxGreen) {
        PxTitle("Shift lever", size = 15, color = PxGreen)
        Spacer(Modifier.height(6.dp))
        PxBody("${repo.me.value} @ ${repo.branchName(repo.branchId.value)} — tap to punch the time card.")
        Spacer(Modifier.height(8.dp))
        PxButton(
            if (repo.clockedIn.value) "■ Clock out" else "▶ Clock in",
            onClick = { repo.toggleClock() },
            bg = if (repo.clockedIn.value) PxRed else PxGreen,
        )
    }
    Spacer(Modifier.height(10.dp))
    PxPanelBox(border = PxCyan) {
        PxTitle("Relief party board", size = 15, color = PxCyan)
        Spacer(Modifier.height(4.dp))
        PxBody("Call for backup (REQUEST) or summon a pal (INVITE). Duty roster below.")
        Spacer(Modifier.height(6.dp))
        TextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note for the party…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            PxButton("✚ Request", onClick = { repo.requestRelief(note) }, bg = PxCyan)
            Spacer(Modifier.width(8.dp))
            PxButton("➤ Invite", onClick = { repo.inviteRelief(note) }, bg = PxPink)
        }
        Spacer(Modifier.height(8.dp))
        repo.reliefs.forEach { r ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(PxVoid)
                        .border(2.dp, PxDim, RectangleShape)
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PxTag(r.kind, if (r.kind == "DUTY") PxGreen else PxYellow)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PxBody("${r.who} → ${r.branch}")
                    PxDim("${r.slot} · ${r.note}")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun PxSessionsScreen(repo: PixelRetroFakeRepo) {
    var filter by remember { mutableStateOf<PxSessionStatus?>(null) }
    PxSectionHead("Quest board", "PENDING → COMPLETED / NO_SHOW / CANCELLED")
    PxPanelBox(border = PxDim) {
        PxBody("Rule cartridge: walk-in sprites can never log NO_SHOW or CANCELLED — they either get seen (COMPLETED) or stay PENDING. Void stamps a quest VOID with a reason; unvoid peels the stamp off.")
    }
    Spacer(Modifier.height(10.dp))
    Row {
        PxFilterChip("ALL", filter == null, PxCyan) { filter = null }
        Spacer(Modifier.width(6.dp))
        PxSessionStatus.entries.forEach { s ->
            PxFilterChip(s.name, filter == s, statusColor(s)) { filter = s }
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    PxButton("+ Admit walk-in", onClick = { repo.addWalkIn() }, bg = PxYellow)
    Spacer(Modifier.height(10.dp))
    repo.sessions.filter { filter == null || it.status == filter }.forEach { s ->
        PxSessionRow(repo, s)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PxFilterChip(
    label: String,
    on: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(if (on) color else PxVoid)
                .border(2.dp, color, RectangleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontFamily = PxMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (on) PxVoid else color,
        )
    }
}

@Composable
private fun PxSessionRow(
    repo: PixelRetroFakeRepo,
    s: PxSession,
) {
    var reason by remember { mutableStateOf("") }
    val edge = if (s.voided) PxRed else statusColor(s.status)
    PxPanelBox(border = edge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${s.id.uppercase()} · ${s.displayClient().uppercase()}",
                    fontFamily = PxMono,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = PxInk,
                )
                PxDim("${s.time} · ${s.kind} · ₱${s.price} · ${repo.branchName(s.branchId)}")
            }
            PxTag(s.status.name, statusColor(s.status))
        }
        if (s.voided) {
            Spacer(Modifier.height(4.dp))
            PxTag("VOID: ${s.voidReason}", PxRed)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            if (s.status == PxSessionStatus.PENDING && !s.voided) {
                PxButton("✔ Done", onClick = { repo.completeSession(s.id) }, bg = PxGreen)
                Spacer(Modifier.width(6.dp))
            }
            if (!s.voided) {
                PxButton("✖ Void", onClick = { repo.voidSession(s.id, reason) }, bg = PxRed)
            } else {
                PxButton("↩ Unvoid", onClick = { repo.unvoidSession(s.id) }, bg = PxCyan)
            }
        }
        if (!s.voided) {
            Spacer(Modifier.height(6.dp))
            TextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Void reason…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PxClientsScreen(repo: PixelRetroFakeRepo) {
    PxSectionHead("Party list", "One global registry across all zones")
    PxPanelBox(border = PxDim) {
        PxBody("Cartridge rule: a client holds at most ONE pending quest. Anonymized rows show █ blocks until revealed.")
    }
    Spacer(Modifier.height(10.dp))
    repo.clients.forEach { c ->
        PxPanelBox(border = if (c.masked) PxDim else PxPink) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (c.masked) "████ ██████" else c.name.uppercase(),
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = PxInk,
                    )
                    PxDim(if (c.masked) "masked view · tap reveal" else c.note)
                }
                if (c.hasPending) {
                    PxTag("1 PENDING", PxYellow)
                    Spacer(Modifier.width(6.dp))
                }
                PxButton(
                    if (c.masked) "Reveal" else "Mask",
                    onClick = { repo.toggleMask(c.id) },
                    bg = PxCyan,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PxFinanceScreen(repo: PixelRetroFakeRepo) {
    var amount by remember { mutableStateOf("1500") }
    PxSectionHead("Coffers", "SESSION + PRODUCT ledgers · submit seals a snapshot")
    PxPanelBox(border = PxDim) {
        PxBody("Commission split note: completed quest coins split Practitioner 70 / Branch 30 at payout; the split memo rides on every SESSION snapshot. Undo reopens a SUBMITTED coffer within 48h with a reason.")
    }
    Spacer(Modifier.height(10.dp))
    Row {
        PxButton("+ Draft SESSION ₱$amount", onClick = { repo.addDraft(PxRemitKind.SESSION, amount.toIntOrNull() ?: 0) }, bg = PxGreen)
        Spacer(Modifier.width(6.dp))
        PxButton("+ Draft PRODUCT ₱$amount", onClick = { repo.addDraft(PxRemitKind.PRODUCT, amount.toIntOrNull() ?: 0) }, bg = PxPink)
    }
    Spacer(Modifier.height(6.dp))
    TextField(
        value = amount,
        onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(6) },
        label = { Text("Draft amount ₱") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    repo.remittances.forEach { r ->
        PxPanelBox(border = if (r.status == PxRemitStatus.SUBMITTED) PxGreen else PxYellow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${r.kind} · ₱${r.amount} · ${r.dayLabel}",
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = PxInk,
                    )
                    PxDim(if (r.snapshot.isNotEmpty()) "snapshot ${r.snapshot} · sealed" else "draft · not sealed")
                }
                PxTag(r.status.name, if (r.status == PxRemitStatus.SUBMITTED) PxGreen else PxYellow)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                if (r.status == PxRemitStatus.DRAFT) {
                    PxButton("Seal + submit", onClick = { repo.submitRemit(r.id) }, bg = PxGreen)
                } else {
                    PxButton("↩ Undo 48h", onClick = { repo.undoRemit(r.id, "Opened by ${repo.me.value}") }, bg = PxOrange)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PxTeamScreen(repo: PixelRetroFakeRepo) {
    PxSectionHead("Guild roster", "Users + roles · ONBOARDING stays locked")
    repo.users.forEach { u ->
        PxPanelBox(border = if (u.onboarding) PxRed else PxCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (u.onboarding) "🔒" else "♥",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = u.name.uppercase(),
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = PxInk,
                    )
                    PxDim("${u.role} · home ${repo.branchName(u.homeBranchId)}${if (u.onboarding) " · LOCKED, no capabilities" else ""}")
                }
                PxTag(u.role, if (u.onboarding) PxRed else PxGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    PxPanelBox(border = PxYellow) {
        PxTitle("Rank glance", size = 14, color = PxYellow)
        Spacer(Modifier.height(4.dp))
        PxBody("LVL 12 Maya · LVL 9 Rico · LVL 15 Lena · LVL 7 Omar · LVL 1 Quinn (tutorial)")
    }
}

@Composable
fun PxMailboxScreen(repo: PixelRetroFakeRepo) {
    PxSectionHead("Mailbox", "${repo.unreadCount()} unread letters")
    PxButton("✉ Open all", onClick = { repo.markAllRead() }, bg = PxCyan)
    Spacer(Modifier.height(10.dp))
    repo.letters.forEach { l ->
        PxPanelBox(border = if (l.read) PxDim else PxPink) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (l.read) "▢" else "▣",
                    fontFamily = PxMono,
                    fontSize = 18.sp,
                    color = if (l.read) PxDim else PxPink,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = l.title.uppercase(),
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = PxInk,
                    )
                    PxDim("${l.day} · ${l.body}")
                }
            }
            if (!l.read) {
                Spacer(Modifier.height(6.dp))
                PxButton("Read", onClick = { repo.markRead(l.id) }, bg = PxYellow)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PxAuditScreen(repo: PixelRetroFakeRepo) {
    PxSectionHead("Quest log", "Every stamp + seal, newest first")
    repo.audits.forEach { a ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PxPanel)
                    .border(2.dp, PxDim, RectangleShape)
                    .padding(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${a.action.uppercase()} · ${a.record}",
                    fontFamily = PxMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PxInk,
                )
                PxDim("${a.actor} · ${a.whenText}${if (a.reason.isNotEmpty()) " · why: ${a.reason}" else ""}")
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun PxProfileScreen(
    repo: PixelRetroFakeRepo,
    onLogout: () -> Unit,
) {
    PxSectionHead("Player card", "Day lever · clock · wipe · quit")
    PxPanelBox(border = PxYellow) {
        PxTitle(repo.me.value, size = 16, color = PxYellow)
        Spacer(Modifier.height(2.dp))
        PxDim("STAGE: ${repo.branchName(repo.branchId.value)} · ${if (repo.clockedIn.value) "ON SHIFT" else "OFF SHIFT"}")
    }
    Spacer(Modifier.height(10.dp))
    PxPanelBox(border = PxCyan) {
        PxTitle("Branch day lever", size = 14, color = PxCyan)
        Spacer(Modifier.height(6.dp))
        Row {
            PxDayStatus.entries.forEach { d ->
                val on = repo.dayStatus.value == d
                Box(
                    modifier =
                        Modifier
                            .background(if (on) PxCyan else PxVoid)
                            .border(2.dp, PxCyan, RectangleShape)
                            .clickable {
                                repo.dayStatus.value = d
                                repo.audit(repo.me.value, "day-${d.name.lowercase()}", "Branch day lever")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = d.name,
                        fontFamily = PxMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (on) PxVoid else PxCyan,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        PxBody("OPEN pays out · PAST freezes the tally · REMITTED seals the run. Boundary 04:00 Asia/Manila.")
    }
    Spacer(Modifier.height(10.dp))
    Row {
        PxButton(
            if (repo.clockedIn.value) "■ Clock out" else "▶ Clock in",
            onClick = { repo.toggleClock() },
            bg = PxGreen,
        )
        Spacer(Modifier.width(8.dp))
        PxButton("↺ Reset demo", onClick = { repo.reset() }, bg = PxOrange)
        Spacer(Modifier.width(8.dp))
        PxButton(
            "✖ Logout",
            onClick = {
                repo.audit(repo.me.value, "logout", "Quit to title")
                onLogout()
            },
            bg = PxRed,
        )
    }
}
