package com.companyb.companyapp.proto.weekreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #850 — finance desk: SESSION/PRODUCT remittance draft/submit/snapshot/undo-48h,
// commission split rule, pay envelopes. Seeds desk: next-week packets. Fake data only.

@Composable
fun WrFinance(repo: WeekReviewFakeRepo) {
    var undoing by remember { mutableStateOf<WrRemittance?>(null) }
    var undoReason by remember { mutableStateOf("") }
    var undoError by remember { mutableStateOf<String?>(null) }

    Column {
        SectionFlag("Finance desk · close the week")
        Spacer(Modifier.height(8.dp))
        NoteCard(
            "Remittance runs per branch day: draft while the day is OPEN, submit to seal an " +
                "immutable snapshot, undo only with a reason inside the 48-hour window.",
        )
        Spacer(Modifier.height(8.dp))
        repo.remittances.forEach { r ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(WrColors.Paper).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.kind.label} remittance · ${r.id}",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = WrColors.Ink,
                        )
                        Text(r.branchDay, fontSize = 12.sp, color = WrColors.Muted, fontStyle = FontStyle.Italic)
                    }
                    Text(peso(r.amount), fontWeight = FontWeight.Black, fontSize = 20.sp, color = WrColors.Ink)
                    Spacer(Modifier.padding(4.dp))
                    StatusStamp(
                        r.state.label,
                        when (r.state) {
                            WrRemitState.DRAFT -> WrColors.Amber
                            WrRemitState.SUBMITTED -> WrColors.Moss
                            WrRemitState.UNDONE -> WrColors.StampRed
                        },
                    )
                }
                if (r.snapshotId != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Snapshot sealed: ${r.snapshotId} — immutable unless undone with reason.", fontSize = 12.sp, color = WrColors.Moss, fontWeight = FontWeight.Bold)
                }
                if (r.undoReason != null) {
                    Text("Undone: ${r.undoReason} (48h window)", fontSize = 12.sp, color = WrColors.StampRed, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (r.state == WrRemitState.DRAFT) {
                        Button(
                            onClick = { repo.submitRemittance(r.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = WrColors.Moss),
                        ) {
                            Text("Submit & seal snapshot")
                        }
                    }
                    if (r.state == WrRemitState.SUBMITTED) {
                        Text(
                            "Undo with reason",
                            color = WrColors.StampRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { undoing = r; undoReason = ""; undoError = null }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionFlag("Commission split")
        Spacer(Modifier.height(8.dp))
        NoteCard(
            "Commission pool is per branch day and split equally over staff clocked in at sold_at. " +
                "Envelopes below are the Friday payout of that split — mark each as cash changes hands.",
        )
        Spacer(Modifier.height(8.dp))
        val pool = repo.payouts.sumOf { it.share }
        val paid = repo.payouts.filter { it.paid }.sumOf { it.share }
        Text(
            "${peso(paid)} of ${peso(pool)} in hands · ${repo.payouts.count { it.paid }}/${repo.payouts.size} envelopes sealed",
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = WrColors.Gold,
        )
        Spacer(Modifier.height(6.dp))
        repo.payouts.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (p.paid) WrColors.GoldWash else WrColors.Paper)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.staff, fontWeight = FontWeight.Black, fontSize = 14.sp, color = WrColors.Ink)
                    Text(
                        "${p.role} · ${p.completed} completions this week",
                        fontSize = 12.sp,
                        color = WrColors.Muted,
                        fontStyle = FontStyle.Italic,
                    )
                }
                Text(peso(p.share), fontWeight = FontWeight.Black, fontSize = 18.sp, color = WrColors.Gold)
                Spacer(Modifier.padding(4.dp))
                if (p.paid) {
                    StatusStamp("paid", WrColors.Moss)
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        "Reopen",
                        color = WrColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.markPaid(p.id) }.padding(4.dp),
                    )
                } else {
                    Text(
                        "Mark paid",
                        color = WrColors.Moss,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.markPaid(p.id) }.padding(4.dp),
                    )
                }
            }
        }
    }

    if (undoing != null) {
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text("Undo ${undoing!!.id}?") },
            text = {
                Column {
                    Text("Snapshot ${undoing!!.snapshotId} reopens as UNDONE. State the reason — the 48-hour window is the rule.", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    TextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Undo reason") }, singleLine = true)
                    if (undoError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(undoError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val err = repo.undoRemittance(undoing!!.id, undoReason)
                        if (err == null) undoing = null else undoError = err
                    },
                ) {
                    Text("Undo snapshot", color = WrColors.StampRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { undoing = null }) { Text("Keep sealed") }
            },
        )
    }
}

@Composable
fun WrSeeds(repo: WeekReviewFakeRepo) {
    var planting by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("Mon") }
    var plantError by remember { mutableStateOf<String?>(null) }
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column {
        SectionFlag("Seed packets · next week Sep 14–19")
        Spacer(Modifier.height(8.dp))
        Text(
            "The ritual ends looking forward: every miss on the front page should become a seed. " +
                "Tap a packet to mark it planted.",
            fontSize = 14.sp,
            color = WrColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        days.forEach { d ->
            val packets = repo.seeds.filter { it.day == d }
            if (packets.isNotEmpty()) {
                Text(d.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = WrColors.Moss)
                Spacer(Modifier.height(4.dp))
                packets.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (s.planted) WrColors.MossWash else WrColors.Paper)
                            .clickable { repo.toggleSeed(s.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = WrColors.Ink,
                            )
                            Text("Owner: ${s.owner}", fontSize = 12.sp, color = WrColors.Muted, fontStyle = FontStyle.Italic)
                        }
                        StatusStamp(if (s.planted) "planted" else "to plant", if (s.planted) WrColors.Moss else WrColors.Amber)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { planting = true; title = ""; owner = ""; plantError = null },
            colors = ButtonDefaults.buttonColors(containerColor = WrColors.Moss),
        ) {
            Text("Pack a new seed")
        }
    }

    if (planting) {
        AlertDialog(
            onDismissRequest = { planting = false },
            title = { Text("New seed packet") },
            text = {
                Column {
                    TextField(value = title, onValueChange = { title = it }, label = { Text("What must happen next week?") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    TextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { d ->
                            Text(
                                d,
                                fontWeight = if (day == d) FontWeight.Black else FontWeight.Medium,
                                color = if (day == d) WrColors.Moss else WrColors.Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { day = d }.padding(4.dp),
                            )
                        }
                    }
                    if (plantError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(plantError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val err = repo.addSeed(title, owner, day)
                        if (err == null) planting = false else plantError = err
                    },
                ) {
                    Text("Pack it")
                }
            },
            dismissButton = {
                TextButton(onClick = { planting = false }) { Text("Cancel") }
            },
        )
    }
}
