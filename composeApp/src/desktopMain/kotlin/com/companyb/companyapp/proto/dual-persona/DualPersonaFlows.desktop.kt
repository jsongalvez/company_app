package com.companyb.companyapp.proto.dualpersona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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

// #781 — dual-persona identity + care flows: onboarding lock, login, branches, clock-in home,
// sessions with lens compare, clients with masked coordinator view, relief board.

@Composable
fun DpOnboarding(repo: DpFakeRepo, p: DpPalette, onNext: () -> Unit) {
    DpSectionTitle(p, "Welcome", "step 1 of 3")
    DpCard(p) {
        Text("One ward, two lenses.", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            "Flip Practitioner ⇄ Coordinator any time. Nav, home, and actions reshape " +
                "around your capabilities — the records underneath stay identical.",
            color = p.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        DpNote(p, "ONBOARDING accounts are locked: they can tour this intro but cannot clock in, treat, or remit until a Coordinator activates them.")
        Spacer(Modifier.height(10.dp))
        DpRowButtons {
            DpButton(p, "Continue to login", onNext)
            DpButton(p, "Tour as " + repo.persona.lensName(), { repo.persona = if (repo.persona == DpPersona.PRACTITIONER) DpPersona.COORDINATOR else DpPersona.PRACTITIONER }, primary = false)
        }
    }
}

@Composable
fun DpLogin(repo: DpFakeRepo, p: DpPalette, onNext: () -> Unit) {
    var picked by remember { mutableStateOf(repo.currentUserId ?: "u-prac") }
    DpSectionTitle(p, "Login", "fake directory")
    DpCard(p) {
        for (user in repo.users) {
            val locked = user.role == DpRole.ONBOARDING
            val sel = picked == user.id
            Row(
                Modifier.fillMaxWidth()
                    .background(if (sel) p.infoBand else p.card, RoundedCornerShape(8.dp))
                    .border(1.dp, if (sel) p.accent else p.line, RoundedCornerShape(8.dp))
                    .clickable(enabled = !locked) { picked = user.id }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, color = if (locked) p.muted else p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        user.role.name + " · home " + (repo.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: "?") +
                            if (locked) " · LOCKED" else "",
                        color = p.muted,
                        fontSize = 12.sp,
                    )
                }
                if (locked) DpChip(p, "LOCKED", p.dangerBand, p.danger)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        DpRowButtons {
            DpButton(p, "Sign in", {
                repo.currentUserId = picked
                repo.log(repo.users.first { it.id == picked }.name, "Signed in")
                onNext()
            })
        }
    }
}

@Composable
fun DpBranchSelect(repo: DpFakeRepo, p: DpPalette, onNext: () -> Unit) {
    DpSectionTitle(p, "Pick your branch", "sessions filter here")
    for (branch in repo.branches) {
        val sel = branch.id == repo.selectedBranchId
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(branch.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(branch.kind.name.replace('_', ' ') + " · day " + repo.selectedDay.status.name, color = p.muted, fontSize = 12.sp)
                }
                DpButton(p, if (sel) "Selected" else "Open", { repo.selectedBranchId = branch.id }, primary = !sel)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    DpRowButtons { DpButton(p, "Enter " + repo.currentBranch.name, onNext) }
}

@Composable
fun DpHome(repo: DpFakeRepo, p: DpPalette, go: (DpScreen) -> Unit) {
    val user = repo.currentUser
    DpSectionTitle(p, if (repo.persona == DpPersona.PRACTITIONER) "My shift" else "Floor control", "home lens")
    DpCard(p) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user?.name ?: "Guest", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    if (repo.clockedIn) "Clocked in · " + repo.clockInAt else "Not clocked in",
                    color = if (repo.clockedIn) p.ok else p.warn,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            DpChip(p, if (repo.clockedIn) "ON DUTY" else "OFF DUTY", if (repo.clockedIn) p.okBand else p.warnBand, if (repo.clockedIn) p.ok else p.warn)
        }
        Spacer(Modifier.height(10.dp))
        DpRowButtons {
            if (!repo.clockedIn) {
                DpButton(p, "Clock in", {
                    repo.clockedIn = true
                    repo.clockInAt = "08:00 · " + repo.currentBranch.name
                    repo.log(user?.name ?: "?", "Clocked in at " + repo.currentBranch.name)
                })
            } else {
                DpButton(p, "Clock out", {
                    repo.clockedIn = false
                    repo.log(user?.name ?: "?", "Clocked out")
                }, primary = false)
            }
            DpButton(p, "Today's sessions (" + repo.branchSessions.size + ")", { go(DpScreen.SESSIONS) }, primary = false)
        }
    }
    if (repo.persona == DpPersona.PRACTITIONER) {
        DpSectionTitle(p, "My relief", repo.relief.count { it.mine && it.state == DpReliefState.OPEN }.toString() + " open")
        for (r in repo.relief.filter { it.mine }) {
            DpCard(p) {
                DpKeyValue(p, "Shift", r.branchName + " · " + r.date + " · " + r.shift)
                DpKeyValue(p, "Kind / state", r.kind.name + " / " + r.state.name)
                Spacer(Modifier.height(6.dp))
                DpRowButtons {
                    if (r.state == DpReliefState.OPEN && r.kind == DpReliefKind.INVITE) {
                        DpButton(p, "Accept", { r.state = DpReliefState.CLAIMED; repo.log(user?.name ?: "?", "Accepted relief " + r.id) })
                        DpButton(p, "Decline", { r.state = DpReliefState.DECLINED }, primary = false)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    } else {
        DpSectionTitle(p, "Coverage radar", "all branches")
        val open = repo.relief.count { it.state == DpReliefState.OPEN }
        val pending = repo.branchSessions.count { it.status == DpSessionStatus.PENDING }
        DpCard(p) {
            DpKeyValue(p, "Open relief", "$open request(s) across network")
            DpKeyValue(p, "Pending today", "$pending session(s) at " + repo.currentBranch.name)
            DpKeyValue(p, "Day state", repo.selectedDay.status.name + " · 04:00 Manila boundary")
            Spacer(Modifier.height(6.dp))
            DpRowButtons {
                DpButton(p, "Open relief board", { go(DpScreen.RELIEF) })
                DpButton(p, "Review finance", { go(DpScreen.FINANCE) }, primary = false)
            }
        }
    }
}

@Composable
fun DpSessions(repo: DpFakeRepo, p: DpPalette) {
    var selected by remember { mutableStateOf<String?>(null) }
    var compareLens by remember { mutableStateOf(false) }
    val canTreat = repo.capabilities().contains(DpCapability.SESSION_TREAT)
    val canManage = repo.capabilities().contains(DpCapability.SESSION_MANAGE)
    DpSectionTitle(p, "Sessions", repo.branchSessions.size.toString() + " on " + repo.selectedDay.dateLabel)
    if (repo.branchSessions.isEmpty()) DpNote(p, "No sessions on this day at " + repo.currentBranch.name + ". Switch day in the banner.")
    for (s in repo.branchSessions) {
        val open = selected == s.id
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { selected = if (open) null else s.id }) {
                    Text(s.time + " · " + s.clientName, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(s.type + " · " + s.id + if (s.walkIn) " · WALK-IN" else "", color = p.muted, fontSize = 12.sp)
                }
                DpChip(p, s.status.name, s.status.chipBand(p), s.status.chipInk(p))
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                DpKeyValue(p, "Price", "₱" + s.price)
                DpKeyValue(p, "Practitioner", s.practitioner)
                if (s.voided) DpKeyValue(p, "Void", s.voidReason ?: "no reason")
                if (s.walkIn) DpNote(p, "Walk-in rule: walk-ins resolve to COMPLETED only — NO_SHOW / CANCELLED do not apply.")
                Spacer(Modifier.height(6.dp))
                if (s.status == DpSessionStatus.PENDING && !s.voided) {
                    DpRowButtons {
                        if (canTreat) {
                            DpButton(p, "Complete", {
                                repo.updateSession(s.id) { it.copy(status = DpSessionStatus.COMPLETED) }
                                repo.log(repo.currentUser?.name ?: "?", "Completed " + s.id)
                            })
                            if (!s.walkIn) {
                                DpButton(p, "No-show", {
                                    repo.updateSession(s.id) { it.copy(status = DpSessionStatus.NO_SHOW) }
                                    repo.log(repo.currentUser?.name ?: "?", "Marked " + s.id + " NO_SHOW")
                                }, primary = false)
                            }
                        } else {
                            DpNote(p, "Treating needs the Practitioner lens — flip the toggle to complete or no-show this session.")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                DpRowButtons {
                    if (canManage) {
                        if (!s.voided) DpButton(p, "Void…", { selected = s.id + ":void" }, primary = false)
                        else DpButton(p, "Unvoid", {
                            repo.updateSession(s.id) { it.copy(voided = false, voidReason = null) }
                            repo.log(repo.currentUser?.name ?: "?", "Unvoided " + s.id)
                            selected = s.id
                        }, primary = false)
                    } else {
                        DpNote(p, "Void / unvoid needs the Coordinator lens — practitioners treat, coordinators correct.")
                    }
                    DpButton(p, if (compareLens) "Hide other lens" else "Same record, other lens", { compareLens = !compareLens }, primary = false)
                }
                if (selected == s.id + ":void") {
                    var reason by remember(s.id) { mutableStateOf("") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Void reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    DpRowButtons {
                        DpButton(p, "Confirm void", {
                            repo.updateSession(s.id) { it.copy(voided = true, voidReason = reason.ifBlank { "coordinator correction" }) }
                            repo.log(repo.currentUser?.name ?: "?", "Voided " + s.id + ": $reason")
                            selected = s.id
                        }, enabled = true)
                        DpButton(p, "Cancel", { selected = s.id }, primary = false)
                    }
                }
                if (compareLens && selected == s.id) {
                    Spacer(Modifier.height(6.dp))
                    val other = if (repo.persona == DpPersona.PRACTITIONER) DpPersona.COORDINATOR else DpPersona.PRACTITIONER
                    val op = other.palette()
                    Column(
                        Modifier.fillMaxWidth().background(op.bg, RoundedCornerShape(8.dp))
                            .border(1.dp, op.line, RoundedCornerShape(8.dp)).padding(10.dp),
                    ) {
                        Text(
                            "Same " + s.id + " through the " + other.lensName() + " lens",
                            color = op.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            if (other == DpPersona.COORDINATOR) "Client masked · void controls visible · finance-linked." else "Full name · treat actions visible · no money controls.",
                            color = op.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DpClients(repo: DpFakeRepo, p: DpPalette) {
    val masked = repo.persona == DpPersona.COORDINATOR
    var selected by remember { mutableStateOf<String?>(null) }
    DpSectionTitle(p, "Clients", "global registry · " + if (masked) "masked lens" else "full lens")
    DpNote(p, "Clients are global across branches. Rule: a client holds at most one PENDING session — the directory flags violators, never double-books.")
    Spacer(Modifier.height(8.dp))
    for (c in repo.clients) {
        val open = selected == c.id
        val display = if (masked) repo.anonymized(c.name) else c.name
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { selected = if (open) null else c.id }) {
                    Text(display, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(c.id + " · " + if (masked) "contact masked" else c.contact, color = p.muted, fontSize = 12.sp)
                }
                if (c.pendingCount > 1) DpChip(p, "OVERBOOKED", p.dangerBand, p.danger)
                else if (c.pendingCount == 1) DpChip(p, "1 PENDING", p.warnBand, p.warn)
                else DpChip(p, "CLEAR", p.okBand, p.ok)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                val history = repo.sessions.filter { it.clientId == c.id }
                if (history.isEmpty()) DpNote(p, "No sessions on file for " + display + ".")
                for (s in history) DpKeyValue(p, s.id + " · " + s.time, s.type + " / " + s.status.name + " / ₱" + s.price)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DpReliefBoard(repo: DpFakeRepo, p: DpPalette) {
    val canRequest = repo.capabilities().contains(DpCapability.RELIEF_REQUEST)
    val canManage = repo.capabilities().contains(DpCapability.RELIEF_MANAGE)
    var showForm by remember { mutableStateOf(false) }
    DpSectionTitle(p, if (repo.persona == DpPersona.PRACTITIONER) "My relief" else "Relief network", "duty · invite · request")
    for (r in repo.relief) {
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.branchName + " · " + r.date, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(r.shift + " · " + r.note + if (r.mine) " · MINE" else "", color = p.muted, fontSize = 12.sp)
                }
                DpChip(
                    p,
                    r.kind.name + " · " + r.state.name,
                    when (r.state) {
                        DpReliefState.OPEN -> p.warnBand
                        DpReliefState.CLAIMED, DpReliefState.APPROVED -> p.okBand
                        DpReliefState.DECLINED -> p.dangerBand
                    },
                    when (r.state) {
                        DpReliefState.OPEN -> p.warn
                        DpReliefState.CLAIMED, DpReliefState.APPROVED -> p.ok
                        DpReliefState.DECLINED -> p.danger
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            DpRowButtons {
                if (r.state == DpReliefState.OPEN && r.mine && r.kind == DpReliefKind.INVITE && canRequest) {
                    DpButton(p, "Accept", { r.state = DpReliefState.CLAIMED })
                    DpButton(p, "Decline", { r.state = DpReliefState.DECLINED }, primary = false)
                }
                if (r.state == DpReliefState.OPEN && !r.mine && canManage) {
                    DpButton(p, "Assign me", { r.state = DpReliefState.APPROVED; repo.log(repo.currentUser?.name ?: "?", "Covered relief " + r.id) })
                    DpButton(p, "Decline", { r.state = DpReliefState.DECLINED }, primary = false)
                }
            }
            if (!canRequest && !canManage) DpNote(p, "Signed out lens: read-only. Sign in to act on relief.")
        }
        Spacer(Modifier.height(8.dp))
    }
    if (canRequest) {
        DpRowButtons { DpButton(p, if (showForm) "Close request form" else "Request cover…", { showForm = !showForm }, primary = !showForm) }
        if (showForm) {
            var note by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            DpCard(p) {
                Text("New relief request (fake — lands in the list + audit)", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Shift + reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                DpRowButtons {
                    DpButton(p, "Post request", {
                        repo.relief.add(DpRelief("r${repo.relief.size + 1}", repo.currentBranch.name, "Fri Sep 18", "09:00–15:00", DpReliefKind.REQUEST, DpReliefState.OPEN, true, note.ifBlank { "cover needed" }))
                        repo.log(repo.currentUser?.name ?: "?", "Posted relief request")
                        showForm = false
                    })
                }
            }
        }
    } else {
        DpNote(p, "Posting requests needs the Practitioner lens — coordinators approve and assign from this board.")
    }
    Spacer(Modifier.height(4.dp))
    Row { Spacer(Modifier.width(0.dp)) }
}
