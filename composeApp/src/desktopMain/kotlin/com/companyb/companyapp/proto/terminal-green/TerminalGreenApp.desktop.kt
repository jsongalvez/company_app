package com.companyb.companyapp.proto.terminalgreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

// #768 — terminal-green console shell: boot login, branch select, rail + banner.

@Composable
fun ProtoTerminalGreenApp(onBack: () -> Unit) {
    val repo = remember { TerminalGreenRepo() }
    var user by remember { mutableStateOf<TgUser?>(null) }
    var branch by remember { mutableStateOf<TgBranch?>(null) }
    var booted by remember { mutableStateOf(false) }
    TgRoot {
        if (!booted) {
            TgBoot(onDone = {
                booted = true
                logInfo("proto-terminal-green", "console attached")
            })
        } else if (user == null) {
            TgLogin(repo.users, onPick = { user = it })
        } else if (user!!.locked) {
            TgLockedOut(user!!, onBack = { user = null })
        } else if (branch == null) {
            TgBranchSelect(repo, user!!, onPick = {
                branch = it
                repo.log(user!!.login, "select branch " + it.name)
            }, onBack = { user = null })
        } else {
            TgShell(repo, user!!, branch!!,
                onBranchChange = { branch = null },
                onLogout = {
                    repo.log(user!!.login, "logout")
                    user = null
                    branch = null
                },
                onExit = onBack)
        }
    }
}

@Composable
private fun TgBoot(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(60.dp))
        TgText("COMPANYAPP BIOS v2.6 — PHOSPHOR TERMINAL", size = 12, color = TgColors.Faint)
        TgText("MEM CHECK ............ 640K OK", size = 13)
        TgText("TUBE WARMUP .......... GREEN OK", size = 13)
        TgText("DAY BOUNDARY ......... 04:00 ASIA/MANILA", size = 13)
        TgText("NET LINK ............. DOWN (LOCAL ONLY)", size = 13, color = TgColors.Amber)
        TgRule()
        TgTitle("COMPANYAPP // FIELD CONSOLE")
        TgNote("fake-data build. no network. all records local.")
        Spacer(Modifier.height(12.dp))
        TgButton("CONNECT TTY0") { onDone() }
    }
}

@Composable
private fun TgLogin(users: List<TgUser>, onPick: (TgUser) -> Unit) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        TgTitle("LOGIN")
        TgNote("pick an operator. ONBOARDING accounts are locked by empty role bundle.")
        TgRule()
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { TgText("filter login_", size = 12, color = TgColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = TgColors.Phosphor,
                unfocusedTextColor = TgColors.Phosphor,
                focusedContainerColor = TgColors.Panel,
                unfocusedContainerColor = TgColors.Panel,
                focusedIndicatorColor = TgColors.Phosphor,
                unfocusedIndicatorColor = TgColors.PanelEdge,
            ),
        )
        Spacer(Modifier.height(10.dp))
        val q = filter.trim()
        users.filter { it.login.contains(q.lowercase()) || it.name.contains(q, ignoreCase = true) }
            .forEach { u ->
                TgPanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TgText(
                                "$ " + u.login + "  (" + u.role + ")",
                                size = 14,
                                weight = FontWeight.Bold,
                            )
                            TgText(
                                u.name + " :: home=" + u.homeBranchId + " :: caps=" + u.capabilities.size,
                                size = 12,
                                color = TgColors.Muted,
                            )
                        }
                        if (u.locked) TgTag("LOCKED", TgColors.Red) else TgTag(u.role, TgColors.PhosphorDim)
                        Spacer(Modifier.width(8.dp))
                        TgGhost("LOGIN") { onPick(u) }
                    }
                }
            }
    }
}

