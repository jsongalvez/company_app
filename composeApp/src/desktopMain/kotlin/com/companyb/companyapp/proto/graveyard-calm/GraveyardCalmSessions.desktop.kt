package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.unit.dp

@Composable
internal fun GraveyardCalmSessions(repo: CalmRepo) {
    CalmSection("quiet queue", "tap a card — the guest never hears a thing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        CalmRowButton("ALL", repo.sessionFilter == null) { repo.sessionFilter = null }
        CalmSessionStatus.entries.forEach { st ->
            CalmRowButton(st.label, repo.sessionFilter == st.label) { repo.sessionFilter = st.label }
        }
        CalmRowButton("VOIDED", repo.sessionFilter == "VOIDED") { repo.sessionFilter = "VOIDED" }
        Spacer(Modifier.weight(1f))
        CalmQuiet("+ book softly") { repo.createOpen = true }
    }
    Spacer(Modifier.height(8.dp))
    val visible =
        repo.sessions.filter { s ->
            when (repo.sessionFilter) {
                null -> true
                "VOIDED" -> s.voided
                else -> s.status.label == repo.sessionFilter && !s.voided
            }
        }
    CalmTwoCol(
        left = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (visible.isEmpty()) {
                    CalmCard { CalmEmpty("the queue is asleep") }
                }
                visible.forEach { s ->
                    val selected = repo.selectedSessionId == s.id
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { repo.selectedSessionId = s.id }
                            .background(
                                if (selected) GraveyardCalmPalette.CardSoft else GraveyardCalmPalette.Card,
                                RoundedCornerShape(10.dp),
                            ).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.time, style = GraveyardCalmType.labelMedium, color = GraveyardCalmPalette.Lamp)
                                Spacer(Modifier.width(6.dp))
                                Text(s.clientName, style = GraveyardCalmType.bodyLarge, color = GraveyardCalmPalette.Ink)
                                Spacer(Modifier.weight(1f))
                                if (s.walkIn) CalmTag("walk-in", GraveyardCalmPalette.Moon)
                                if (s.voided) CalmTag("VOIDED", GraveyardCalmPalette.Ember)
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.service, style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
                                Spacer(Modifier.weight(1f))
                                CalmStatusTag(s.status)
                            }
                        }
                    }
                }
            }
        },
        right = {
            val s = repo.selectedSessionId?.let { repo.sessionById(it) }
            CalmCard {
                if (s == null) {
                    CalmSection("detail")
                    CalmEmpty("choose a card on the left")
                } else {
                    CalmSection("detail · ${s.id}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.clientName, style = GraveyardCalmType.titleLarge, color = GraveyardCalmPalette.Ink)
                        Spacer(Modifier.weight(1f))
                        CalmStatusTag(s.status)
                    }
                    Text(
                        "${s.service} · ${s.time} · ₱${s.price} · ${s.practitioner}",
                        style = GraveyardCalmType.bodySmall,
                        color = GraveyardCalmPalette.Dim,
                    )
                    if (s.walkIn) {
                        Spacer(Modifier.height(4.dp))
                        CalmTag("walk-in", GraveyardCalmPalette.Moon)
                    }
                    if (s.voided) {
                        Spacer(Modifier.height(6.dp))
                        CalmNote("Voided: ${s.voidReason}")
                        Spacer(Modifier.height(6.dp))
                        CalmGhost("unvoid — restore to queue") { repo.unvoidSession(s.id) }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CalmQuiet("complete") { repo.setStatus(s.id, CalmSessionStatus.COMPLETED) }
                            CalmQuiet("no-show") { repo.setStatus(s.id, CalmSessionStatus.NO_SHOW) }
                            CalmQuiet("cancel") { repo.setStatus(s.id, CalmSessionStatus.CANCELLED) }
                        }
                        Spacer(Modifier.height(6.dp))
                        CalmGhost("void with reason…") { repo.voidDialogFor = s.id }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            CalmNote("House rule: walk-ins never take NO_SHOW or CANCELLED — there was no booking to miss. Void with reason covers mistakes.")
        },
    )
    if (repo.voidDialogFor != null) {
        Spacer(Modifier.height(8.dp))
        CalmCard {
            CalmSection("void ${repo.voidDialogFor} — why?")
            TextField(
                value = repo.voidReason,
                onValueChange = { repo.voidReason = it },
                placeholder = { Text("e.g. double-booked by mistake…", style = GraveyardCalmType.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = GraveyardCalmPalette.CardSoft,
                        unfocusedContainerColor = GraveyardCalmPalette.CardSoft,
                        focusedTextColor = GraveyardCalmPalette.Ink,
                        unfocusedTextColor = GraveyardCalmPalette.Ink,
                    ),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalmPrimary("void it") { repo.voidSession(repo.voidDialogFor!!) }
                CalmGhost("keep it") {
                    repo.voidDialogFor = null
                    repo.voidReason = ""
                }
            }
        }
    }
    if (repo.createOpen) {
        Spacer(Modifier.height(8.dp))
        CalmCreateDialog(repo)
    }
}

@Composable
private fun CalmCreateDialog(repo: CalmRepo) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("500") }
    var time by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    CalmCard {
        CalmSection("book softly", "PENDING by default · one PENDING per client at most")
        CalmField("guest name", name) { name = it }
        CalmField("service", service) { service = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CalmField("price ₱", price) { price = it } }
            Box(Modifier.weight(1f)) { CalmField("time (blank = now)", time) { time = it } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = walkIn,
                onCheckedChange = { walkIn = it },
                colors = CheckboxDefaults.colors(checkedColor = GraveyardCalmPalette.Lamp),
            )
            Text("walk-in (no NO_SHOW / CANCELLED later)", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalmPrimary("pin to queue") { repo.addSession(name, service, price.toIntOrNull() ?: 0, walkIn, time) }
            CalmGhost("never mind") { repo.createOpen = false }
        }
    }
}

@Composable
private fun CalmField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = GraveyardCalmPalette.CardSoft,
                    unfocusedContainerColor = GraveyardCalmPalette.CardSoft,
                    focusedTextColor = GraveyardCalmPalette.Ink,
                    unfocusedTextColor = GraveyardCalmPalette.Ink,
                ),
        )
        Spacer(Modifier.height(4.dp))
    }
}
