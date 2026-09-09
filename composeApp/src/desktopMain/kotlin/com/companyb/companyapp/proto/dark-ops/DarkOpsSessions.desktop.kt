package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

private val SessionFilters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED")

@Composable
internal fun DarkOpsSessions(repo: DarkOpsRepo) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier =
            Modifier.focusRequester(focus).focusable().opsListKeys(onUp = {
                repo.bumpSession(-1)
            }, onDown = { repo.bumpSession(1) }),
    ) {
        OpsSectionHeader("sessions", repo.visibleSessions().size.toString() + " rows · j/k moves") {
            SessionFilterStrip(repo)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadMd)) {
            Column(Modifier.weight(1f)) {
                OpsPanel(modifier = Modifier.fillMaxWidth()) {
                    SessionRows(repo)
                }
                Spacer(Modifier.height(OpsPadSm))
                OpsNote("walk-in sessions cannot be NO_SHOW or CANCELLED")
            }
            OpsPanel(modifier = Modifier.width(OpsDetailWidth)) {
                SessionDetail(repo)
            }
        }
        Spacer(Modifier.height(OpsPadSm))
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadSm)) {
            Button(onClick = { repo.createOpen = true }, colors = greenButton(), enabled = !repo.dayLocked) {
                Text("+ book session", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
            if (repo.dayLocked) OpsBadge("day covered — coordinator edit only", DarkOpsPalette.Amber)
        }
    }
    if (repo.voidTargetId != null) VoidReasonDialog(repo)
    if (repo.createOpen) CreateSessionDialog(repo)
}

internal fun DarkOpsRepo.visibleSessions(): List<OpsSession> =
    sessions.filter {
        when (sessionFilter) {
            "VOIDED" -> it.voided
            "ALL" -> true
            else -> it.status.name == sessionFilter && !it.voided
        }
    }

internal fun DarkOpsRepo.bumpSession(delta: Int) {
    val rows = visibleSessions()
    if (rows.isEmpty()) return
    val current = rows.indexOfFirst { it.id == selectedSessionId }.takeIf { it >= 0 } ?: 0
    selectedSessionId = rows[(current + delta + rows.size) % rows.size].id
}

@Composable
private fun SessionFilterStrip(repo: DarkOpsRepo) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SessionFilters.forEach { filter ->
            val active = repo.sessionFilter == filter
            OpsBadgeClickable(filter, active) { repo.sessionFilter = filter }
        }
    }
}

@Composable
private fun SessionRows(repo: DarkOpsRepo) {
    val rows = repo.visibleSessions()
    if (rows.isEmpty()) {
        OpsEmpty("no rows under " + repo.sessionFilter, "loosen the filter")
        return
    }
    rows.forEach { session ->
        OpsRow(selected = session.id == repo.selectedSessionId, onClick = { repo.selectedSessionId = session.id }) {
            Text(
                if (session.id ==
                    repo.selectedSessionId
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(session.time, style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Cyan)
                    Text(session.clientName, style = DarkOpsType.bodyMedium)
                    if (session.walkIn) OpsBadge("WALK-IN", DarkOpsPalette.Amber)
                    if (session.voided) OpsBadge("VOID", DarkOpsPalette.Red)
                }
                Text(session.service.trim() + " · " + session.practitioner, style = DarkOpsType.bodySmall)
            }
            Text(peso(session.price), style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Dim)
            Spacer(Modifier.width(6.dp))
            OpsBadge(session.status.label, statusColor(session.status))
        }
    }
}

@Composable
private fun SessionDetail(repo: DarkOpsRepo) {
    val session = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
    if (session == null) {
        OpsEmpty("nothing selected", "j/k to move")
        return
    }
    Text("DETAIL // " + session.id, style = DarkOpsType.labelSmall)
    Spacer(Modifier.height(4.dp))
    DetailLine("client", session.clientName + " (" + session.clientId + ")")
    DetailLine("slot", session.time + " · " + repo.currentDay.date)
    DetailLine("service", session.service.trim())
    DetailLine("price", peso(session.price))
    DetailLine("practitioner", session.practitioner)
    DetailLine("status", session.status.label + if (session.voided) " + VOID" else "")
    if (session.voided) DetailLine("reason", session.voidReason)
    Spacer(Modifier.height(OpsPadSm))
    SessionActionGrid(repo, session)
}

