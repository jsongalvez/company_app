package com.companyb.companyapp.proto.papercraft

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

// #842 — paper-craft back sheets: taped remittance cut-outs, team, mailbox, audit, profile.

@Composable
fun PcFinance(repo: PaperCraftFakeRepo) {
    Column {
        PcTitle("Sheet 7 — Remittance")
        PcSubtitle("SESSION and PRODUCT cut-outs are taped pages: " +
            "draft → submit → pasted cut-out → peel back within 48 hours.")
        PcGap()
        PcCutoutCard(repo = repo, kind = PcRemitKind.SESSION)
        Spacer(Modifier.height(10.dp))
        PcCutoutCard(repo = repo, kind = PcRemitKind.PRODUCT)
        Spacer(Modifier.height(10.dp))
        PcSheet {
            PcHead("COMMISSION LAYERS")
            PcWashi(color = PcColors.WashiYellow)
            PcNote("Session takings layer per branch day and split evenly over faces " +
                "scissors-down at sold_at. Relief takings come from the relief desk mat.")
        }
    }
}

@Composable
private fun PcCutoutCard(repo: PaperCraftFakeRepo, kind: PcRemitKind) {
    val remit = if (kind == PcRemitKind.SESSION) repo.remitSession else repo.remitProduct
    var amount by remember(kind) { mutableStateOf(remit.draftTotal.toString()) }
    var reason by remember(kind) { mutableStateOf("") }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    val user = repo.currentUser?.name ?: "crafter"
    PcSheet {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            Text("✂ ✂ ✂  R E M I T T A N C E  ✂ ✂ ✂", color = PcColors.Faint, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = PcHand)
            Text("${kind.name} CUT-OUT", color = PcColors.Ink, fontSize = 22.sp,
                fontWeight = FontWeight.Black, fontFamily = PcHand)
            PcSticker(text = remit.state.name,
                color = if (remit.state == PcRemitState.DRAFT) PcColors.StampPast
                else PcColors.StampRemitted)
        }
        PcWashi(color = if (kind == PcRemitKind.SESSION) PcColors.WashiPink else PcColors.WashiTeal)
        when (remit.state) {
            PcRemitState.DRAFT -> {
                TextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Draft sum (₱)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PcButtonRow {
                    PcCutGhost(text = "Keep draft") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Write a numeric draft sum on the scrap."
                        } else {
                            repo.updateRemit(kind, remit.copy(draftTotal = total), user, "DRAFT_SAVE")
                            error = null
                        }
                    }
                    PcCutPrimary(text = "Tape down") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Write a numeric draft sum on the scrap."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = PcRemitState.SUBMITTED,
                                    draftTotal = total,
                                    snapshotId = repo.nextCutId(kind),
                                    snapshotTotal = total,
                                ),
                                user,
                                "SUBMIT",
                            )
                            logInfo("PaperCraftProto", "${kind.name} cut-out taped")
                            error = null
                        }
                    }
                }
                PcNote("Drafts are loose scraps and may overlap. Taping freezes a cut-out.")
            }
            PcRemitState.SUBMITTED -> {
                PcLine("CUT-OUT NO", remit.snapshotId ?: "—")
                PcLine("PASTED SUM", "₱${remit.snapshotTotal}")
                PcLine("CRAFTER", user)
                PcNote("Later layers never rewrite a pasted cut-out. Peeling returns it " +
                    "to Draft — within 48 hours, with a tape note.")
                Spacer(Modifier.height(8.dp))
                TextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Peel note (required)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PcButtonRow {
                    PcCutPrimary(text = "Peel (48h)") {
                        if (reason.isBlank()) {
                            error = "A tape note is needed to peel."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = PcRemitState.DRAFT,
                                    snapshotId = null,
                                    snapshotTotal = null,
                                    undoReason = reason.trim(),
                                ),
                                user,
                                "UNDO",
                            )
                            reason = ""
                            error = null
                        }
                    }
                }
            }
        }
        PcError(error)
    }
}

