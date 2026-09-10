package com.companyb.companyapp.proto.whiteboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #844 — whiteboard shell: cork-frame chrome, branch-day banner, magnet tab rail.

enum class WhiteboardScreen(val title: String) {
    BOARD("Board"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    MONEY("Money"),
    CREW("Crew"),
    BELL("Bell"),
    LEDGER("Ledger"),
    ME("Me"),
}

@Composable
fun WhiteboardApp(onBack: () -> Unit) {
    val repo = remember { WhiteboardFakeRepo() }
    var authed by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(WhiteboardScreen.BOARD) }

    LaunchedEffect(Unit) { logInfo("Whiteboard", "whiteboard prototype launched") }

    Column(Modifier.fillMaxSize().background(BoardColors.PaperDeep)) {
        WhiteboardTopFrame(repo = repo)
        WhiteboardDayBanner(repo = repo)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!authed) {
                WhiteboardGate(repo = repo, onAuthed = { authed = true })
            } else if (repo.currentUser?.role == BoardRole.ONBOARDING) {
                WhiteboardLocked(repo = repo, onLogout = { authed = false })
            } else {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (screen) {
                        WhiteboardScreen.BOARD -> WhiteboardBoard(repo = repo, go = { screen = it })
                        WhiteboardScreen.SESSIONS -> WhiteboardSessions(repo = repo)
                        WhiteboardScreen.CLIENTS -> WhiteboardClients(repo = repo)
                        WhiteboardScreen.MONEY -> WhiteboardMoney(repo = repo)
                        WhiteboardScreen.CREW -> WhiteboardCrew(repo = repo)
                        WhiteboardScreen.BELL -> WhiteboardBell(repo = repo)
                        WhiteboardScreen.LEDGER -> WhiteboardLedger(repo = repo)
                        WhiteboardScreen.ME -> WhiteboardMe(
                            repo = repo,
                            onLogout = {
                                repo.logout()
                                authed = false
                                screen = WhiteboardScreen.BOARD
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        if (authed && repo.currentUser?.role != BoardRole.ONBOARDING) {
            WhiteboardTabRail(
                screen = screen,
                unread = repo.notices.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun WhiteboardTopFrame(repo: WhiteboardFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(BoardColors.Frame)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "TEAM WHITEBOARD",
                color = BoardColors.MagnetYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.standupLine,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        val user = repo.currentUser
        Column(horizontalAlignment = Alignment.End) {
            Text(
                user?.name ?: "Not signed in",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (user == null) {
                    "pick a marker to sign in"
                } else if (repo.clockedIn) {
                    "on duty · ${repo.branchName(repo.clockBranchId)}"
                } else {
                    "off duty · ${user.role}"
                },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WhiteboardDayBanner(repo: WhiteboardFakeRepo) {
    val accent = repo.dayStatus.accent()
    Column(
        Modifier.fillMaxWidth().background(accent)
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BRANCH DAY ${repo.operationalDate} · ${repo.dayStatus}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            BoardDayStatus.entries.forEach { option ->
                val active = option == repo.dayStatus
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) Color.White else Color.White.copy(alpha = 0.22f))
                        .clickable { repo.dayStatus = option }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        option.name,
                        color = if (active) accent else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            "Day boundary 04:00 Asia/Manila — OPEN stays editable until 04:00 the next morning, then turns PAST. REMITTED needs a Coordinator edit.",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WhiteboardGate(repo: WhiteboardFakeRepo, onAuthed: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoardPanel {
            MarkerTitle("Grab a marker", BoardColors.MarkerPurple)
            Spacer(Modifier.height(6.dp))
            BoardNote("Fake sign-in only — tap any teammate card. No passwords, no network.")
            Spacer(Modifier.height(10.dp))
            repo.users.forEach { user ->
                MagnetCard(
                    magnet = when (user.role) {
                        BoardRole.ONBOARDING -> BoardColors.InkSoft
                        BoardRole.PRACTITIONER -> BoardColors.MagnetTeal
                        BoardRole.COORDINATOR -> BoardColors.MarkerBlue
                        BoardRole.MANAGER -> BoardColors.MagnetYellow
                        BoardRole.ACCOUNTANT -> BoardColors.MagnetPink
                    },
                    onClick = {
                        repo.login(user.id)
                        onAuthed()
                    },
                ) {
                    Text(user.name, color = BoardColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${user.role} · home ${repo.branchName(user.homeBranchId)}",
                        color = BoardColors.InkSoft,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        BoardPanel(tape = BoardColors.MagnetSky) {
            MarkerTitle("Pick a branch", BoardColors.MarkerGreen)
            Spacer(Modifier.height(6.dp))
            BoardNote("Cards pin to the branch you stand in. Relief pins stick only for the day.")
            Spacer(Modifier.height(10.dp))
            repo.branches.forEach { branch ->
                val active = branch.id == repo.currentBranchId
                MagnetCard(
                    magnet = if (active) BoardColors.MarkerGreen else BoardColors.CardLine,
                    onClick = { repo.currentBranchId = branch.id },
                ) {
                    Text(
                        "${if (active) "★ " else ""}${branch.name}",
                        color = BoardColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text("${branch.kind} · ${branch.standupLine}", color = BoardColors.InkSoft, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WhiteboardLocked(repo: WhiteboardFakeRepo, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoardPanel(tape = BoardColors.MagnetPink) {
            MarkerTitle("Locked out", BoardColors.MarkerRed)
            Spacer(Modifier.height(6.dp))
            Text(
                "${repo.currentUser?.name ?: "New teammate"} is ONBOARDING — the role bundle is empty, so no capabilities derive yet, even with a branch assignment.",
                color = BoardColors.Ink,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            BoardNote("A MANAGER grants a real role with MANAGE_USERS. Nothing else on this board is clickable until then.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoardGhost(text = "Back to markers") {
                    repo.logout()
                    onLogout()
                }
            }
        }
    }
}

@Composable
private fun WhiteboardTabRail(
    screen: WhiteboardScreen,
    unread: Int,
    onPick: (WhiteboardScreen) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(BoardColors.Frame)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhiteboardScreen.entries.forEach { option ->
            val active = option == screen
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) BoardColors.MagnetYellow else Color.Transparent)
                    .clickable { onPick(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (option == WhiteboardScreen.BELL && unread > 0) {
                        "Bell ($unread)"
                    } else {
                        option.title
                    },
                    color = if (active) BoardColors.Frame else Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Exit",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp).clickable(onClick = onBack),
        )
    }
}
