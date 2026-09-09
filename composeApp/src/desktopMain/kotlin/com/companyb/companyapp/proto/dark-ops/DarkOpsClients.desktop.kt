package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsClients(repo: DarkOpsRepo) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier =
            Modifier.focusRequester(focus).focusable().opsListKeys(onUp = {
                repo.bumpClient(-1)
            }, onDown = { repo.bumpClient(1) }),
    ) {
        OpsSectionHeader("clients", "global registry · j/k moves") {
            OpsBadge(repo.clients.size.toString() + " records", DarkOpsPalette.Cyan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadMd)) {
            Column(Modifier.weight(1f)) {
                OpsPanel(modifier = Modifier.fillMaxWidth()) {
                    ClientRows(repo)
                }
                Spacer(Modifier.height(OpsPadSm))
                OpsNote("at most one PENDING session per client — starred rows hold the live one")
            }
            OpsPanel(modifier = Modifier.width(OpsDetailWidth)) {
                ClientDetail(repo)
            }
        }
    }
}

internal fun DarkOpsRepo.bumpClient(delta: Int) {
    if (clients.isEmpty()) return
    val current = clients.indexOfFirst { it.id == selectedClientId }.takeIf { it >= 0 } ?: 0
    selectedClientId = clients[(current + delta + clients.size) % clients.size].id
}

@Composable
private fun ClientRows(repo: DarkOpsRepo) {
    val pending = repo.pendingClientIds()
    repo.clients.forEach { client ->
        OpsRow(selected = client.id == repo.selectedClientId, onClick = { repo.selectedClientId = client.id }) {
            Text(
                if (client.id ==
                    repo.selectedClientId
                ) {
                    ">"
                } else {
                    " "
                },
                style = DarkOpsType.bodyMedium,
                color = DarkOpsPalette.Phosphor,
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(client.name, style = DarkOpsType.bodyMedium)
                    if (client.id in pending) OpsBadge("* PENDING", DarkOpsPalette.Amber)
                    if (client.anonymized) OpsBadge("ANON", DarkOpsPalette.Violet)
                }
                Text(client.id + " · " + client.gender + " · age " + client.age, style = DarkOpsType.bodySmall)
            }
            Text(client.phone, style = DarkOpsType.bodySmall)
        }
    }
}

@Composable
private fun ClientDetail(repo: DarkOpsRepo) {
    val client = repo.clients.firstOrNull { it.id == repo.selectedClientId }
    if (client == null) {
        OpsEmpty("nothing selected", "j/k to move")
        return
    }
    Text("RECORD // " + client.id, style = DarkOpsType.labelSmall)
    Spacer(Modifier.height(4.dp))
    ClientField("name", client.name)
    ClientField("gender", client.gender)
    ClientField("age", client.age.toString())
    ClientField("phone", client.phone)
    ClientField("pending", if (client.id in repo.pendingClientIds()) "1 live session" else "none")
    Spacer(Modifier.height(OpsPadSm))
    if (client.anonymized) {
        OpsBadge("anonymized — PII nullified, gender/age kept", DarkOpsPalette.Violet)
    } else {
        Button(onClick = { repo.anonymizeClient(client.id) }, colors = greenButton(), enabled = !repo.dayLocked) {
            Text("ANONYMIZE", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("soft-delete + PII nullification, record retained")
    }
}

@Composable
private fun ClientField(
    key: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(key.padEnd(10), style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
        Text(value, style = DarkOpsType.bodyMedium)
    }
}
