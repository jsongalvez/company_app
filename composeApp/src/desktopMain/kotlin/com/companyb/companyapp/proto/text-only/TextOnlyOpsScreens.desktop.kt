package com.companyb.companyapp.proto.textonly

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #853 — onboarding, login, branches, home (clock + relief), sessions ledger,
// client file. Fake data only.

@Composable
fun TxOnboarding(
    repo: TextOnlyFakeRepo,
    onNext: () -> Unit,
) {
    Head("00", "onboarding", "first run: the kiosk signs in locked.")
    TxPanel {
        KV("role", "ONBOARDING")
        KV("state", "LOCKED")
        Spacer(Modifier.height(6.dp))
        T("capabilities: (none)")
        T("an ONBOARDING identity holds zero capabilities until a")
        T("MANAGER grants a working role. every other screen prints")
        T("this same lock line while you are signed out or locked.")
        Spacer(Modifier.height(6.dp))
        T(repo.capabilitiesOf(TxRole.ONBOARDING), color = TxTerm.Amber, bold = true)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cmd("continue as kiosk", onClick = {
                repo.login("U-SAM")
                onNext()
            })
            Spacer(Modifier.width(12.dp))
            Cmd("skip to login", onClick = onNext)
        }
    }
    Spacer(Modifier.height(8.dp))
    Note("fake flow: continuing signs in as Sam Rivera (ONBOARDING, locked).")
}