@Composable
private fun DetailLine(
    key: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(key.padEnd(14), style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
        Text(value, style = DarkOpsType.bodyMedium)
    }
}

@Composable
private fun SessionActionGrid(
    repo: DarkOpsRepo,
    session: OpsSession,
) {
    val locked = repo.dayLocked || session.voided
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OpsAction("complete", !locked && session.status == OpsSessionStatus.PENDING) {
                repo.setStatus(session.id, OpsSessionStatus.COMPLETED)
            }
            OpsAction("no-show", !locked && session.status == OpsSessionStatus.PENDING && !session.walkIn) {
                repo.setStatus(session.id, OpsSessionStatus.NO_SHOW)
            }
            OpsAction("cancel", !locked && session.status == OpsSessionStatus.PENDING && !session.walkIn) {
                repo.setStatus(session.id, OpsSessionStatus.CANCELLED)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (session.voided) {
                OpsAction("unvoid", !repo.dayLocked) { repo.unvoidSession(session.id) }
            } else {
                OpsAction("void…", !repo.dayLocked) { repo.voidTargetId = session.id }
            }
        }
    }
}

@Composable
private fun OpsAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            "[" + label + "]",
            style = DarkOpsType.labelLarge,
            color = if (enabled) DarkOpsPalette.Phosphor else DarkOpsPalette.Faint,
        )
    }
}

@Composable
private fun VoidReasonDialog(repo: DarkOpsRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.voidTargetId = null },
        title = { Text("void " + (repo.voidTargetId ?: "") + " — reason required", style = DarkOpsType.titleMedium) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("reason", style = DarkOpsType.bodySmall) },
                singleLine = true,
                textStyle = DarkOpsType.bodyMedium,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = {
                repo.voidSession(repo.voidTargetId ?: "", reason.ifBlank { "ops call" })
                repo.voidTargetId =
                    null
            }, colors = greenButton()) {
                Text("VOID", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.voidTargetId = null }) {
                Text("abort", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Dim)
            }
        },
        containerColor = DarkOpsPalette.PanelRaised,
    )
}

@Composable
private fun CreateSessionDialog(repo: DarkOpsRepo) {
    var name by remember { mutableStateOf("H. Villamor") }
    var walkIn by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf("Rehab 45m") }
    AlertDialog(
        onDismissRequest = { repo.createOpen = false },
        title = { Text("book session // " + repo.currentDay.date, style = DarkOpsType.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("client", style = DarkOpsType.bodySmall)
                    },
                    singleLine = true,
                    textStyle = DarkOpsType.bodyMedium,
                    colors = fieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
                OutlinedTextField(
                    value = service,
                    onValueChange = {
                        service = it
                    },
                    label = {
                        Text("service", style = DarkOpsType.bodySmall)
                    },
                    singleLine = true,
                    textStyle = DarkOpsType.bodyMedium,
                    colors = fieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
                TextButton(onClick = { walkIn = !walkIn }) {
                    Text(
                        if (walkIn) "[x] walk-in" else "[ ] walk-in",
                        style = DarkOpsType.labelLarge,
                        color = DarkOpsPalette.Amber,
                    )
                }
                OpsNote("at most one PENDING session per client is enforced")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target =
                        repo.clients.firstOrNull { it.name == name } ?: OpsClient("c-99", name, "F", 30, "withheld")
                    repo.createSession(target, walkIn, " " + service, 950)
                    repo.createOpen = false
                },
                colors = greenButton(),
            ) {
                Text("BOOK", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.createOpen = false }) {
                Text("abort", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Dim)
            }
        },
        containerColor = DarkOpsPalette.PanelRaised,
    )
}
