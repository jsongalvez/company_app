package com.companyb.companyapp.proto.nordfrost

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NfLogin(
    repo: NordFrostFakeRepo,
    onLogin: () -> Unit,
    onOnboarding: () -> Unit,
) {
    var name by remember { mutableStateOf(repo.me.value) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text(text = "❄ ❄ ❄", fontSize = 30.sp, color = NfFrostDeep)
        Spacer(Modifier.height(12.dp))
        NfTitle("Frost Desk", size = 30)
        Spacer(Modifier.height(6.dp))
        NfDim("Arctic blues · snow surfaces · crisp clinical chill")
        Spacer(Modifier.height(8.dp))
        NfFrostRule()
        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.fillMaxWidth(0.55f)) {
            NfMicroLabel("Keeper name")
            Spacer(Modifier.height(6.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. Frost Keeper") },
            )
            Spacer(Modifier.height(14.dp))
            NfButton(
                "Step inside",
                onClick = {
                    repo.me.value = name.ifBlank { "Frost Keeper" }
                    onLogin()
                },
            )
            Spacer(Modifier.height(10.dp))
            NfGhostButton("I am onboarding — show the locked door", onClick = onOnboarding)
            Spacer(Modifier.height(14.dp))
            NfDim("Any name works. This desk runs on local frost only — no network calls leave the window.")
        }
    }
}

@Composable
fun NfOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        NfTag("ONBOARDING · sealed in ice", dot = NfFrostDeep)
        Spacer(Modifier.height(12.dp))
        NfTitle("The door stays shut", size = 26)
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth(0.55f)) {
            NfPanelBox(border = NfFrost) {
                NfMicroLabel("Why locked")
                Spacer(Modifier.height(6.dp))
                NfBody(
                    "ONBOARDING keepers carry an empty capability bundle: no chairs, no vault, no drift. " +
                        "A Coordinator thaws the door once training completes.",
                )
                Spacer(Modifier.height(8.dp))
                NfDim("❄ Nothing here is broken — the frost simply has not cleared yet.")
            }
            Spacer(Modifier.height(14.dp))
            NfGhostButton("Back to the entrance", onClick = onBack)
        }
    }
}

