package com.companyb.companyapp.proto.ledgerbook

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

// #835 — ledger-book back folios: stamped remittance pages, team, mailbox, audit, profile.

@Composable
fun LbFinance(repo: LedgerBookFakeRepo) {
    Column {
        LbChapter("Chapter VII — Remittance")
        LbEpigraph("SESSION and PRODUCT folios are stamped pages: " +
            "draft → submit → sealed folio → undo within 48 hours.")
        LbGap()
        LbFolioCard(repo = repo, kind = LbRemitKind.SESSION)
        Spacer(Modifier.height(10.dp))
        LbFolioCard(repo = repo, kind = LbRemitKind.PRODUCT)
        Spacer(Modifier.height(10.dp))
        LbPage {
            LbHead("COMMISSION APPORTION")
            LbRuled()
            LbMarginalia("Session takings pool per branch day and divide evenly over hands " +
                "pen-down at sold_at. Relief takings are drawn from the relief house till.")
        }
    }
}

@Composable
private fun LbFolioCard(repo: LedgerBookFakeRepo, kind: LbRemitKind) {
    val remit = if (kind == LbRemitKind.SESSION) repo.remitSession else repo.remitProduct
    var amount by remember(kind) { mutableStateOf(remit.draftTotal.toString()) }
    var reason by remember(kind) { mutableStateOf("") }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    val user = repo.currentUser?.name ?: "amanuensis"
    LbPage {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            Text("❦ ❦ ❦  R E M I T T A N C E  ❦ ❦ ❦", color = LbColors.Faint, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = LbSerif)
            Text("${kind.name} FOLIO", color = LbColors.Ink, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, fontFamily = LbSerif)
            LbStamp(text = remit.state.name,
                color = if (remit.state == LbRemitState.DRAFT) LbColors.StampPast
                else LbColors.StampRemitted)
        }
        LbRuled()
        when (remit.state) {
            LbRemitState.DRAFT -> {
                TextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Draft sum (₱)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LbButtonRow {
                    LbPencil(text = "Keep draft") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Ink a numeric draft sum."
                        } else {
                            repo.updateRemit(kind, remit.copy(draftTotal = total), user, "DRAFT_SAVE")
                            error = null
                        }
                    }
                    LbQuill(text = "Seal + stamp") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Ink a numeric draft sum."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = LbRemitState.SUBMITTED,
                                    draftTotal = total,
                                    snapshotId = repo.nextFolioId(kind),
                                    snapshotTotal = total,
                                ),
                                user,
                                "SUBMIT",
                            )
                            logInfo("LedgerBookProto", "${kind.name} folio stamped")
                            error = null
                        }
                    }
                }
                LbMarginalia("Drafts are pencil and may overlap. Sealing freezes an immutable folio.")
            }
            LbRemitState.SUBMITTED -> {
                LbEntry("FOLIO NO", remit.snapshotId ?: "—")
                LbEntry("SEALED SUM", "₱${remit.snapshotTotal}")
                LbEntry("AMANUENSIS", user)
                LbRuled()
                LbMarginalia("Later amendments never rewrite a sealed folio. Undo tears the folio " +
                    "and returns it to Draft — within 48 hours, with a writ.")
                Spacer(Modifier.height(8.dp))
                TextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Undo writ (required)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LbButtonRow {
                    LbQuill(text = "Undo (48h)") {
                        if (reason.isBlank()) {
                            error = "A writ is required to undo."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = LbRemitState.DRAFT,
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
        LbErratum(error)
    }
}

@Composable
fun LbTeam(repo: LedgerBookFakeRepo) {
    Column {
        LbChapter("Chapter VIII — Company")
        LbEpigraph("The roster by home house, with the standing of each hand.")
        LbGap()
        repo.branches.forEach { branch ->
            LbPage {
                LbHead(branch.name.uppercase() + " — " + branch.kind.name)
                LbRuled()
                repo.users.filter { it.homeBranchId == branch.id }.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, color = LbColors.Ink, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                            Text(member.role.name, color = LbColors.Faint, fontSize = 12.sp,
                                fontFamily = LbSerif)
                        }
                        if (repo.currentUserId == member.id) {
                            LbStamp(text = "THOU", color = LbColors.StampOpen)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        LbPage {
            LbHead("STANDING NOTES")
            LbRuled()
            LbMarginalia("ONBOARDING holds no capabilities. The ACCOUNTANT reads every house " +
                "but amends none. Coordinators alone amend PAST and REMITTED folios; " +
                "the MANAGER further keeps the register of hands.")
        }
    }
}

@Composable
fun LbMailbox(repo: LedgerBookFakeRepo) {
    val user = repo.currentUser?.name ?: "amanuensis"
    Column {
        LbChapter("Chapter IX — Post")
        LbEpigraph("Pigeon-holes — read singly or clear the whole tray.")
        LbGap()
        LbButtonRow {
            LbPencil(text = "Read all") {
                repo.notifications.forEachIndexed { i, note -> repo.notifications[i] = note.copy(read = true) }
                repo.audit(user, "MAIL_READ_ALL", "notifications")
            }
        }
        Spacer(Modifier.height(6.dp))
        repo.notifications.forEach { note ->
            LbPage {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (note.read) "" else "✉ ") + note.title,
                            color = LbColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            fontFamily = LbSerif)
                        LbMarginalia(note.body)
                    }
                    if (!note.read) {
                        LbPencil(text = "Read") {
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
fun LbAuditList(repo: LedgerBookFakeRepo) {
    Column {
        LbChapter("Chapter X — Marginalia")
        LbEpigraph("The bound audit — every amendment prepends hand / deed / folio / writ.")
        LbGap()
        repo.audits.forEach { entry ->
            LbPage {
                Text("${entry.whenLabel} · ${entry.who} · ${entry.action}",
                    color = LbColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = LbSerif)
                LbMarginalia(entry.target + (entry.reason?.let { " — $it" } ?: ""))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun LbProfile(repo: LedgerBookFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    Column {
        LbChapter("Chapter XI — Colophon")
        LbEpigraph("The signing-off leaf — duty slate, pen-up, departure.")
        LbGap()
        LbPage {
            if (user == null) {
                LbMarginalia("No hand hath signed.")
            } else {
                Text(user.name, color = LbColors.Ink, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                Text("${user.role.name} · of ${repo.branches.first { it.id == user.homeBranchId }.name}",
                    color = LbColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = LbSerif)
                LbMarginalia(
                    if (repo.clockedIn) "Pen down at ${repo.currentBranch.name}." else "Pen up.",
                )
                LbRuled()
                LbButtonRow {
                    if (repo.clockedIn) {
                        LbPencil(text = "Pen up") {
                            repo.clockedIn = false
                            repo.audit(user.name, "CLOCK_OUT", repo.currentBranch.name)
                        }
                    }
                    LbQuill(text = "Depart") {
                        repo.audit(user.name, "LOGOUT", user.name)
                        repo.logout()
                        logInfo("LedgerBookProto", "hand departed")
                        onLogout()
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LbPage {
            LbMarginalia("Finis. The book remembers every stroke above.")
        }
    }
}