@Composable
fun TxLogin(
    repo: TextOnlyFakeRepo,
    onNext: () -> Unit,
) {
    Head("01", "login", "pick an identity from the fake roster.")
    TxPanel {
        T("id        name              role          branch", size = 12, bold = true, color = TxTerm.Bright)
        repo.users.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(
                    (u.id + "  " + u.name).padEnd(26, ' ') + u.role.label.padEnd(14, ' ') + u.branch,
                    size = 12,
                )
            }
            Row {
                Spacer(Modifier.width(4.dp))
                Cmd(
                    if (repo.currentUserId == u.id) "* signed in" else "sign in",
                    onClick = {
                        repo.login(u.id)
                        onNext()
                    },
                    enabled = repo.currentUserId != u.id,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    val cur = repo.currentUser
    if (cur != null) {
        TxPanel {
            KV("signed in", "${cur.name} (${cur.role.label})", TxTerm.Green)
            KV("capabilities", repo.capabilitiesOf(cur.role))
        }
    } else {
        Note("nobody is signed in yet.")
    }
}

@Composable
fun TxBranchSelect(
    repo: TextOnlyFakeRepo,
    onNext: () -> Unit,
) {
    Head("02", "branches", "one branch-day at a time; banner follows.")
    TxPanel {
        repo.branches.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(if (b.id == repo.branchId) "> " else "  ", color = TxTerm.Amber, bold = true)
                T("${b.name}  [${b.kind}]  ${b.dayDate}", size = 13, bold = b.id == repo.branchId)
            }
            Row {
                Spacer(Modifier.width(4.dp))
                Cmd(
                    if (b.id == repo.branchId) "* current" else "switch",
                    onClick = { repo.pickBranch(b.id) },
                    enabled = b.id != repo.branchId,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row {
        Cmd("open home", onClick = onNext)
    }
    Spacer(Modifier.height(8.dp))
    Note("day boundary 04:00 Asia/Manila: visits before 04:00 count toward the previous branch-day.")
}

@Composable
fun TxHome(
    repo: TextOnlyFakeRepo,
    go: (TxScreen) -> Unit,
) {
    val locked = repo.currentUser?.role == TxRole.ONBOARDING || repo.currentUser == null
    Head("03", "home", "clock, relief traffic, day numbers.")
    if (locked) {
        TxPanel { T("LOCKED: sign in with a working role to clock in.", color = TxTerm.Amber, bold = true) }
        Spacer(Modifier.height(8.dp))
    }
    TxPanel {
        T("shift", size = 12, bold = true, color = TxTerm.Amber)
        KV("clock", if (repo.clockedIn) "IN" else "OUT", if (repo.clockedIn) TxTerm.Green else TxTerm.Dim)
        Spacer(Modifier.height(4.dp))
        Cmd(
            if (repo.clockedIn) "clock-out" else "clock-in",
            onClick = { repo.clockToggle() },
            enabled = !locked,
        )
        if (locked) Note("kiosk/ONBOARDING cannot clock in.")
    }
    Spacer(Modifier.height(8.dp))
    TxPanel {
        T("relief invites (duty calls from other branches)", size = 12, bold = true, color = TxTerm.Amber)
        if (repo.invites.none { it.state == "PENDING" }) T("(none pending)", dim = true)
        repo.invites.forEach { inv ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${inv.id} ${inv.fromBranch} :: ${inv.shift} ", size = 12)
                Tag(inv.state, if (inv.state == "PENDING") TxTerm.Amber else TxTerm.Dim)
            }
            if (inv.state == "PENDING" && !locked) {
                Row {
                    Cmd("accept", onClick = { repo.answerInvite(inv.id, true) })
                    Spacer(Modifier.width(8.dp))
                    Cmd("decline", onClick = { repo.answerInvite(inv.id, false) }, danger = true)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    var askShift by remember { mutableStateOf("") }
    var askError by remember { mutableStateOf<String?>(null) }
    TxPanel {
        T("relief requests (cover asks)", size = 12, bold = true, color = TxTerm.Amber)
        repo.reliefAsks.forEach { ask ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${ask.id} ${ask.by} :: ${ask.shift} ", size = 12)
                Tag(ask.state, if (ask.state == "PENDING") TxTerm.Amber else TxTerm.Dim)
            }
            if (ask.state == "PENDING" && !locked) {
                Row {
                    if (!ask.mine) Cmd("grant", onClick = { repo.answerAsk(ask.id, true) })
                    if (!ask.mine) Spacer(Modifier.width(8.dp))
                    Cmd(
                        if (ask.mine) "withdraw" else "deny",
                        onClick = { repo.answerAsk(ask.id, false) },
                        danger = true,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(4.dp))
        TxInputRow(
            value = askShift,
            onValueChange = {
                askShift = it
                askError = null
            },
            placeholder = "new cover ask, e.g. Sat Sep 12 / night cover",
        )
        askError?.let { TxError(it) }
        Cmd(
            "raise ask",
            onClick = {
                askError = if (locked) "Sign in first." else repo.raiseAsk(askShift)
                if (askError == null) askShift = ""
            },
            enabled = !locked,
        )
    }
    Spacer(Modifier.height(8.dp))
    TxPanel {
        T("week Mon-Fri done/total", size = 12, bold = true, color = TxTerm.Amber)
        repo.daySummary().forEach { (day, doneTotal) ->
            val (done, total) = doneTotal
            val bar = "#".repeat(done) + "-".repeat((total - done).coerceAtLeast(0))
            T("$day  [$bar] $done/$total", size = 12)
        }
        val till = repo.weekCompleted().sumOf { it.amount }
        KV("till (completed, unvoided)", txPeso(till), TxTerm.Green)
        KV("misses (no-show/cancel/void)", repo.weekMisses().size.toString(), TxTerm.Red)
    }
    Spacer(Modifier.height(8.dp))
    Row {
        Cmd("sessions >", onClick = { go(TxScreen.SESSIONS) })
        Spacer(Modifier.width(8.dp))
        Cmd("finance >", onClick = { go(TxScreen.FINANCE) })
    }
}

private fun txStatusColor(status: TxSessionStatus) =
    when (status) {
        TxSessionStatus.PENDING -> TxTerm.Amber
        TxSessionStatus.COMPLETED -> TxTerm.Green
        TxSessionStatus.NO_SHOW -> TxTerm.Red
        TxSessionStatus.CANCELLED -> TxTerm.Dim
    }

@Composable
fun TxSessions(repo: TextOnlyFakeRepo) {
    var filter by remember { mutableStateOf<TxSessionStatus?>(null) }
    var managing by remember { mutableStateOf<TxSession?>(null) }
    var voiding by remember { mutableStateOf<TxSession?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var voidError by remember { mutableStateOf<String?>(null) }
    var manageError by remember { mutableStateOf<String?>(null) }
    var addingWalkIn by remember { mutableStateOf(false) }
    var walkService by remember { mutableStateOf("") }
    var walkError by remember { mutableStateOf<String?>(null) }

    val locked = repo.currentUser?.role == TxRole.ONBOARDING || repo.currentUser == null
    Head("04", "sessions", "PENDING -> COMPLETED / NO_SHOW / CANCELLED.")
    Row {
        T("filter: ", size = 12, dim = true)
        val all: List<TxSessionStatus?> = listOf(null) + TxSessionStatus.entries
        all.forEach { f ->
            T(
                text = if (f == null) "[all]" else "[${f.label.lowercase()}]",
                size = 12,
                bold = f == filter,
                color = if (f == filter) TxTerm.Bright else TxTerm.Faint,
                modifier = Modifier.clickable { filter = f }.padding(horizontal = 2.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    val visible = repo.sessions.filter { filter == null || it.status == filter }
    visible.forEach { s ->
        TxPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${s.id} ", size = 12, bold = true, color = TxTerm.Bright)
                Tag(s.status.label, txStatusColor(s.status))
                if (s.walkIn) {
                    Spacer(Modifier.width(6.dp))
                    Tag("WALK-IN", TxTerm.Amber)
                }
                if (s.voided) {
                    Spacer(Modifier.width(6.dp))
                    Tag("VOID", TxTerm.Red)
                }
            }
            T("${s.service} :: ${s.client}", size = 13)
            T("${s.branch} / ${s.day} / ${s.practitioner} / ${txPeso(s.amount)}", size = 12, dim = true)
            if (s.voided) T("void reason: ${s.voidReason}", size = 12, color = TxTerm.Red)
            Spacer(Modifier.height(4.dp))
            Row {
                Cmd("manage", onClick = {
                    managing = s
                    manageError = null
                }, enabled = !locked)
                Spacer(Modifier.width(8.dp))
                if (s.voided) {
                    Cmd("unvoid", onClick = {
                        voiding = s
                        voidReason = ""
                        voidError = null
                    }, enabled = !locked)
                } else {
                    Cmd("void", onClick = {
                        voiding = s
                        voidReason = ""
                        voidError = null
                    }, enabled = !locked, danger = true)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Note("house rule: walk-in sessions refuse NO_SHOW and CANCELLED (nobody to no-show).")
    Spacer(Modifier.height(4.dp))
    Row {
        Cmd("book walk-in", onClick = {
            addingWalkIn = true
            walkService = ""
            walkError = null
        }, enabled = !locked)
    }
    if (locked) Note("signed out / ONBOARDING: ledger is read-only.")

    managing?.let { s ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text("manage ${s.id}", fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    Text("walk-in rule applies: NO_SHOW / CANCELLED rejected.", fontFamily = FontFamily.Monospace)
                    manageError?.let { Text("error: $it", fontFamily = FontFamily.Monospace) }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row {
                    TxSessionStatus.entries.forEach { next ->
                        TextButton(onClick = {
                            manageError = repo.setSessionStatus(s.id, next)
                            if (manageError == null) managing = null
                        }) { Text(next.label.lowercase(), fontFamily = FontFamily.Monospace) }
                    }
                    TextButton(onClick = { managing = null }) { Text("close", fontFamily = FontFamily.Monospace) }
                }
            },
        )
    }
    voiding?.let { s ->
        AlertDialog(
            onDismissRequest = { voiding = null },
            title = { Text(if (s.voided) "unvoid ${s.id}" else "void ${s.id}", fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    if (!s.voided) {
                        TxDialogField(
                            value = voidReason,
                            onValueChange = {
                                voidReason = it
                                voidError = null
                            },
                            placeholder = "void reason (required)",
                        )
                    }
                    voidError?.let { Text("error: $it", fontFamily = FontFamily.Monospace) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    voidError = repo.setVoid(s.id, !s.voided, voidReason)
                    if (voidError == null) voiding = null
                }) { Text(if (s.voided) "unvoid" else "void", fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { voiding = null }) { Text("cancel", fontFamily = FontFamily.Monospace) }
            },
        )
    }
    if (addingWalkIn) {
        AlertDialog(
            onDismissRequest = { addingWalkIn = false },
            title = { Text("book walk-in (Fri)", fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    TxDialogField(
                        value = walkService,
                        onValueChange = {
                            walkService = it
                            walkError = null
                        },
                        placeholder = "service, e.g. Haircut",
                    )
                    walkError?.let { Text("error: $it", fontFamily = FontFamily.Monospace) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    walkError = repo.addWalkIn(walkService, repo.currentUser?.name ?: "kiosk")
                    if (walkError == null) addingWalkIn = false
                }) { Text("book", fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { addingWalkIn = false }) { Text("cancel", fontFamily = FontFamily.Monospace) }
            },
        )
    }
}

@Composable
fun TxClients(repo: TextOnlyFakeRepo) {
    var query by remember { mutableStateOf("") }
    Head("05", "clients", "global file: every branch sees every client.")
    TxInputRow(value = query, onValueChange = { query = it }, placeholder = "filter name or id...")
    Spacer(Modifier.height(6.dp))
    val visible =
        repo.clients.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    visible.forEach { c ->
        TxPanel {
            val name = if (c.anonymized) "CLIENT ${c.id} (masked)" else c.name
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${c.id}  $name  ", size = 13, bold = true)
                if (c.anonymized) Tag("MASKED", TxTerm.Amber)
            }
            T("gender=${c.gender} age=${c.age} home=${c.homeBranch}", size = 12, dim = true)
            T("pending=${c.pendingCount}", size = 12, color = if (c.pendingCount > 0) TxTerm.Amber else TxTerm.Dim)
            Spacer(Modifier.height(4.dp))
            Cmd(if (c.anonymized) "reveal" else "anonymize", onClick = { repo.anonymize(c.id) })
        }
        Spacer(Modifier.height(6.dp))
    }
    Note("rule: at most one PENDING session per client; booking a second is refused at the desk.")
    Note("anonymized view keeps gender + age for reporting while masking the name.")
}

@Composable
fun TxInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        singleLine = true,
        textStyle =
            androidx.compose.ui.text
                .TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        colors = TextFieldDefaults.colors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun TxDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        singleLine = true,
        textStyle =
            androidx.compose.ui.text
                .TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        colors = TextFieldDefaults.colors(),
    )
}