@Composable
fun NfBranchSelect(
    repo: NordFrostFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(48.dp)) {
        NfMicroLabel("Frost network · pick your hall")
        Spacer(Modifier.height(6.dp))
        NfTitle("Choose a branch", size = 26)
        Spacer(Modifier.height(4.dp))
        NfDim("❄ ${repo.me.value}, three halls glimmer. Settle into one to begin the watch.")
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { b ->
            val selected = repo.branchId.value == b.id
            NfPanelBox(border = if (selected) NfGlacier else NfDrift) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        NfBody("❄ ${b.name}")
                        NfDim("◇ ${b.zone} · ${b.flavor}")
                    }
                    if (selected) {
                        NfTag("Settled", dot = NfTealInk, wash = NfTealWash)
                    } else {
                        NfGhostButton("Settle") {
                            repo.branchId.value = b.id
                            repo.log("keeper settled into ${b.name}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row {
            NfButton("Begin the watch", onClick = onPick)
            Spacer(Modifier.width(10.dp))
            NfGhostButton("Back", onClick = onBack)
        }
    }
}

@Composable
fun NfHomeScreen(repo: NordFrostFakeRepo) {
    var note by remember { mutableStateOf("") }
    NfBanner(repo)
    NfSectionHead("Home drift", "Clock the watch, call for relief across the icefields")
    NfPanelBox(border = if (repo.clockedIn.value) NfTeal else NfDrift) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                NfMicroLabel(if (repo.clockedIn.value) "On watch" else "Off watch")
                Spacer(Modifier.height(4.dp))
                NfBody("${repo.me.value} · ${repo.branchName(repo.branchId.value)}")
                NfDim("❄ Clocking writes a line into the audit trail.")
            }
            if (repo.clockedIn.value) {
                NfButton("Clock out", onClick = { repo.toggleClock() }, bg = NfSlate)
            } else {
                NfButton("Clock in", onClick = { repo.toggleClock() }, bg = NfTealInk)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    NfPanelBox {
        NfMicroLabel("Relief across the icefields")
        Spacer(Modifier.height(6.dp))
        NfDim("Ask for cover (REQUEST) or send a watch invite (INVITE). A short note keeps the far hall warm.")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. Cover SAT morning drift") },
        )
        Spacer(Modifier.height(10.dp))
        Row {
            NfButton("Request cover", onClick = { repo.requestRelief(note) }, bg = NfGlacier)
            Spacer(Modifier.width(10.dp))
            NfGhostButton("Send invite") { repo.inviteRelief(note) }
        }
    }
    Spacer(Modifier.height(12.dp))
    NfMicroLabel("Relief board · duty / invites / requests")
    Spacer(Modifier.height(8.dp))
    repo.reliefs.forEach { r ->
        NfPanelBox {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    NfTag(r.kind, dot = NfFrostDeep)
                    Spacer(Modifier.height(6.dp))
                    NfBody("${r.who} → ${r.branch} · ${r.slot}")
                    NfDim("◇ ${r.note}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfSessionsScreen(repo: NordFrostFakeRepo) {
    var filter by remember { mutableStateOf("ALL") }
    var reason by remember { mutableStateOf("") }
    NfBanner(repo)
    NfSectionHead("Sessions", "PENDING → COMPLETED / NO_SHOW / CANCELLED, frozen or thawed with reasons")
    Row {
        listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
            if (filter == f) {
                NfButton(f, onClick = { filter = f }, bg = NfGlacier)
            } else {
                NfGhostButton(f) { filter = f }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    NfDim("◇ Walk-ins never take NO_SHOW or CANCELLED — a drifter at the door is either seen or sent gently on.")
    Spacer(Modifier.height(10.dp))
    NfButton("＋ Admit walk-in", onClick = { repo.addWalkIn() }, bg = NfTealInk)
    Spacer(Modifier.height(12.dp))
    NfMicroLabel("Void reason (applies to the next void tap)")
    Spacer(Modifier.height(6.dp))
    TextField(
        value = reason,
        onValueChange = { reason = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("e.g. Client called through the whiteout") },
    )
    Spacer(Modifier.height(12.dp))
    repo.sessions.filter { filter == "ALL" || it.status.name == filter }.forEach { s ->
        NfPanelBox(border = statusDot(s.status)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NfTag(s.status.name, dot = statusDot(s.status), wash = statusWash(s.status))
                        Spacer(Modifier.width(8.dp))
                        if (s.voided) NfTag("Voided", dot = NfRose, wash = NfRoseWash)
                        if (s.walkIn) NfTag("Walk-in", dot = NfFrostDeep)
                    }
                    Spacer(Modifier.height(6.dp))
                    NfBody("❄ ${s.displayClient()} · ${s.kind} · ₱${s.price} · ${s.time}")
                    NfDim("◇ ${repo.branchName(s.branchId)} · ${s.id}")
                    if (s.voided) NfDim("◇ Void reason: ${s.voidReason}")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                if (s.status == NfSessionStatus.PENDING && !s.voided) {
                    NfButton("Complete", onClick = { repo.completeSession(s.id) }, bg = NfTealInk)
                    Spacer(Modifier.width(8.dp))
                }
                if (!s.voided) {
                    NfButton("Void", onClick = { repo.voidSession(s.id, reason) }, bg = NfRose)
                } else {
                    NfGhostButton("Restore") { repo.unvoidSession(s.id) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfClientsScreen(repo: NordFrostFakeRepo) {
    NfBanner(repo)
    NfSectionHead("Clients", "One global registry across every hall — frost keeps a single list")
    NfPanelBox(border = NfFrost) {
        NfBody(
            "At most one PENDING session per client: the desk refuses a second open booking " +
                "until the first freezes or completes.",
        )
        Spacer(Modifier.height(4.dp))
        NfDim("◇ The anonymized veil hides names on shared boards; lift it per row to verify identity.")
    }
    Spacer(Modifier.height(12.dp))
    repo.clients.forEach { c ->
        NfPanelBox {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    NfBody(if (c.masked) "❄ ▓▓▓▓ veiled drifter ▓▓▓▓" else "❄ ${c.name}")
                    NfDim("◇ ${c.note}")
                    Spacer(Modifier.height(4.dp))
                    if (c.hasPending) {
                        NfTag("One PENDING open", dot = NfAmber, wash = NfAmberWash)
                    } else {
                        NfTag("No open session", dot = NfTealInk, wash = NfTealWash)
                    }
                }
                Spacer(Modifier.width(10.dp))
                NfGhostButton(if (c.masked) "Unveil" else "Veil") { repo.toggleMask(c.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfFinanceScreen(repo: NordFrostFakeRepo) {
    var amountText by remember { mutableStateOf("1500") }
    var undoReason by remember { mutableStateOf("") }
    NfBanner(repo)
    NfSectionHead("Finance & remittance", "SESSION + PRODUCT vaults, sealed snapshots, 48-hour thaw")
    NfPanelBox(border = NfFrost) {
        NfBody(
            "Commission split note: each sealed SESSION vault splits Practitioner / Branch / Frontline " +
                "the moment the snapshot freezes — PRODUCT vaults settle to the branch ledger.",
        )
        Spacer(Modifier.height(4.dp))
        NfDim("◇ Undo reopens a sealed vault only within 48h and always with a written reason.")
    }
    Spacer(Modifier.height(12.dp))
    NfPanelBox {
        NfMicroLabel("New draft · Day 041")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Amount in pesos") },
        )
        Spacer(Modifier.height(10.dp))
        Row {
            NfButton(
                "Draft SESSION",
                onClick = { repo.addDraft(NfRemitKind.SESSION, amountText.toIntOrNull() ?: 0) },
                bg = NfGlacier,
            )
            Spacer(Modifier.width(8.dp))
            NfButton(
                "Draft PRODUCT",
                onClick = { repo.addDraft(NfRemitKind.PRODUCT, amountText.toIntOrNull() ?: 0) },
                bg = NfTealInk,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    NfMicroLabel("Undo reason (applies to the next thaw tap)")
    Spacer(Modifier.height(6.dp))
    TextField(
        value = undoReason,
        onValueChange = { undoReason = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("e.g. Counted twice through the frost") },
    )
    Spacer(Modifier.height(12.dp))
    repo.remittances.forEach { r ->
        val edge = if (r.status == NfRemitStatus.SUBMITTED) NfGlacier else NfDrift
        NfPanelBox(border = edge) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NfTag(r.kind.name, dot = NfGlacier)
                        Spacer(Modifier.width(8.dp))
                        NfTag(r.status.name, dot = if (r.status == NfRemitStatus.SUBMITTED) NfTealInk else NfAmber)
                    }
                    Spacer(Modifier.height(6.dp))
                    val snap = if (r.snapshot.isNotEmpty()) " · snapshot ${r.snapshot}" else ""
                    NfBody("₱${r.amount} · ${r.dayLabel}$snap")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                if (r.status == NfRemitStatus.DRAFT) {
                    NfButton("Seal snapshot", onClick = { repo.submitRemit(r.id) }, bg = NfGlacier)
                } else {
                    NfGhostButton("Thaw (undo 48h)") { repo.undoRemit(r.id, undoReason) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfTeamScreen(repo: NordFrostFakeRepo) {
    NfBanner(repo)
    NfSectionHead("Team", "Keepers of the frost — roles, halls, and the sealed door")
    NfPanelBox(border = NfFrost) {
        NfMicroLabel("Watch glance")
        Spacer(Modifier.height(6.dp))
        val practitioners = repo.users.count { it.role == "Practitioner" }
        NfBody("${repo.users.size} keepers · $practitioners practitioners · ${repo.branches.size} halls")
        NfDim("◇ ONBOARDING rows stay veiled until a Coordinator thaws them.")
    }
    Spacer(Modifier.height(12.dp))
    repo.users.forEach { u ->
        NfPanelBox(border = if (u.onboarding) NfFrost else NfDrift) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    NfBody(if (u.onboarding) "❄ ▓▓ sealed keeper ▓▓" else "❄ ${u.name}")
                    NfDim("◇ ${u.role} · home ${repo.branchName(u.homeBranchId)}")
                }
                if (u.onboarding) {
                    NfTag("ONBOARDING · locked", dot = NfFrostDeep)
                } else {
                    NfTag(u.role, dot = NfGlacier)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfMailboxScreen(repo: NordFrostFakeRepo) {
    NfSectionHead("Mailbox", "${repo.unreadCount()} unread letters resting on the sill")
    Row {
        NfGhostButton("Open all") { repo.markAllRead() }
    }
    Spacer(Modifier.height(12.dp))
    repo.letters.forEach { l ->
        NfPanelBox(border = if (l.read) NfDrift else NfGlacier) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NfTag(if (l.read) "Read" else "Unread", dot = if (l.read) NfMist else NfGlacier)
                        Spacer(Modifier.width(8.dp))
                        NfDim("◇ ${l.day}")
                    }
                    Spacer(Modifier.height(6.dp))
                    NfBody("${if (l.read) "○" else "●"} ${l.title}")
                    NfDim(l.body)
                }
                if (!l.read) {
                    Spacer(Modifier.width(10.dp))
                    NfButton("Open", onClick = { repo.markRead(l.id) }, bg = NfGlacier)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfAuditScreen(repo: NordFrostFakeRepo) {
    NfSectionHead("Audit trail", "Every frost-print, newest drift first")
    repo.audits.forEach { a ->
        NfPanelBox {
            NfBody("❄ ${a.actor} · ${a.action} — ${a.record}")
            Spacer(Modifier.height(4.dp))
            NfDim("◇ ${a.whenText}${if (a.reason.isNotEmpty()) " · reason: ${a.reason}" else ""} · ${a.id}")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NfProfileScreen(
    repo: NordFrostFakeRepo,
    onLogout: () -> Unit,
) {
    NfBanner(repo)
    NfSectionHead("Profile", "Tend the keeper, steer the day, rest the desk")
    NfPanelBox {
        NfMicroLabel("Keeper")
        Spacer(Modifier.height(6.dp))
        NfTitle(repo.me.value, size = 18)
        Spacer(Modifier.height(4.dp))
        NfDim("◇ Home hall: ${repo.branchName(repo.branchId.value)}")
        NfDim("◇ Watch: ${if (repo.clockedIn.value) "on — the frost glows" else "off — the drift sleeps"}")
    }
    Spacer(Modifier.height(12.dp))
    NfPanelBox(border = NfAmber) {
        NfMicroLabel("Branch-day lever")
        Spacer(Modifier.height(8.dp))
        Row {
            NfDayStatus.entries.forEach { d ->
                if (repo.dayStatus.value == d) {
                    NfButton(d.name, onClick = {}, bg = NfGlacier)
                } else {
                    NfGhostButton(d.name) {
                        repo.dayStatus.value = d
                        repo.audit(repo.me.value, "day-${d.name.lowercase()}", "Branch Day lever → ${d.name}")
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        NfDim("◇ OPEN drifts, PAST tallies, REMITTED archives. The lever writes to the audit trail.")
    }
    Spacer(Modifier.height(12.dp))
    NfPanelBox {
        NfMicroLabel("Rest the desk")
        Spacer(Modifier.height(8.dp))
        Row {
            if (repo.clockedIn.value) {
                NfButton("Clock out", onClick = { repo.toggleClock() }, bg = NfSlate)
                Spacer(Modifier.width(8.dp))
            }
            NfButton("Reset frost", onClick = { repo.reset() }, bg = NfTealInk)
            Spacer(Modifier.width(8.dp))
            NfGhostButton("Logout") { onLogout() }
        }
    }
}
