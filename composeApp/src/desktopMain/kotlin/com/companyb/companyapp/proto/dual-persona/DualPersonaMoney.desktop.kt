package com.companyb.companyapp.proto.dualpersona

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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

// #781 — dual-persona money + ops: remittance drafts, commission split, team matrix,
// mailbox, audit trail, profile with clock-out and logout.

@Composable
fun DpFinance(repo: DpFakeRepo, p: DpPalette) {
    val canSubmit = repo.capabilities().contains(DpCapability.REMIT_SUBMIT)
    DpSectionTitle(p, "Finance", "SESSION + PRODUCT")
    for (r in repo.remittances) {
        var amount by remember(r.kind) { mutableStateOf(r.draftTotal.toString()) }
        var undoReason by remember(r.kind) { mutableStateOf("") }
        var showUndo by remember(r.kind) { mutableStateOf(false) }
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.kind.name + " remittance", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                DpChip(
                    p,
                    r.state.name,
                    if (r.state == DpRemitState.DRAFT) p.warnBand else p.okBand,
                    if (r.state == DpRemitState.DRAFT) p.warn else p.ok,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (r.state == DpRemitState.DRAFT) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Draft total ₱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                DpRowButtons {
                    DpButton(p, "Save draft", {
                        val i = repo.remittances.indexOfFirst { it.kind == r.kind }
                        repo.remittances[i] = r.copy(draftTotal = amount.toIntOrNull() ?: r.draftTotal)
                        repo.log(repo.currentUser?.name ?: "?", "Saved " + r.kind.name + " draft ₱$amount")
                    }, primary = false)
                    DpButton(p, "Submit + snapshot", {
                        val i = repo.remittances.indexOfFirst { it.kind == r.kind }
                        val total = amount.toIntOrNull() ?: r.draftTotal
                        repo.remittances[i] = r.copy(state = DpRemitState.SUBMITTED, draftTotal = total, snapshotId = "R-" + r.kind.name + "-0907", snapshotTotal = total)
                        repo.log(repo.currentUser?.name ?: "?", "Submitted " + r.kind.name + " snapshot ₱$total")
                    }, enabled = canSubmit)
                }
                if (!canSubmit) {
                    Spacer(Modifier.height(6.dp))
                    DpNote(p, "Submit needs REMIT_SUBMIT — flip to the Coordinator lens. Drafts are editable in both lenses.")
                }
            } else {
                DpKeyValue(p, "Snapshot", (r.snapshotId ?: "—") + " · ₱" + (r.snapshotTotal ?: 0))
                DpKeyValue(p, "Undo window", "48h after submit · Manila time")
                if (r.undoReason != null) DpKeyValue(p, "Undone", r.undoReason ?: "")
                Spacer(Modifier.height(6.dp))
                if (!showUndo) {
                    DpRowButtons { DpButton(p, "Undo within 48h…", { showUndo = true }, primary = false) }
                } else {
                    OutlinedTextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Undo reason (required)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    DpRowButtons {
                        DpButton(p, "Confirm undo", {
                            val i = repo.remittances.indexOfFirst { it.kind == r.kind }
                            repo.remittances[i] = r.copy(state = DpRemitState.DRAFT, snapshotId = null, snapshotTotal = null, undoReason = undoReason.ifBlank { "correction" })
                            repo.log(repo.currentUser?.name ?: "?", "Undid " + r.kind.name + " snapshot: $undoReason")
                        }, enabled = canSubmit)
                        DpButton(p, "Cancel", { showUndo = false }, primary = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    DpSectionTitle(p, "Commission split", "per completed session")
    DpCard(p) {
        DpNote(p, "Split rule: practitioner keeps 70%, branch keeps 30%. Paid on COMPLETED sessions only — voids and no-shows earn nothing.")
        Spacer(Modifier.height(6.dp))
        val done = repo.sessions.filter { it.status == DpSessionStatus.COMPLETED && !it.voided }
        if (done.isEmpty()) DpNote(p, "Nothing completed yet — complete a session to see the split.")
        for (s in done) {
            val cut = (s.price * 70) / 100
            DpKeyValue(p, s.id + " · " + s.practitioner, "₱" + s.price + " → ₱" + cut + " / ₱" + (s.price - cut))
        }
    }
}

@Composable
fun DpTeam(repo: DpFakeRepo, p: DpPalette) {
    DpSectionTitle(p, "Team", "users · roles · caps")
    for (user in repo.users) {
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(user.role.name + " · home " + (repo.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: "?"), color = p.muted, fontSize = 12.sp)
                }
                if (user.role == DpRole.ONBOARDING) DpChip(p, "LOCKED", p.dangerBand, p.danger)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    DpSectionTitle(p, "Capability matrix", "who sees what")
    DpCard(p) {
        for (cap in DpCapability.entries) {
            val prac = DpPersona.PRACTITIONER
            val coord = DpPersona.COORDINATOR
            val hasPrac = cap in setOf(DpCapability.CLOCK, DpCapability.SCHEDULE, DpCapability.SESSION_TREAT, DpCapability.CLIENT_VIEW, DpCapability.RELIEF_REQUEST)
            val hasCoord = cap in setOf(DpCapability.CLOCK, DpCapability.SCHEDULE, DpCapability.SESSION_MANAGE, DpCapability.CLIENT_VIEW, DpCapability.RELIEF_MANAGE, DpCapability.FINANCE_VIEW, DpCapability.REMIT_SUBMIT, DpCapability.TEAM_VIEW, DpCapability.AUDIT_VIEW)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cap.name, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (hasPrac) "● " + prac.lensName() else "○", color = if (hasPrac) p.ok else p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("   ", color = p.muted, fontSize = 12.sp)
                Text(if (hasCoord) "◆ " + coord.lensName() else "○", color = if (hasCoord) p.accent else p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        val you = if (repo.persona == DpPersona.PRACTITIONER) "practitioner" else "coordinator"
        DpNote(p, "You are on the $you lens: " + repo.capabilities().size + " of " + DpCapability.entries.size + " capabilities. Flip the toggle to reshape this nav.")
    }
}

@Composable
fun DpMailbox(repo: DpFakeRepo, p: DpPalette) {
    val unread = repo.notifications.count { !it.read }
    DpSectionTitle(p, "Mailbox", "$unread unread")
    DpRowButtons {
        DpButton(p, "Mark all read", { repo.notifications.forEach { it.read = true } }, primary = false)
    }
    Spacer(Modifier.height(8.dp))
    for (n in repo.notifications) {
        DpCard(p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { n.read = !n.read }) {
                    Text(n.title, color = p.ink, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.SemiBold else FontWeight.ExtraBold)
                    Text(n.body, color = p.muted, fontSize = 12.sp)
                }
                DpChip(p, if (n.read) "READ" else "UNREAD", if (n.read) p.surface else p.infoBand, if (n.read) p.muted else p.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DpAuditList(repo: DpFakeRepo, p: DpPalette) {
    DpSectionTitle(p, "Audit log", repo.audit.size.toString() + " events")
    DpNote(p, "Every tap that matters lands here — sign-ins, clock events, treats, voids, drafts, relief. Newest first.")
    Spacer(Modifier.height(8.dp))
    for (a in repo.audit) {
        DpCard(p) {
            Text(a.action, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(a.time + " · " + a.actor + " · " + a.id, color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun DpProfile(repo: DpFakeRepo, p: DpPalette, onLogout: () -> Unit) {
    val user = repo.currentUser
    DpSectionTitle(p, "Profile", repo.persona.lensName() + " lens")
    DpCard(p) {
        Text(user?.name ?: "Signed out", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            (user?.role?.name ?: "—") + " · home " + (repo.branches.firstOrNull { it.id == user?.homeBranchId }?.name ?: "—"),
            color = p.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        DpKeyValue(p, "Lens", repo.persona.lensName() + " · " + repo.capabilities().size + "/" + DpCapability.entries.size + " caps")
        DpKeyValue(p, "Shift", if (repo.clockedIn) "clocked in · " + repo.clockInAt else "off duty")
        DpKeyValue(p, "Branch day", repo.selectedDay.dateLabel + " · " + repo.selectedDay.status.name)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().background(p.infoBand, RoundedCornerShape(8.dp)).padding(10.dp),
        ) {
            Text("Switch lens", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Same account, reshaped powers. Try Finance from each side.", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            DpRowButtons {
                DpButton(p, "Flip to " + (if (repo.persona == DpPersona.PRACTITIONER) "Coordinator" else "Practitioner"), {
                    repo.persona = if (repo.persona == DpPersona.PRACTITIONER) DpPersona.COORDINATOR else DpPersona.PRACTITIONER
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        DpRowButtons {
            if (repo.clockedIn) DpButton(p, "Clock out", { repo.clockedIn = false; repo.log(user?.name ?: "?", "Clocked out") }, primary = false)
            DpButton(p, "Log out", {
                repo.log(user?.name ?: "?", "Signed out")
                repo.currentUserId = null
                repo.clockedIn = false
                onLogout()
            }, primary = false)
        }
    }
}
