package com.companyb.companyapp.proto.guestpass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

enum class GuestPassScreen(val label: String) {
    HOME("Duty pass"),
    BRANCHES("Counters"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun GuestPassApp() {
    val repo = remember { GuestPassRepo() }
    var screen by remember { mutableStateOf(GuestPassScreen.HOME) }
    logInfo("GuestPass", "guest-pass prototype started")
    val user = repo.currentUser
    if (user == null) {
        GuestPassFrame(title = "Visitor desk") { LoginScreen(repo) }
        return
    }
    if (user.role == FakeRole.ONBOARDING) {
        GuestPassFrame(title = "Locked badge") { OnboardingLockedScreen(repo) }
        return
    }
    Row(modifier = Modifier.fillMaxSize().background(GuestPassColors.Paper)) {
        GuestPassRail(
            userName = user.name,
            unread = repo.unreadCount(),
            selected = screen,
            onPick = { screen = it },
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BranchDayBanner(repo)
            ExpiryStrip(repo)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    GuestPassScreen.HOME -> HomeScreen(repo)
                    GuestPassScreen.BRANCHES -> BranchSelectScreen(repo)
                    GuestPassScreen.SESSIONS -> SessionsScreen(repo)
                    GuestPassScreen.CLIENTS -> ClientsScreen(repo)
                    GuestPassScreen.FINANCE -> FinanceScreen(repo)
                    GuestPassScreen.TEAM -> TeamScreen(repo)
                    GuestPassScreen.MAILBOX -> MailboxScreen(repo, onGoHome = { screen = GuestPassScreen.HOME })
                    GuestPassScreen.AUDIT -> AuditScreen(repo)
                    GuestPassScreen.PROFILE -> ProfileScreen(repo)
                }
            }
            FakeBadgeFooter()
        }
    }
}

@Composable
fun GuestPassFrame(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(GuestPassColors.Paper)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(GuestPassColors.Ink).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("✂ GUEST PASS · $title", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        FakeBadgeFooter()
    }
}

@Composable
fun GuestPassRail(
    userName: String,
    unread: Int,
    selected: GuestPassScreen,
    onPick: (GuestPassScreen) -> Unit,
) {
    Column(
        modifier = Modifier.width(210.dp).fillMaxHeight().background(GuestPassColors.Ink).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("✂ GUEST PASS", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(userName, fontSize = 13.sp, color = Color(0xFFC9BFA9))
        GuestPassScreen.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    if (active) Color(0xFF4A3B28) else Color.Transparent,
                ).clickable { onPick(entry) }.padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.label,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else Color(0xFFC9BFA9),
                    )
                    if (entry == GuestPassScreen.MAILBOX && unread > 0) {
                        Text(
                            "$unread",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = GuestPassColors.Ink,
                            modifier = Modifier.background(Color(0xFFF0D060))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BranchDayBanner(repo: GuestPassRepo) {
    val branchId = repo.selectedBranchId
    val state = repo.dayStates[branchId] ?: DayState.OPEN
    val (label, kind) = when (state) {
        DayState.OPEN -> "OPEN · editable" to StampKind.GREEN
        DayState.PAST -> "PAST · coordinator edits only" to StampKind.AMBER
        DayState.REMITTED -> "REMITTED · covered by snapshot" to StampKind.PLUM
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(GuestPassColors.Stub)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Day 12 · ${repo.branchName(branchId)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuestPassColors.Ink,
                )
                StampChip(label, kind)
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoId("boundary 04:00 Asia/Manila · fake clock ${repo.fakeHour}:00")
                PassButton("Switch day state (fake)", onClick = { repo.cycleDayState(branchId) })
            }
        }
    }
}

@Composable
fun ExpiryStrip(repo: GuestPassRepo) {
    val branchId = repo.selectedBranchId
    if (repo.canEdit(branchId)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(GuestPassColors.StampGreen).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "★ EDIT GRANT · ${repo.branchName(branchId)} · " +
                    "expires 04:00 Asia/Manila (${repo.hoursToExpiry()}h left)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().background(GuestPassColors.Amber).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "VIEW ONLY · ${repo.branchName(branchId)} · no grant — outsider eyes",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
