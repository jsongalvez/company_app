package com.companyb.companyapp.proto.zenfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

private fun ZenStatus.calm(): String =
    when (this) {
        ZenStatus.PENDING -> "pending"
        ZenStatus.COMPLETED -> "completed"
        ZenStatus.NO_SHOW -> "no-show"
        ZenStatus.CANCELLED -> "cancelled"
    }

@Composable
fun ZenLogin(
    onEnter: (String) -> Unit,
    onPreviewOnboarding: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    ZenPage(
        title = "zen · focus",
        subtitle = "One session on screen. Everything else can wait.",
    ) {
        ZenSheet {
            ZenBody("Sit down, breathe once, then enter.")
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Work email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ZenPrimary(
                label = "Enter quietly",
                onClick = { onEnter(address.ifBlank { "practitioner@company.app" }) },
                enabled = true,
            )
        }
        ZenGhost(label = "Preview the ONBOARDING welcome", onClick = onPreviewOnboarding)
        ZenQuiet("Fake door — any email opens it. Nothing leaves this machine.")
    }
}

@Composable
fun ZenOnboardingLocked(onBack: () -> Unit) {
    ZenPage(
        title = "Welcome, newcomer",
        subtitle = "An ONBOARDING profile observes before it acts.",
        onBack = onBack,
    ) {
        ZenSheet {
            ZenBody("Your capability bundle is empty, and that is intentional.")
            ZenRow(label = "Sessions", value = "observe only")
            ZenRow(label = "Clients", value = "observe only")
            ZenRow(label = "Finance", value = "locked")
            ZenRow(label = "Branch day", value = "locked")
        }
        ZenQuiet("A coordinator grants Practitioner capabilities after orientation. Until then, rest.")
    }
}

@Composable
fun ZenBranchSelect(
    repo: ZenFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    ZenPage(
        title = "Where do you sit today?",
        subtitle = "One branch at a time. The others keep without you.",
        onBack = onBack,
    ) {
        repo.branches.forEach { branch ->
            ZenSheet {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            repo.branchId = branch.id
                            onPick()
                        }.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = branch.name, fontFamily = ZenSerif, fontSize = 20.sp, color = ZenInk)
                    ZenQuiet("${branch.place} · branch day ${branch.day.name}")
                }
            }
        }
    }
}

