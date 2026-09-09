package com.companyb.companyapp.proto.dualpersona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #781 — dual-persona shell: persona toggle, branch-day banner, capability-gated rail, router.
// Same FakeRepo under both lenses; only the palette, nav, and actions reshape.

enum class DpScreen(val title: String, val need: DpCapability?) {
    ONBOARDING("Onboarding", null),
    LOGIN("Login", null),
    BRANCHES("Branches", null),
    HOME("Home", DpCapability.CLOCK),
    SESSIONS("Sessions", DpCapability.SCHEDULE),
    CLIENTS("Clients", DpCapability.CLIENT_VIEW),
    RELIEF("Relief", null),
    FINANCE("Finance", DpCapability.FINANCE_VIEW),
    TEAM("Team", DpCapability.TEAM_VIEW),
    MAILBOX("Mailbox", null),
    AUDIT("Audit", DpCapability.AUDIT_VIEW),
    PROFILE("Profile", null),
}

@Composable
fun DualPersonaProtoApp(onBack: () -> Unit) {
    val repo = remember { DpFakeRepo() }
    var screen by remember { mutableStateOf(DpScreen.ONBOARDING) }
    val persona = repo.persona
    val p = persona.palette()

    LaunchedEffect(Unit) { logInfo("DualPersonaProto", "dual-persona prototype launched") }

    val need = screen.need
    val visible = need == null || repo.capabilities().contains(need) || screen == DpScreen.RELIEF
    val effective = if (visible) screen else DpScreen.HOME

    Column(Modifier.fillMaxSize().background(p.bg)) {
        DpMasthead(repo = repo, p = p, screen = screen, onFlip = {
            repo.persona = if (repo.persona == DpPersona.PRACTITIONER) DpPersona.COORDINATOR else DpPersona.PRACTITIONER
            if (screen.need != null && !repo.capabilities().contains(screen.need)) screen = DpScreen.HOME
        })
        DpDayBanner(repo = repo, p = p, onPickDay = { repo.selectedDayId = it })
        Row(Modifier.fillMaxSize()) {
            DpRail(
                p = p,
                repo = repo,
                screen = effective,
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                when (effective) {
                    DpScreen.ONBOARDING -> DpOnboarding(repo = repo, p = p, onNext = { screen = DpScreen.LOGIN })
                    DpScreen.LOGIN -> DpLogin(repo = repo, p = p, onNext = { screen = DpScreen.BRANCHES })
                    DpScreen.BRANCHES -> DpBranchSelect(repo = repo, p = p, onNext = { screen = DpScreen.HOME })
                    DpScreen.HOME -> DpHome(repo = repo, p = p, go = { screen = it })
                    DpScreen.SESSIONS -> DpSessions(repo = repo, p = p)
                    DpScreen.CLIENTS -> DpClients(repo = repo, p = p)
                    DpScreen.RELIEF -> DpReliefBoard(repo = repo, p = p)
                    DpScreen.FINANCE -> DpFinance(repo = repo, p = p)
                    DpScreen.TEAM -> DpTeam(repo = repo, p = p)
                    DpScreen.MAILBOX -> DpMailbox(repo = repo, p = p)
                    DpScreen.AUDIT -> DpAuditList(repo = repo, p = p)
                    DpScreen.PROFILE -> DpProfile(repo = repo, p = p, onLogout = { screen = DpScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun DpMasthead(repo: DpFakeRepo, p: DpPalette, screen: DpScreen, onFlip: () -> Unit) {
    val persona = repo.persona
    Column(Modifier.fillMaxWidth().background(p.surface).border(0.dp, p.line).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    persona.lensName().uppercase() + " · " + screen.title.uppercase(),
                    color = p.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
                val user = repo.currentUser
                Text(
                    (user?.name ?: "SIGNED OUT") + " · " + repo.currentBranch.name + " · same data, both lenses",
                    color = p.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            DpPersonaSwitch(p = p, persona = persona, onFlip = onFlip)
        }
    }
}

@Composable
private fun DpPersonaSwitch(p: DpPalette, persona: DpPersona, onFlip: () -> Unit) {
    Row(
        Modifier.background(p.card, RoundedCornerShape(24.dp))
            .border(1.dp, p.line, RoundedCornerShape(24.dp))
            .clickable(onClick = onFlip)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in DpPersona.entries) {
            val active = option == persona
            Text(
                if (option == DpPersona.PRACTITIONER) "● Practitioner" else "◆ Coordinator",
                color = if (active) p.accentInk else p.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.background(
                    if (active) p.accent else p.card,
                    RoundedCornerShape(20.dp),
                ).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DpDayBanner(repo: DpFakeRepo, p: DpPalette, onPickDay: (String) -> Unit) {
    val day = repo.selectedDay
    Row(
        Modifier.fillMaxWidth()
            .background(day.status.band(p))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DpChip(p = p, text = "BRANCH DAY · " + day.status.name, band = p.card, ink = day.status.ink(p))
        Spacer(Modifier.width(10.dp))
        Text(
            repo.currentBranch.name + " · " + day.dateLabel + " · boundary 04:00 Asia/Manila",
            color = p.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        for (d in repo.days) {
            val sel = d.id == day.id
            Text(
                (if (sel) "[" else "") + d.label + (if (sel) "]" else ""),
                color = if (sel) p.ink else p.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { onPickDay(d.id) }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DpRail(repo: DpFakeRepo, p: DpPalette, screen: DpScreen, onPick: (DpScreen) -> Unit, onBack: () -> Unit) {
    val caps = repo.capabilities()
    val items = DpScreen.entries.filter {
        when (it) {
            DpScreen.ONBOARDING, DpScreen.LOGIN, DpScreen.BRANCHES -> false
            DpScreen.RELIEF -> caps.contains(DpCapability.RELIEF_REQUEST) || caps.contains(DpCapability.RELIEF_MANAGE)
            else -> it.need == null || caps.contains(it.need)
        }
    }
    val hidden = DpScreen.entries.size - 3 - items.size
    Column(
        Modifier.width(196.dp).fillMaxHeight().background(p.surface)
            .border(0.dp, p.line)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text("NAV · " + repo.persona.lensName().uppercase(), color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        for (item in items) {
            val active = item == screen
            val badge = if (item == DpScreen.MAILBOX) " " + repo.notifications.count { !it.read } else ""
            Text(
                item.title + badge,
                color = if (active) p.accentInk else p.ink,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
                    .background(if (active) p.accent else p.surface, RoundedCornerShape(8.dp))
                    .clickable { onPick(item) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
        if (hidden > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "$hidden section(s) need the other lens — flip the toggle up top.",
                color = p.muted,
                fontSize = 11.sp,
                modifier = Modifier.background(p.infoBand, RoundedCornerShape(8.dp)).padding(8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "← Exit prototype",
            color = p.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
    }
}
