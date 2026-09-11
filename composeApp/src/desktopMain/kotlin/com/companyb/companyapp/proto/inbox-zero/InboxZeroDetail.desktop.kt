package com.companyb.companyapp.proto.inboxzero

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #856 — inbox-zero session + client detail stages. Triage verdicts land here
// from inbox rows; every control below is also reachable without the inbox.

@Composable
fun IzSessionDetail(repo: IzFakeRepo, s: IzSession, onOpenClient: (String) -> Unit) {
    var reason by remember(s.id) { mutableStateOf("") }
    var refused by remember(s.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.id} · ${s.service}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
            Spacer(Modifier.weight(1f))
            IzPill(s.status.label, s.status.tone())
            Spacer(Modifier.width(8.dp))
            if (s.walkIn) IzPill("walk-in", IzTone.BLUE)
            if (s.voided) IzPill("voided", IzTone.RED)
        }
        Spacer(Modifier.height(6.dp))
        IzCard {
            IzKey("Client", s.client)
            IzKey("Branch", s.branch)
            IzKey("Practitioner", s.practitioner)
            IzKey("Amount", izPeso(s.amount))
            IzKey("Type", if (s.walkIn) "Walk-in" else "Booked")
            if (s.voided) IzKey("Void reason", s.voidReason ?: "—")
        }
        Spacer(Modifier.height(10.dp))
        if (s.status == IzSessionStatus.PENDING) {
            Text("Triage verdict", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
            Spacer(Modifier.height(6.dp))
            Row {
                IzChip("Complete", onClick = { repo.transitionSession(s, IzSessionStatus.COMPLETED); refused = null }, primary = true)
                Spacer(Modifier.width(8.dp))
                IzChip("No-show", onClick = {
                    refused = if (!repo.transitionSession(s, IzSessionStatus.NO_SHOW)) {
                        "House rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED."
                    } else {
                        null
                    }
                })
                Spacer(Modifier.width(8.dp))
                IzChip("Cancel", onClick = {
                    refused = if (!repo.transitionSession(s, IzSessionStatus.CANCELLED)) {
                        "House rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED."
                    } else {
                        null
                    }
                })
            }
            if (refused != null) {
                Spacer(Modifier.height(6.dp))
                Text(refused!!, fontSize = 12.sp, color = IzColors.Brick)
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("Void / unvoid (reason required)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            if (!s.voided) {
                IzChip("Void session", onClick = { repo.voidSession(s, reason); reason = "" })
            } else {
                IzChip("Unvoid session", onClick = { repo.unvoidSession(s, reason); reason = "" }, primary = true)
            }
            Spacer(Modifier.width(8.dp))
            IzChip("Open client", onClick = { onOpenClient(s.clientId) })
        }
    }
}

@Composable
fun IzClientDetail(repo: IzFakeRepo, c: IzClient, onOpenSession: (String) -> Unit) {
    var service by remember(c.id) { mutableStateOf("PT Rehab 45m") }
    var blocked by remember(c.id) { mutableStateOf<String?>(null) }
    val shown = if (c.anonymized) "Client ${c.id}" else c.name
    val pending = repo.pendingCount(c.id)
    val mine = repo.sessions.filter { it.clientId == c.id }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(shown, fontSize = 20.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
            Spacer(Modifier.weight(1f))
            IzPill(if (pending > 0) "1 PENDING" else "no pending", if (pending > 0) IzTone.ORANGE else IzTone.GREEN)
            Spacer(Modifier.width(8.dp))
            if (c.anonymized) IzPill("anonymized", IzTone.GREY)
        }
        Spacer(Modifier.height(6.dp))
        IzCard {
            IzKey("Record", "${c.id} · global across all branches")
            IzKey("Gender / age", "${c.gender} · ${c.age} (kept for reporting even when anonymized)")
            Text(
                "House rule: a client holds at most one PENDING session at a time. Walk-ins bypass the check.",
                fontSize = 11.sp,
                color = IzColors.Faint,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            if (!c.anonymized) {
                IzChip("Anonymize", onClick = { repo.anonymize(c) })
            } else {
                IzChip("Reveal", onClick = { repo.reveal(c) }, primary = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Book at ${repo.currentBranch.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = service,
            onValueChange = { service = it; blocked = null },
            label = { Text("Service") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            IzChip("Book session", onClick = {
                blocked = if (repo.bookSession(c.id, service, false) == null) {
                    "Blocked: this client already holds a PENDING session."
                } else {
                    null
                }
            }, primary = true)
            Spacer(Modifier.width(8.dp))
            IzChip("Book walk-in", onClick = { repo.bookSession(c.id, service, true); blocked = null })
        }
        if (blocked != null) {
            Spacer(Modifier.height(6.dp))
            Text(blocked!!, fontSize = 12.sp, color = IzColors.Brick)
        }
        Spacer(Modifier.height(10.dp))
        Text("Sessions (${mine.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IzColors.Ink)
        Spacer(Modifier.height(6.dp))
        mine.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.id} · ${s.service} · ${s.branch}", fontSize = 12.sp, color = IzColors.Ink, modifier = Modifier.weight(1f))
                IzPill(s.status.label, s.status.tone())
                Spacer(Modifier.width(8.dp))
                IzChip("Open", onClick = { onOpenSession(s.id) })
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