@Composable
fun ZenStillness(
    repo: ZenFakeRepo,
    onAttend: (String) -> Unit,
    onOpenSessions: () -> Unit,
) {
    val branch = repo.currentBranch()
    ZenPage(
        title = "Stillness",
        subtitle = "${branch.name} · ${branch.day.name.lowercase()} day",
        topNote = { ZenDayBanner(branchName = branch.name, day = branch.day) },
    ) {
        ZenSection("Arrival")
        ZenSheet {
            if (repo.clockedIn) {
                ZenBody("You are clocked in. The day has you.")
            } else {
                ZenBody("You have not clocked in yet. There is no rush.")
                ZenPrimary(label = "Clock in", onClick = { repo.clock(true) })
            }
        }
        ZenSection("Your one task")
        val task = repo.oneTask()
        if (task == null) {
            ZenSheet {
                ZenBody("The mat is clear. No pending session waits for you.")
                ZenGhost(label = "Look at all sessions", onClick = onOpenSessions)
            }
        } else {
            ZenSheet {
                Text(text = task.client, fontFamily = ZenSerif, fontSize = 22.sp, color = ZenInk)
                ZenQuiet("${task.service} · ${task.time}")
                ZenPrimary(label = "Attend this session", onClick = { onAttend(task.id) })
                ZenGhost(label = "Look at all sessions", onClick = onOpenSessions)
            }
        }
        ZenSection("Relief")
        ZenSheet {
            ZenBody("You hold relief duty Thursday — Harbor Tour, 14:00.")
            ZenQuiet("Duty is a promise to one afternoon, not to every ask.")
        }
        repo.relief.forEach { item ->
            ZenSheet {
                ZenQuiet(
                    "${item.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                        "${item.branch} · ${item.day}",
                )
                ZenBody(item.note)
                if (item.answered.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Accept",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ZenMoss,
                            modifier = Modifier.clickable { repo.answerRelief(item.id, "Accepted") }.padding(6.dp),
                        )
                        Text(
                            text = "Decline",
                            fontSize = 14.sp,
                            color = ZenSoftInk,
                            modifier = Modifier.clickable { repo.answerRelief(item.id, "Declined") }.padding(6.dp),
                        )
                    }
                } else {
                    ZenQuiet("You ${item.answered.lowercase()} this.")
                }
            }
        }
        var askBranch by remember { mutableStateOf("") }
        var askNote by remember { mutableStateOf("") }
        ZenSheet {
            ZenBody("Ask for relief")
            OutlinedTextField(
                value = askBranch,
                onValueChange = { askBranch = it },
                label = { Text("Branch (blank = here)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = askNote,
                onValueChange = { askNote = it },
                label = { Text("What do you need?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ZenGhost(
                label = "Send the ask",
                onClick = {
                    repo.askRelief(askBranch.trim(), askNote.trim())
                    askBranch = ""
                    askNote = ""
                },
            )
        }
    }
}

@Composable
fun ZenSessionFocus(
    repo: ZenFakeRepo,
    session: ZenSession,
    onLayDown: () -> Unit,
) {
    ZenPage(
        title = session.client,
        subtitle = "${session.service} · ${session.time}",
        onBack = onLayDown,
    ) {
        ZenSheet {
            ZenBody(progressLine(session))
            if (session.voidReason.isNotEmpty()) {
                ZenQuiet("Voided — ${session.voidReason}.")
            }
        }
        ZenSessionActions(repo = repo, session = session)
        ZenQuiet("Nothing else is on screen. Finish or lay this down.")
    }
}

private fun progressLine(session: ZenSession): String =
    when (session.status) {
        ZenStatus.PENDING -> "arrived · awaiting begin"
        ZenStatus.COMPLETED -> "arrived · begun · completed — well done"
        ZenStatus.NO_SHOW -> "the mat stayed empty — marked no-show"
        ZenStatus.CANCELLED -> "released — cancelled"
    }

@Composable
private fun ZenSessionActions(
    repo: ZenFakeRepo,
    session: ZenSession,
) {
    if (session.voidReason.isEmpty()) {
        if (session.status == ZenStatus.PENDING) {
            ZenPrimary(
                label = "Complete the session",
                onClick = { repo.setStatus(session.id, ZenStatus.COMPLETED) },
            )
            if (session.kind == ZenKind.BOOKED) {
                ZenGhost(label = "Mark no-show", onClick = { repo.setStatus(session.id, ZenStatus.NO_SHOW) })
                ZenGhost(label = "Cancel", onClick = { repo.setStatus(session.id, ZenStatus.CANCELLED) })
            } else {
                ZenSheet {
                    ZenQuiet("Walk-ins rest lightly: no NO_SHOW, no CANCELLED. Complete, or void with a reason.")
                }
            }
        } else {
            ZenSheet {
                ZenBody("This session is ${session.status.calm()}. Nothing more to do here.")
            }
        }
        var showVoid by remember(session.id) { mutableStateOf(false) }
        var reason by remember(session.id) { mutableStateOf("") }
        if (showVoid) {
            ZenSheet {
                ZenBody("Void with a reason. The book remembers why.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZenPrimary(label = "Void this session", onClick = { repo.voidSession(session.id, reason.trim()) })
            }
        } else {
            ZenGhost(label = "Void with reason", onClick = { showVoid = true })
        }
    } else {
        var reason by remember(session.id) { mutableStateOf("") }
        ZenSheet {
            ZenBody("Voided — ${session.voidReason}.")
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Unvoid reason") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ZenGhost(label = "Unvoid with reason", onClick = { repo.unvoidSession(session.id, reason.trim()) })
        }
    }
}

@Composable
fun ZenSessions(
    repo: ZenFakeRepo,
    onAttend: (String) -> Unit,
) {
    val branch = repo.currentBranch()
    var filter by remember { mutableStateOf<ZenStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    ZenPage(
        title = "Sessions",
        subtitle = "Showing ${branch.name} only — one branch at a time.",
        topNote = { ZenDayBanner(branchName = branch.name, day = branch.day) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ZenFilterChip(label = "all", selected = filter == null, onClick = { filter = null })
            ZenStatus.entries.forEach { status ->
                ZenFilterChip(
                    label = status.calm(),
                    selected = filter == status,
                    onClick = { filter = if (filter == status) null else status },
                )
            }
        }
        val list = repo.branchSessions().filter { filter == null || it.status == filter }
        if (list.isEmpty()) {
            ZenSheet { ZenBody("Nothing here. The book is quiet on this page.") }
        }
        list.forEach { session ->
            ZenSheet {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            openId = if (openId == session.id) null else session.id
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = session.client, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ZenInk)
                        Text(text = session.status.calm(), fontSize = 13.sp, color = ZenSoftInk)
                    }
                    ZenQuiet(
                        "${session.service} · ${session.time} · " +
                            session.kind.name.lowercase().replace('_', '-'),
                    )
                }
                if (openId == session.id) {
                    ZenRule()
                    ZenRow(label = "Practitioner", value = session.practitioner)
                    ZenRow(label = "Amount", value = zenMoney(session.amount))
                    ZenGhost(label = "Attend alone", onClick = { onAttend(session.id) })
                    ZenSessionActions(repo = repo, session = session)
                }
            }
        }
    }
}

@Composable
private fun ZenFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) ZenDeepMoss else ZenSoftInk,
        modifier =
            Modifier.background(
                if (selected) ZenStone else ZenSurface,
                RoundedCornerShape(12.dp),
            ).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun ZenClients(repo: ZenFakeRepo) {
    var anonymized by remember { mutableStateOf(true) }
    ZenPage(
        title = "Clients",
        subtitle = "Every client, every branch — the book is global.",
    ) {
        ZenSheet {
            ZenBody("One client holds at most one PENDING session. The rest is history.")
            Text(
                text = if (anonymized) "Showing codes — tap to reveal names" else "Showing names — tap to veil them",
                fontSize = 14.sp,
                color = ZenMoss,
                modifier = Modifier.clickable { anonymized = !anonymized }.padding(vertical = 4.dp),
            )
        }
        repo.clients.forEach { client ->
            ZenSheet {
                Text(
                    text = if (anonymized) client.code else client.name,
                    fontFamily = ZenSerif,
                    fontSize = 19.sp,
                    color = ZenInk,
                )
                ZenRow(label = "Pending", value = if (client.pending == 0) "none" else "one")
                ZenRow(label = "Visits", value = client.visits.toString())
                ZenQuiet(client.note)
            }
        }
    }
}

