package com.companyb.companyapp.proto.kiosktouch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

// #762 — kiosk-touch money + mailbox + audit + profile: giant collect-pay flow.

@Composable
fun KioskFinance(repo: KioskFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Collect pay", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("Draft → submit seals a snapshot → undo within 48h.", color = KioskColors.Muted, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        repo.remittances.forEach { remit ->
            KioskCard {
                Column {
                    Text(
                        if (remit.kind == KioskRemitKind.SESSION) "SESSION — net income" else "PRODUCT — price × qty",
                        color = KioskColors.Ink,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (remit.state == KioskRemitState.DRAFT) "DRAFT — editable"
                        else "SUBMITTED — sealed ${remit.snapshotId}",
                        color = if (remit.state == KioskRemitState.DRAFT) KioskColors.Amber else KioskColors.Green,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "₱${remit.snapshotTotal ?: remit.draftTotal}",
                        color = KioskColors.Ink,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (remit.state == KioskRemitState.DRAFT) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KioskGhostButton(text = "− 500", onClick = { repo.adjustDraft(remit.kind, -500) })
                            KioskGhostButton(text = "+ 500", onClick = { repo.adjustDraft(remit.kind, 500) })
                            KioskRowButton(text = "Submit", onClick = {
                                error = repo.submitRemittance(remit.kind)
                                logInfo("KioskTouch", "submitted ${remit.kind}")
                            })
                        }
                    } else {
                        KioskNote("Snapshot ${remit.snapshotId} is immutable. Later edits never rewrite it.")
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = undoReason,
                            onValueChange = { undoReason = it; error = null },
                            label = { Text("Undo reason — required") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        KioskSecondary(text = "Undo within 48h", onClick = {
                            error = repo.undoRemittance(remit.kind, undoReason)
                            if (error == null) undoReason = ""
                        })
                    }
                    KioskError(error)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        KioskCard {
            Column {
                KioskSection("Commission split")
                Text(
                    "Product commissions pool per branch day, " +
                        "split equally over everyone clocked in at sold_at.",
                    color = KioskColors.Ink,
                    fontSize = 20.sp,
                )
                KioskNote("Manual include/exclude can override. Separate from pay, never remitted.")
            }
        }
    }
}

@Composable
fun KioskMailbox(repo: KioskFakeRepo) {
    val unread = repo.notifications.count { !it.read }
    Column {
        Text("Inbox ($unread new)", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("Relief events name the branch and the day.", color = KioskColors.Muted, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        KioskSecondary(text = "Mark everything read", onClick = { repo.markAllRead() })
        Spacer(Modifier.height(14.dp))
        if (repo.notifications.isEmpty()) {
            KioskCard {
                Column {
                    Text("All caught up.", color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    KioskNote("Read rows stay forever as history.")
                }
            }
        }
        repo.notifications.forEach { note ->
            KioskCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (note.read) "" else "● ") + note.title,
                            color = KioskColors.Ink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(note.body, color = KioskColors.Muted, fontSize = 19.sp)
                    }
                    if (!note.read) {
                        KioskRowGap()
                        KioskRowButton(text = "Read", onClick = { repo.markRead(note.id) })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun KioskAudit(repo: KioskFakeRepo) {
    Column {
        Text("Log", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("Every tap that changes data lands here.", color = KioskColors.Muted, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        repo.audits.forEach { entry ->
            KioskCard {
                Column {
                    Text(
                        "${entry.whenLabel} · ${entry.action}",
                        color = KioskColors.Accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(entry.target, color = KioskColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("by ${entry.who}", color = KioskColors.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (entry.reason != null) KioskNote("Reason: ${entry.reason}")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun KioskProfile(
    repo: KioskFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    Column {
        Text("Me", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        KioskCard {
            Column {
                Text(
                    user?.name ?: "Signed out",
                    color = KioskColors.Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${user?.role?.name ?: "—"} · ${repo.currentBranch.name}",
                    color = KioskColors.Muted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                KioskNote(
                    "Day is ${repo.dayStatus.name}. Boundary 04:00 Asia/Manila — " +
                        "OPEN stays editable until 04:00 next morning, then goes PAST. " +
                        "REMITTED needs a coordinator.",
                )
                KioskGap()
                if (repo.clockedIn) {
                    KioskSecondary(text = "Clock out", onClick = {
                        repo.clockedIn = false
                        repo.audit(user?.name ?: "kiosk", "CLOCK_OUT", repo.currentBranch.name)
                    })
                    KioskGap()
                }
                KioskBigButton(text = "Log out", onClick = {
                    repo.logout()
                    logInfo("KioskTouch", "logged out")
                    onLogout()
                })
            }
        }
    }
}