@Composable
private fun TgLockedOut(user: TgUser, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        TgTitle("ACCESS DENIED")
        TgRule()
        TgPanel {
            TgText("user: " + user.login + " (" + user.name + ")", size = 14)
            TgText("role: ONBOARDING — role bundle EMPTY", size = 13, color = TgColors.Red)
            TgText("caps derived: NONE. branch assignment alone grants nothing.", size = 13)
            TgNote("a MANAGER with MANAGE_USERS must grant a real role. see TEAM screen.")
        }
        Spacer(Modifier.height(12.dp))
        TgGhost("BACK TO LOGIN") { onBack() }
    }
}

@Composable
private fun TgBranchSelect(repo: TerminalGreenRepo, user: TgUser, onPick: (TgBranch) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        TgText("operator: " + user.login + " [" + user.role + "]", size = 12, color = TgColors.Muted)
        TgTitle("SELECT BRANCH")
        TgNote("branch day boundary 04:00 Asia/Manila. status shown live per branch.")
        TgRule()
        repo.branches.forEach { b ->
            TgPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TgText("▸ " + b.name, size = 15, weight = FontWeight.Bold)
                        TgText("kind=" + b.kind + "  id=" + b.id, size = 12, color = TgColors.Muted)
                    }
                    TgTag(b.dayStatus.name, TgDayColor(b.dayStatus))
                    Spacer(Modifier.width(8.dp))
                    TgButton("OPEN") { onPick(b) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TgGhost("LOGOUT") { onBack() }
    }
}

@Composable
private fun TgShell(
    repo: TerminalGreenRepo,
    user: TgUser,
    branch: TgBranch,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var screen by remember { mutableStateOf(TgScreen.HOME) }
    var clockedIn by remember { mutableStateOf(false) }
    val unread = repo.notices.count { !it.read }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(208.dp).fillMaxHeight().background(TgColors.Panel)
                .padding(12.dp).verticalScroll(rememberScrollState()),
        ) {
            TgText("▓▓ TTY0", size = 15, weight = FontWeight.Bold, color = TgColors.Cursor)
            TgText(user.login + "@" + branch.name, size = 11, color = TgColors.Muted)
            TgRule()
            TgScreen.entries.forEach { s ->
                val label = if (s == TgScreen.MAIL && unread > 0) "MAIL ($unread)" else s.name
                TgRowLink(label, screen == s) { screen = s }
            }
            Spacer(Modifier.weight(1f))
            TgRule()
            TgLink("⌁ switch branch") { onBranchChange() }
            TgLink("⏻ exit console") { onExit() }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(TgColors.PanelEdge))
        Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp).verticalScroll(rememberScrollState())) {
            TgDayBanner(branch)
            Spacer(Modifier.height(10.dp))
            when (screen) {
                TgScreen.HOME -> TgHome(repo, user, branch, clockedIn, onClock = {
                    clockedIn = !clockedIn
                    repo.log(user.login, if (clockedIn) "clock-in " + branch.name else "clock-out " + branch.name)
                })
                TgScreen.SESSIONS -> TgSessions(repo, user, branch)
                TgScreen.CLIENTS -> TgClients(repo, user)
                TgScreen.FINANCE -> TgFinance(repo, user, branch)
                TgScreen.TEAM -> TgTeam(repo, user)
                TgScreen.MAIL -> TgMail(repo, user)
                TgScreen.AUDIT -> TgAudit(repo)
                TgScreen.PROFILE -> TgProfile(repo, user, branch, clockedIn, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun TgDayBanner(branch: TgBranch) {
    val editNote = when (branch.dayStatus) {
        TgDayStatus.OPEN -> "editable by all on-duty users"
        TgDayStatus.PAST -> "sole editor: Coordinator (PAST record)"
        TgDayStatus.REMITTED -> "sealed by remittance; Coordinator-only edits, flagged audit"
    }
    Row(
        Modifier.fillMaxWidth().background(TgColors.Panel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TgTag("DAY:" + branch.dayStatus.name, TgDayColor(branch.dayStatus))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            TgText(
                branch.name + " :: boundary 04:00 ASIA/MANILA",
                size = 12,
                weight = FontWeight.Bold,
            )
            TgText(editNote, size = 11, color = TgColors.Muted)
        }
    }
}
