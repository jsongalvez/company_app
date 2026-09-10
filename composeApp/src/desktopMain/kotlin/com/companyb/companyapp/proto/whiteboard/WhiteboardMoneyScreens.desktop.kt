package com.companyb.companyapp.proto.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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

// #844 — whiteboard money screens: global client cards, SESSION+PRODUCT remittance, commission note.

@Composable
fun WhiteboardClients(repo: WhiteboardFakeRepo) {
    BoardPanel {
        MarkerTitle("Client wall", BoardColors.MarkerGreen)
        Spacer(Modifier.height(6.dp))
        BoardNote("Clients are global — one wall for every branch. At most one PENDING session per client at a time.")
    }
    repo.clients.forEach { client ->
        val pending = repo.pendingFor(client.id)
        MagnetCard(magnet = if (client.anonymized) BoardColors.CardLine else BoardColors.MagnetTeal) {
            Text(
                if (client.anonymized) client.name else client.name,
                color = BoardColors.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            if (client.anonymized) {
                BoardNote("Anonymized view: PII nullified — gender ${client.gender} and age ${client.age} kept for reporting.")
            } else {
                BoardNote("${client.gender} · age ${client.age} · $pending PENDING session(s)")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!client.anonymized) {
                    BoardGhost("Anonymize") { repo.anonymize(client) }
                } else {
                    BoardChip("anonymized", BoardColors.InkSoft)
                }
                if (pending > 1) {
                    BoardChip("rule break: 2 PENDING", BoardColors.MarkerRed)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun WhiteboardMoney(repo: WhiteboardFakeRepo) {
    BoardPanel {
        MarkerTitle("Remittance board", BoardColors.MarkerBlue)
        Spacer(Modifier.height(6.dp))
        BoardNote("Two independent flows: SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity). Submission freezes an immutable snapshot of the P&L.")
    }
    repo.remittances.forEach { remit ->
        WhiteboardRemitCard(repo = repo, remit = remit)
    }
    BoardPanel(tape = BoardColors.MagnetPink) {
        MarkerTitle("Commission split", BoardColors.MarkerPurple)
        Spacer(Modifier.height(6.dp))
        Text(
            "Each COMPLETED session splits by the posted table: practitioner share lands in compensation, the branch drawer keeps the remainder. Relief pay comes out of the relief branch drawer — never the home one.",
            color = BoardColors.Ink,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardChip("Makati split 60/40", BoardColors.MarkerPurple)
            BoardChip("tour split 70/30", BoardColors.MarkerOrange)
            BoardChip("mission: no split", BoardColors.MarkerGreen)
        }
    }
}

@Composable
private fun WhiteboardRemitCard(repo: WhiteboardFakeRepo, remit: BoardRemittance) {
    var draft by remember(remit.draftTotal) { mutableStateOf(remit.draftTotal.toString()) }
    var reason by remember { mutableStateOf("") }
    var showUndo by remember { mutableStateOf(false) }
    BoardPanel(tape = if (remit.kind == BoardRemitKind.SESSION) BoardColors.MagnetSky else BoardColors.MagnetYellow) {
        BoardSectionRow("${remit.kind} flow") {
            BoardChip(
                if (remit.state == BoardRemitState.DRAFT) "DRAFT" else "SUBMITTED",
                if (remit.state == BoardRemitState.DRAFT) BoardColors.MarkerOrange else BoardColors.MarkerBlue,
            )
        }
        if (remit.state == BoardRemitState.DRAFT) {
            BoardNote("Drafts are unconstrained and can overlap. Scribble freely.")
            Spacer(Modifier.height(8.dp))
            TextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Draft total ₱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            BoardCta("Submit + freeze snapshot") {
                remit.draftTotal = draft.toIntOrNull() ?: remit.draftTotal
                repo.submitRemittance(remit)
            }
            if (remit.undoReason != null) {
                Spacer(Modifier.height(6.dp))
                BoardNote("Reopened earlier: ${remit.undoReason}")
            }
        } else {
            Text(
                "Snapshot ${remit.snapshotId} · ₱${remit.snapshotTotal} frozen ${remit.submittedAt ?: ""}.",
                color = BoardColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            BoardNote("Later edits to sessions, expenses, or compensation do not rewrite this snapshot.")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = remit.windowElapsed,
                    onCheckedChange = {
                        remit.windowElapsed = it
                        repo.audit("REMIT_WINDOW", "${remit.kind} ${remit.snapshotId}", if (it) "48h elapsed" else "within window")
                    },
                )
                Spacer(Modifier.width(6.dp))
                Text("Simulate 48h elapsed (snapshot permanent)", color = BoardColors.Ink, fontSize = 13.sp)
            }
            if (!remit.windowElapsed) {
                Spacer(Modifier.height(6.dp))
                BoardLink(if (showUndo) "keep snapshot" else "undo within 48h") { showUndo = !showUndo }
                if (showUndo) {
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Undo reason (recorded)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    BoardCta("Undo to draft") {
                        repo.undoRemittance(remit, reason)
                        draft = remit.draftTotal.toString()
                        reason = ""
                        showUndo = false
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                BoardChip("window closed — permanent", BoardColors.MarkerRed)
            }
        }
    }
}