@Composable
fun ZenFinance(repo: ZenFakeRepo) {
    val branch = repo.currentBranch()
    ZenPage(
        title = "Finance",
        subtitle = "Draft slowly. Submit once. Undo rarely.",
        topNote = { ZenDayBanner(branchName = branch.name, day = branch.day) },
    ) {
        ZenSection("New session draft")
        var sLabel by remember { mutableStateOf("") }
        var sAmount by remember { mutableStateOf("") }
        ZenSheet {
            ZenQuiet("SESSION drafts carry net income for the day.")
            OutlinedTextField(
                value = sLabel,
                onValueChange = { sLabel = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sAmount,
                onValueChange = { sAmount = it },
                label = { Text("Net amount") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ZenGhost(
                label = "Lay down the draft",
                onClick = {
                    repo.addDraft(ZenDraftKind.SESSION, sLabel.trim(), sAmount.toDoubleOrNull() ?: 0.0, 1)
                    sLabel = ""
                    sAmount = ""
                },
            )
        }
        ZenSection("New product draft")
        var pLabel by remember { mutableStateOf("") }
        var pPrice by remember { mutableStateOf("") }
        var pQty by remember { mutableStateOf("") }
        ZenSheet {
            ZenQuiet("PRODUCT drafts are price × quantity.")
            OutlinedTextField(
                value = pLabel,
                onValueChange = { pLabel = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pPrice,
                onValueChange = { pPrice = it },
                label = { Text("Price") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pQty,
                onValueChange = { pQty = it },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ZenGhost(
                label = "Lay down the draft",
                onClick = {
                    val price = pPrice.toDoubleOrNull() ?: 0.0
                    val qty = pQty.toIntOrNull() ?: 1
                    repo.addDraft(ZenDraftKind.PRODUCT, pLabel.trim(), price * qty, qty)
                    pLabel = ""
                    pPrice = ""
                    pQty = ""
                },
            )
        }
        ZenSection("Drafts")
        val open = repo.drafts.filter { !it.submitted }
        if (open.isEmpty()) {
            ZenSheet { ZenBody("No open drafts. The desk is clear.") }
        }
        open.forEach { draft ->
            ZenSheet {
                ZenBody(draft.label)
                ZenRow(label = draft.kind.name.lowercase(), value = zenMoney(draft.amount))
                ZenPrimary(label = "Submit — seal a snapshot", onClick = { repo.submitDraft(draft.id) })
            }
        }
        ZenSection("Sealed snapshots")
        val sealed = repo.drafts.filter { it.submitted }
        if (sealed.isEmpty()) {
            ZenSheet { ZenBody("Nothing sealed yet.") }
        }
        sealed.forEach { draft ->
            ZenSheet {
                ZenBody(draft.label)
                ZenRow(label = "Snapshot", value = draft.snapshot)
                ZenRow(label = "Amount", value = zenMoney(draft.amount))
                if (draft.undone) {
                    ZenQuiet("Undone — ${draft.undoReason}.")
                } else {
                    var reason by remember(draft.id) { mutableStateOf("") }
                    ZenQuiet("Undo is possible within 48h, with a reason.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Undo reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZenGhost(label = "Undo this snapshot", onClick = { repo.undoDraft(draft.id, reason.trim()) })
                }
            }
        }
        ZenSheet {
            ZenBody("On commission")
            ZenQuiet(
                "SESSION net splits 60/40, practitioner and branch. " +
                    "PRODUCT carries no split — the shelf pays for itself.",
            )
        }
    }
}

@Composable
fun ZenTeam(repo: ZenFakeRepo) {
    ZenPage(
        title = "Team",
        subtitle = "Few names, clear roles.",
    ) {
        repo.mates.forEach { mate ->
            ZenSheet {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = mate.name, fontFamily = ZenSerif, fontSize = 19.sp, color = ZenInk)
                    Text(text = mate.role, fontSize = 13.sp, color = ZenSoftInk)
                }
                ZenQuiet(mate.note)
                if (mate.locked) {
                    ZenQuiet("ONBOARDING is locked: observes only, touches nothing.")
                }
            }
        }
        ZenSheet {
            ZenBody("At a glance")
            ZenQuiet(
                "Practitioner holds the mat · Coordinator keeps the book · " +
                    "Manager moves days · Accountant reads seals.",
            )
        }
    }
}

@Composable
fun ZenMail(repo: ZenFakeRepo) {
    ZenPage(
        title = "Mailbox",
        subtitle = "Read what needs you. Leave the rest.",
    ) {
        ZenGhost(label = "Mark everything read", onClick = { repo.markAllRead() })
        repo.notices.forEach { notice ->
            ZenSheet {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { repo.markRead(notice.id) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (notice.read) "○" else "●",
                            fontSize = 13.sp,
                            color = if (notice.read) ZenHairline else ZenMoss,
                        )
                        Text(text = notice.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ZenInk)
                    }
                    ZenBody(notice.body)
                    ZenQuiet("${notice.branch} · ${notice.day}")
                }
            }
        }
    }
}

@Composable
fun ZenAudit(repo: ZenFakeRepo) {
    ZenPage(
        title = "Audit log",
        subtitle = "Everything remembered, in order.",
    ) {
        repo.audits.forEach { entry ->
            ZenSheet {
                ZenQuiet("${entry.time} · ${entry.actor}")
                ZenBody(entry.action)
                ZenQuiet(entry.detail)
            }
        }
    }
}

@Composable
fun ZenProfile(
    repo: ZenFakeRepo,
    onLogout: () -> Unit,
) {
    val branch = repo.currentBranch()
    ZenPage(
        title = "Profile",
        subtitle = "You, the day, and the door.",
    ) {
        ZenSheet {
            ZenRow(label = "Email", value = repo.email.ifBlank { "—" })
            ZenRow(label = "Branch", value = branch.name)
            ZenRow(label = "Branch day", value = branch.day.name)
        }
        ZenSheet {
            ZenBody("Move the branch day forward: OPEN → PAST → REMITTED.")
            ZenGhost(
                label = "Move the day (now ${branch.day.name})",
                onClick = { repo.cycleDay() },
            )
        }
        ZenSheet {
            if (repo.clockedIn) {
                ZenBody("You are clocked in.")
                ZenPrimary(label = "Clock out", onClick = { repo.clock(false) })
            } else {
                ZenBody("You are clocked out.")
                ZenPrimary(label = "Clock in", onClick = { repo.clock(true) })
            }
        }
        ZenGhost(label = "Reset the demo", onClick = { repo.reset() })
        ZenGhost(label = "Log out", onClick = onLogout)
    }
}
