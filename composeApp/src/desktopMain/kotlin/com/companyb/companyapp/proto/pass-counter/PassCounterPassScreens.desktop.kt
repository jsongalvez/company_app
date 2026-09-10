package com.companyb.companyapp.proto.passcounter

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

// #832 — pass-counter front of house: onboarding lock, login, station select, the
// pass home (pending rail + slip + bell/waste), tickets board, guests book.

@Composable
fun PcOnboarding(
    repo: PassCounterFakeRepo,
    onNext: () -> Unit,
) {
    PcTicketCard {
        PcSectionTitle("Stage welcome")
        Text("New on the line? Watch the pass first.", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            "ONBOARDING accounts are locked to the stage corner: empty capability bundle, " +
                "no firing, no bell, no cash-up. The expo grants a station once the stage " +
                "knows the menu.",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        val stage = repo.users.firstOrNull { it.onboarding }
        PcDarkCard {
            Text(
                "${stage?.name ?: "Stage"} · ONBOARDING · locked",
                color = PcColors.LampSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (repo.stageGranted) "Granted Practitioner — head to login." else "Capability bundle: empty.",
                color = PcColors.Muted, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        PcRowButtons(
            { PcFireButton("Continue to login", onClick = onNext) },
            { PcGhost("Preview the lock", onClick = { repo.audit("STAGE", "ONBOARDING lock previewed", null) }) },
        )
    }
}

@Composable
fun PcLogin(
    repo: PassCounterFakeRepo,
    onNext: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    PcTicketCard {
        PcSectionTitle("Clock in at the pass")
        Text("Who is on the line?", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.users.forEach { user ->
                val locked = user.onboarding && !repo.stageGranted
                val selected = repo.currentUserId == user.id
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selected) PcColors.LampSoft else PcColors.Cream,
                            RoundedCornerShape(8.dp),
                        ).clickable { if (!locked) repo.currentUserId = user.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val home = repo.branches.firstOrNull { it.id == user.homeBranchId }?.name
                                ?: user.homeBranchId
                            Text(
                                "${user.role} · $home" + if (locked) " · LOCKED" else "",
                                fontSize = 12.sp, color = PcColors.Muted, fontFamily = PcSlip,
                            )
                        }
                        if (selected) PcTag("ON TICKET", PcColors.Fire)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = pin, onValueChange = { pin = it }, label = { Text("PIN (any 4 digits, fake)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        PcFireButton(
            "Fire up the pass",
            enabled = repo.currentUserId != null && pin.length >= 4,
            onClick = {
                repo.clockedIn = true
                repo.audit("CLOCK-IN", repo.actorName(), "login at the pass")
                onNext()
            },
        )
    }
}

@Composable
fun PcStationSelect(
    repo: PassCounterFakeRepo,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTicketCard {
            PcSectionTitle("Pick your station")
            Text("Three passes, one kitchen.", fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        repo.branches.forEach { branch ->
            val selected = repo.currentBranchId == branch.id
            val band = when (branch.dayStatus) {
                PcDayStatus.OPEN -> PcColors.Serve
                PcDayStatus.PAST -> PcColors.Past
                PcDayStatus.REMITTED -> PcColors.Remitted
            }
            Box(
                Modifier.fillMaxWidth()
                    .background(PcColors.Ticket, RoundedCornerShape(8.dp))
                    .clickable {
                        repo.currentBranchId = branch.id
                        repo.selectedTicketId = repo.pendingFor(branch.id).firstOrNull()?.id
                            ?: repo.tickets.firstOrNull { it.branchId == branch.id }?.id
                            ?: repo.selectedTicketId
                    }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "${branch.station} · ${branch.onLine} on the line · " +
                                "target ₱${branch.target} · banked ₱${repo.branchBanked(branch.id)}",
                            fontSize = 12.sp, color = PcColors.Muted, fontFamily = PcSlip,
                        )
                    }
                    PcTag(branch.dayStatus.name, band)
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        PcTag("YOUR PASS", PcColors.Fire)
                    }
                }
            }
        }
        PcSteelButton("Work ${repo.currentBranch.name}", onClick = onNext)
    }
}

@Composable
fun PcPass(
    repo: PassCounterFakeRepo,
    go: (PcScreen) -> Unit,
) {
    val branchId = repo.currentBranchId
    val pending = repo.tickets.filter { it.branchId == branchId && it.status == PcSessionStatus.PENDING }
    val belled = repo.tickets.filter { it.branchId == branchId && it.status == PcSessionStatus.COMPLETED }
    val waste = repo.tickets.filter { it.branchId == branchId && it.voided }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PcTicketCard {
                PcSectionTitle("Pending rail · ${pending.size} firing")
                if (pending.isEmpty()) {
                    Text("Rail is clear. Ring the bell on the next one.", fontSize = 13.sp, color = PcColors.Muted)
                }
                pending.forEach { ticket ->
                    PcSlipRow(
                        ticket = ticket, selected = ticket.id == repo.selectedTicketId,
                        onPick = { repo.selectedTicketId = ticket.id },
                    )
                }
            }
            PcTicketCard {
                PcSectionTitle("Relief cover · line backup")
                val open = repo.covers.filter { it.state == PcCoverState.OPEN }
                Text(
                    if (open.isEmpty()) "Nobody calling for cover." else "${open.size} cook(s) calling for cover.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                PcRowButtons(
                    { PcGhost("Invite cover", onClick = { go(PcScreen.CREW) }) },
                    { PcGhost("Request cover", onClick = { go(PcScreen.CREW) }) },
                )
            }
        }
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val slip = repo.selectedTicket
            if (slip == null) {
                PcTicketCard { Text("Pin a ticket from the rail.", fontSize = 13.sp) }
            } else {
                PcSlipDetail(repo = repo, ticket = slip)
            }
            if (!repo.clockedIn) {
                PcTicketCard {
                    Text("Off the line — clock in to fire.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    PcFireButton(
                        "Clock in",
                        onClick = {
                            repo.clockedIn = true
                            repo.audit("CLOCK-IN", repo.actorName(), repo.currentBranch.name)
                        },
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PcDarkCard {
                PcSectionTitle("Completed bell · ${belled.size}", dark = true)
                if (belled.isEmpty()) Text("No bells yet.", color = PcColors.Muted, fontSize = 12.sp)
                belled.take(5).forEach { ticket ->
                    Text(
                        "🔔 ${ticket.slip} ${ticket.guest} · ₱${ticket.price}",
                        color = PcColors.Ticket, fontSize = 12.sp, fontFamily = PcSlip,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                PcLink("Open tickets board", onClick = { go(PcScreen.TICKETS) })
            }
            PcDarkCard {
                PcSectionTitle("Void waste log · ${waste.size}", dark = true)
                if (waste.isEmpty()) Text("Zero waste. Clean pass.", color = PcColors.Muted, fontSize = 12.sp)
                waste.take(5).forEach { ticket ->
                    Text(
                        "✕ ${ticket.slip} ${ticket.guest} — ${ticket.voidReason}",
                        color = PcColors.WasteSoft, fontSize = 12.sp, fontFamily = PcSlip,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                PcLink("Audit the waste", onClick = { go(PcScreen.LEDGER) })
            }
        }
    }
}

@Composable
private fun PcSlipRow(
    ticket: PcTicket,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(if (selected) PcColors.LampSoft else PcColors.Cream, RoundedCornerShape(6.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ticket.slip, fontWeight = FontWeight.Black, fontFamily = PcSlip, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(ticket.guest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val flags = (if (ticket.walkIn) " · WALK-IN" else "") + if (ticket.voided) " · VOIDED" else ""
                Text(
                    "${ticket.fireTime} · ${ticket.course}$flags",
                    fontSize = 11.sp, color = PcColors.Muted, fontFamily = PcSlip,
                )
            }
            Text("₱${ticket.price}", fontWeight = FontWeight.Black, fontFamily = PcSlip, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun PcSlipDetail(
    repo: PassCounterFakeRepo,
    ticket: PcTicket,
) {
    var reason by remember(ticket.id) { mutableStateOf("") }
    PcTicketCard(pinned = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SLIP ${ticket.slip}", fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = PcSlip)
            Spacer(Modifier.width(8.dp))
            PcTag(ticket.status.name, if (ticket.voided) PcColors.Waste else PcColors.Fire)
            if (ticket.walkIn) {
                Spacer(Modifier.width(6.dp))
                PcTag("WALK-IN", PcColors.Brass)
            }
        }
        Spacer(Modifier.height(8.dp))
        PcFieldRow("Guest", ticket.guest)
        PcFieldRow("Course", "${ticket.course} · ₱${ticket.price}")
        PcFieldRow("Fired", "${ticket.fireTime} · ${repo.serviceDate}")
        PcFieldRow("Station", repo.branches.firstOrNull { it.id == ticket.branchId }?.name ?: ticket.branchId)
        if (ticket.walkIn) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Walk-in rule: slips off the street fire fast and clear fast — " +
                    "NO_SHOW and CANCELLED do not apply to walk-ins.",
                fontSize = 12.sp, color = PcColors.Brass, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        PcSectionTitle("Call the status")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ticket.status != PcSessionStatus.COMPLETED) {
                PcFireButton("🔔 Bell (served)", onClick = { repo.setTicketStatus(ticket, PcSessionStatus.COMPLETED) })
            }
            if (!ticket.walkIn) {
                if (ticket.status != PcSessionStatus.NO_SHOW) {
                    PcGhost("No-show") { repo.setTicketStatus(ticket, PcSessionStatus.NO_SHOW) }
                }
                if (ticket.status != PcSessionStatus.CANCELLED) {
                    PcGhost("Cancel") { repo.setTicketStatus(ticket, PcSessionStatus.CANCELLED) }
                }
            }
            if (ticket.status != PcSessionStatus.PENDING) {
                PcGhost("Re-fire") { repo.setTicketStatus(ticket, PcSessionStatus.PENDING) }
            }
        }
        Spacer(Modifier.height(10.dp))
        PcSectionTitle("Waste (void) with reason")
        if (ticket.voided) {
            Text("Voided: ${ticket.voidReason}", fontSize = 13.sp, color = PcColors.Waste, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PcSteelButton("Pull from waste", onClick = { repo.unvoidTicket(ticket) })
        } else {
            TextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("Void reason (required)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            PcFireButton("Void to waste", enabled = reason.isNotBlank(), onClick = { repo.voidTicket(ticket, reason) })
        }
    }
}

@Composable
fun PcTickets(repo: PassCounterFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var guest by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("Standard 60") }
    var price by remember { mutableStateOf("1200") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTicketCard {
            PcSectionTitle("Tickets board · ${repo.currentBranch.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PcChip("ALL", filter == null, onClick = { filter = null })
                PcSessionStatus.entries.forEach { status ->
                    PcChip(status.name, filter == status.name, onClick = { filter = status.name })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Walk-in rule note: walk-in slips never take NO_SHOW or CANCELLED — " +
                    "those buttons stay off the slip.",
                fontSize = 12.sp, color = PcColors.Muted,
            )
        }
        val rows = repo.tickets.filter {
            it.branchId == repo.currentBranchId && (filter == null || it.status.name == filter)
        }
        if (rows.isEmpty()) PcTicketCard { Text("No tickets under this call.", fontSize = 13.sp) }
        rows.forEach { ticket ->
            PcTicketCard {
                PcSlipRow(
                    ticket = ticket, selected = ticket.id == repo.selectedTicketId,
                    onPick = { repo.selectedTicketId = ticket.id },
                )
                if (ticket.id == repo.selectedTicketId) {
                    Spacer(Modifier.height(8.dp))
                    PcSlipDetail(repo = repo, ticket = ticket)
                }
            }
        }
        PcTicketCard {
            PcSectionTitle("Fire a new ticket")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    TextField(
                        value = guest, onValueChange = { guest = it }, label = { Text("Guest") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                }
                Box(Modifier.weight(1f)) {
                    TextField(
                        value = course, onValueChange = { course = it }, label = { Text("Course") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                }
                Box(Modifier.width(140.dp)) {
                    TextField(
                        value = price, onValueChange = { price = it.filter { c -> c.isDigit() } },
                        label = { Text("₱") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PcFireButton(
                    "Fire booked",
                    enabled = guest.isNotBlank(),
                    onClick = {
                        repo.fireTicket(guest, course.ifBlank { "Standard 60" }, price.toIntOrNull() ?: 1200, false)
                        guest = ""
                    },
                )
                PcSteelButton(
                    "Seat walk-in",
                    enabled = guest.isNotBlank(),
                    onClick = {
                        repo.fireTicket(guest, course.ifBlank { "Standard 60" }, price.toIntOrNull() ?: 1200, true)
                        guest = ""
                    },
                )
            }
        }
    }
}

@Composable
fun PcGuests(repo: PassCounterFakeRepo) {
    var anonymized by remember { mutableStateOf(false) }
    val showAnon = anonymized || repo.anonymizeAll
    PcTicketCard {
        PcSectionTitle("Guest book · global")
        Text(
            "One guest, one pending ticket at most — the rail refuses a second firing " +
                "while a slip is still up. Names blur in anonymized view; party size and notes stay.",
            fontSize = 12.sp, color = PcColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        PcRowButtons(
            { PcChip(if (showAnon) "ANONYMIZED" else "NAMED", showAnon, onClick = { anonymized = !anonymized }) },
            { PcGhost(if (repo.anonymizeAll) "Unblur all" else "Blur all") { repo.anonymizeAll = !repo.anonymizeAll } },
        )
    }
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repo.guests.forEach { guest ->
            val pending = repo.pendingCountForGuest(guest.name)
            PcTicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showAnon) "Guest ••••" else guest.name,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        Text(guest.detail, fontSize = 12.sp, color = PcColors.Muted)
                    }
                    PcTag(if (pending > 0) "1 PENDING" else "CLEAR", if (pending > 0) PcColors.Fire else PcColors.Serve)
                }
                Spacer(Modifier.height(8.dp))
                PcRowButtons(
                    {
                        PcFireButton(
                            "Fire ticket",
                            enabled = pending == 0,
                            onClick = { repo.fireTicket(guest.name, "Standard 60", 1200, false) },
                        )
                    },
                    { PcGhost("History", onClick = { repo.audit("GUEST", "${guest.name} book opened", null) }) },
                )
                if (pending > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Rail rule: clear the pending slip before firing another.",
                        fontSize = 11.sp, color = PcColors.Fire,
                    )
                }
            }
        }
    }
}
