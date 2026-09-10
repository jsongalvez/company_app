package com.companyb.companyapp.proto.roundscart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun RcDrawerTab() {
    val repo = RoundsCartFakeRepo
    val me = repo.currentUser.value
    var adjustKind by remember { mutableStateOf(RcRemitKind.SESSION) }
    var freeAmount by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("DRAWER · COUNT AT THE CART", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("Two drawers, one seal each.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Text(
            "SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity) " +
                "submit independently. Submit freezes an immutable Snapshot; Undo reopens the Draft within 48h with a reason.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftChart,
        )
        Spacer(Modifier.height(10.dp))
        repo.remittances.forEach { remit ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ChartCard)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${remit.kind} · ${remit.dayLabel}", style = MaterialTheme.typography.titleMedium, color = InkChart)
                        Text(
                            "₱${remit.amount} · ${remit.status}" +
                                (if (remit.snapshotNo.isNotEmpty()) " · ${remit.snapshotNo}" else "") +
                                (if (remit.note.isNotEmpty()) " · ${remit.note}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftChart,
                        )
                    }
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (remit.status == RcRemitStatus.DRAFT) CartAmberWash else CartGreen)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            remit.status.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remit.status == RcRemitStatus.DRAFT) CartAmber else Color.White,
                        )
                    }
                }
                if (remit.status == RcRemitStatus.DRAFT) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RcSmallButton("− ₱500") {
                            val i = repo.remittances.indexOfFirst { it.id == remit.id }
                            if (i >= 0) repo.remittances[i] = remit.copy(amount = (remit.amount - 500).coerceAtLeast(0))
                        }
                        RcSmallButton("+ ₱500") {
                            val i = repo.remittances.indexOfFirst { it.id == remit.id }
                            if (i >= 0) repo.remittances[i] = remit.copy(amount = remit.amount + 500)
                        }
                        RcSmallButton("Seal submit") {
                            val i = repo.remittances.indexOfFirst { it.id == remit.id }
                            if (i >= 0) {
                                repo.remittances[i] = remit.copy(status = RcRemitStatus.SUBMITTED, snapshotNo = repo.nextSnapshot())
                            }
                            repo.stamp(me.name, "SUBMIT_REMITTANCE", "${remit.kind} ${remit.dayLabel}")
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RcSmallButton("Undo (48h)") {
                            val i = repo.remittances.indexOfFirst { it.id == remit.id }
                            if (i >= 0) {
                                repo.remittances[i] = remit.copy(status = RcRemitStatus.DRAFT, note = "undone <48h with reason")
                            }
                            repo.stamp(me.name, "UNDO_REMITTANCE", "${remit.snapshotNo} ${remit.kind}", "drawer miscount")
                        }
                        Text("Window closes 48h after seal.", style = MaterialTheme.typography.bodySmall, color = InkSoftChart)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        RcCard("LOOSE BILLS · DROP INTO A DRAWER") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RcRemitKind.entries.forEach { kind ->
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (kind == adjustKind) CartTealDeep else CartTealWash)
                            .clickable { adjustKind = kind }.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            kind.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (kind == adjustKind) Color.White else InkChart,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = freeAmount,
                    onValueChange = { freeAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Amount ₱") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val amt = freeAmount.toIntOrNull() ?: 0
                        val target = repo.remittances.firstOrNull { it.kind == adjustKind && it.status == RcRemitStatus.DRAFT }
                        if (target != null) {
                            val i = repo.remittances.indexOfFirst { it.id == target.id }
                            repo.remittances[i] = target.copy(amount = target.amount + amt)
                            repo.stamp(me.name, "DRAWER_ADJUST", "$adjustKind +₱$amt")
                            freeAmount = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CartTealDeep),
                ) { Text("Drop in") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Commission split note: product commissions pool per Branch Day and split equally among all " +
                    "Practitioners and Coordinators clocked in at sold_at time; manual inclusions/exclusions may " +
                    "override. Separate from compensation, never touched by remittance.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoftChart,
            )
        }
    }
}