@Composable
fun PcTeam(repo: PaperCraftFakeRepo) {
    Column {
        PcTitle("Sheet 8 — Hands")
        PcSubtitle("The paper-doll row by home desk, with the standing of each face.")
        PcGap()
        repo.branches.forEachIndexed { index, branch ->
            PcSheet {
                PcWashi(color = washiFor(index))
                PcHead(branch.name.uppercase() + " — " + branch.kind.name)
                repo.users.filter { it.homeBranchId == branch.id }.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, color = PcColors.Ink, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, fontFamily = PcHand)
                            Text(member.role.name, color = PcColors.Faint, fontSize = 12.sp,
                                fontFamily = PcHand)
                        }
                        if (repo.currentUserId == member.id) {
                            PcSticker(text = "YOU", color = PcColors.StampOpen)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PcSheet {
            PcHead("STANDING NOTES")
            PcWashi(color = PcColors.WashiBlue)
            PcNote("ONBOARDING holds no strings. The ACCOUNTANT reads every desk " +
                "but pastes none. Coordinators alone re-stick PAST and REMITTED sheets; " +
                "the MANAGER further keeps the paper-doll register.")
        }
    }
}

@Composable
fun PcMailbox(repo: PaperCraftFakeRepo) {
    val user = repo.currentUser?.name ?: "crafter"
    Column {
        PcTitle("Sheet 9 — Pockets")
        PcSubtitle("Paper pockets — open singly or empty the whole row.")
        PcGap()
        PcButtonRow {
            PcCutGhost(text = "Open all") {
                repo.notifications.forEachIndexed { i, note -> repo.notifications[i] = note.copy(read = true) }
                repo.audit(user, "MAIL_READ_ALL", "notifications")
            }
        }
        Spacer(Modifier.height(6.dp))
        repo.notifications.forEach { note ->
            PcSheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (note.read) "" else "✉ ") + note.title,
                            color = PcColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            fontFamily = PcHand)
                        PcNote(note.body)
                    }
                    if (!note.read) {
                        PcCutGhost(text = "Open") {
                            val i = repo.notifications.indexOfFirst { it.id == note.id }
                            repo.notifications[i] = note.copy(read = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PcAuditList(repo: PaperCraftFakeRepo) {
    Column {
        PcTitle("Sheet 10 — Scraps")
        PcSubtitle("The taped audit trail — every move pastes face / deed / sheet / note.")
        PcGap()
        repo.audits.forEach { entry ->
            PcSheet {
                Text("${entry.whenLabel} · ${entry.who} · ${entry.action}",
                    color = PcColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PcHand)
                PcNote(entry.target + (entry.reason?.let { " — $it" } ?: ""))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PcProfile(repo: PaperCraftFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    Column {
        PcTitle("Sheet 11 — Back Cover")
        PcSubtitle("The peel-off leaf — duty paper, scissors-up, walk away.")
        PcGap()
        PcSheet {
            if (user == null) {
                PcNote("No face is stuck on.")
            } else {
                Text(user.name, color = PcColors.Ink, fontSize = 24.sp,
                    fontWeight = FontWeight.Black, fontFamily = PcHand)
                Text("${user.role.name} · of ${repo.branches.first { it.id == user.homeBranchId }.name}",
                    color = PcColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PcHand)
                PcNote(
                    if (repo.clockedIn) "Scissors down at ${repo.currentBranch.name}." else "Scissors up.",
                )
                PcWashi(color = PcColors.WashiPink)
                PcButtonRow {
                    if (repo.clockedIn) {
                        PcCutGhost(text = "Scissors up") {
                            repo.clockedIn = false
                            repo.audit(user.name, "CLOCK_OUT", repo.currentBranch.name)
                        }
                    }
                    PcCutPrimary(text = "Walk away") {
                        repo.audit(user.name, "LOGOUT", user.name)
                        repo.logout()
                        logInfo("PaperCraftProto", "face walked away")
                        onLogout()
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PcSheet {
            PcNote("The end. The desk keeps every scrap above.")
        }
    }
}