@Composable
internal fun RcTeamTab() {
    val repo = RoundsCartFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("TEAM · WHO PUSHES", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("The ward crew for today's round.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Spacer(Modifier.height(10.dp))
        repo.roleBoard.forEach { (role, count, note) ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ChartCard)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$role · $count", style = MaterialTheme.typography.titleMedium, color = InkChart)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = InkSoftChart)
                }
                if (role == "ONBOARDING") {
                    Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = CartRed)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        RcCard("DIRECTORY") {
            repo.directory.forEach { user ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.bodyLarge, color = InkChart)
                        Text(
                            "${user.role} · home ${repo.branchName(user.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftChart,
                        )
                    }
                    if (user.onboarding) Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = CartRed)
                }
            }
        }
    }
}

@Composable
internal fun RcPagerTab() {
    val repo = RoundsCartFakeRepo
    val me = repo.currentUser.value
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("PAGER · ${repo.unreadCount} UNREAD", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("The cart's pager never loses a slip.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Spacer(Modifier.height(6.dp))
        RcGhostButton("File all read") {
            repo.mailbox.forEachIndexed { i, mail -> repo.mailbox[i] = mail.copy(read = true) }
            repo.stamp(me.name, "READ_ALL_MAIL", "pager")
        }
        Spacer(Modifier.height(8.dp))
        repo.mailbox.forEach { mail ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(MaterialTheme.shapes.small).background(if (mail.read) ChartCard else CartTealWash)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.small)
                    .clickable {
                        val i = repo.mailbox.indexOfFirst { it.id == mail.id }
                        if (i >= 0) repo.mailbox[i] = mail.copy(read = true)
                    }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(mail.title, style = MaterialTheme.typography.bodyLarge, color = InkChart)
                    Text("${mail.body} · ${mail.day}", style = MaterialTheme.typography.bodySmall, color = InkSoftChart)
                }
                Text(
                    if (mail.read) "FILED" else "NEW",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mail.read) InkSoftChart else CartTealDeep,
                )
            }
        }
    }
}

@Composable
internal fun RcLogbookTab() {
    val repo = RoundsCartFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("LOGBOOK · EVERY PUSH RECORDED", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("Who moved the cart, and why.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Spacer(Modifier.height(8.dp))
        repo.audits.forEach { audit ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(MaterialTheme.shapes.small).background(ChartCard)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.small).padding(10.dp),
            ) {
                Text("${audit.action} · ${audit.record}", style = MaterialTheme.typography.bodyLarge, color = InkChart)
                Text(
                    "${audit.actor} · ${audit.whenText}" + if (audit.reason.isNotEmpty()) " · reason: ${audit.reason}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoftChart,
                )
            }
        }
    }
}

@Composable
internal fun RcProfileTab(onLogout: () -> Unit, onExit: () -> Unit) {
    val repo = RoundsCartFakeRepo
    val me = repo.currentUser.value
    val clockedIn by repo.clockedIn
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("PROFILE · END OF ROUND", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text(me.name, style = MaterialTheme.typography.displaySmall, color = InkChart)
        Text(
            "${me.role} · home ${repo.branchName(me.homeBranchId)} · " +
                if (clockedIn) "clocked in at ${repo.branchName(repo.clockedBranchId.value)}" else "off the clock",
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoftChart,
        )
        Spacer(Modifier.height(10.dp))
        RcCard("SHIFT") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clockedIn) {
                    RcSmallButton("Clock out") {
                        repo.clockedIn.value = false
                        repo.stamp(me.name, "CLOCK_OUT", repo.branchName(repo.clockedBranchId.value))
                    }
                } else {
                    RcSmallButton("Clock in") {
                        repo.clockedIn.value = true
                        repo.stamp(me.name, "CLOCK_IN", repo.branchName(repo.clockedBranchId.value))
                    }
                }
                RcGhostButton("Log out") {
                    repo.stamp(me.name, "LOGOUT", me.role)
                    onLogout()
                }
                RcGhostButton("Park cart (close)") { onExit() }
            }
        }
    }
}
