package com.companyb.companyapp.proto.nightshift

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

// #780 — night-shift shell: dusk boot, login, branch select, rail + banner.

@Composable
fun ProtoNightShiftApp(onBack: () -> Unit) {
    val repo = remember { NightShiftRepo() }
    var user by remember { mutableStateOf<NsUser?>(null) }
    var branch by remember { mutableStateOf<NsBranch?>(null) }
    var booted by remember { mutableStateOf(false) }
    NsRoot {
        if (!booted) {
            NsBoot(repo, onDone = {
                booted = true
                logInfo("proto-night-shift", "night mode engaged")
            })
        } else if (user == null) {
            NsLogin(repo, onPick = { user = it })
        } else if (user!!.locked) {
            NsLockedOut(user!!, onBack = { user = null })
        } else if (branch == null) {
            NsBranchSelect(repo, user!!, onPick = {
                branch = it
                repo.log(user!!.login, "select branch " + it.name)
            }, onBack = { user = null })
        } else {
            NsShell(repo, user!!, branch!!,
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
private fun NsBoot(repo: NightShiftRepo, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(36.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(70.dp))
        NsNote("22:47  ASIA/MANILA  ·  GRAVEYARD FRIENDLY")
        Spacer(Modifier.height(8.dp))
        NsNumeral("22:47", repo.dim, 72)
        Spacer(Modifier.height(8.dp))
        NsTitle("night shift")
        NsNote("true-black OLED  ·  red-shift warm  ·  local only, no network")
        NsRule()
        NsPanel {
            NsText("DIMMER", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
            NsRowButtons {
                listOf("EMBER", "LOW", "MED", "HIGH").forEachIndexed { i, name ->
                    if (repo.dim == i) NsButton(name) { repo.dim = i } else NsGhost(name) { repo.dim = i }
                }
            }
            NsNote("numerals above follow the dimmer. set it before the ward lights go out.")
        }
        Spacer(Modifier.height(14.dp))
        NsButton("START THE NIGHT") { onDone() }
    }
}

@Composable
private fun NsLogin(repo: NightShiftRepo, onPick: (NsUser) -> Unit) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        NsTitle("who is awake?")
        NsNote("pick a crew member. ONBOARDING accounts stay locked until granted a role.")
        NsRule()
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { NsText("search crew", size = 12, color = NsColors.Taupe) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = NsColors.Glow,
                unfocusedTextColor = NsColors.Glow,
                focusedContainerColor = NsColors.Panel,
                unfocusedContainerColor = NsColors.Panel,
                focusedIndicatorColor = NsColors.Ember,
                unfocusedIndicatorColor = NsColors.Edge,
            ),
        )
        Spacer(Modifier.height(10.dp))
        val q = filter.trim()
        repo.users.filter { it.login.contains(q.lowercase()) || it.name.contains(q, ignoreCase = true) }
            .forEach { u ->
                NsPanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NsText(u.name, size = 15, weight = FontWeight.Bold)
                            NsText(
                                u.login + " · " + u.role + " · home " + repo.branchName(u.homeBranchId),
                                size = 12,
                                color = NsColors.Taupe,
                            )
                        }
                        if (u.locked) NsTag("LOCKED", NsColors.Rose) else NsTag(u.role, NsColors.Ember)
                        Spacer(Modifier.width(8.dp))
                        NsGhost("ENTER") { onPick(u) }
                    }
                }
            }
    }
}

@Composable
private fun NsLockedOut(user: NsUser, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        NsTitle("still onboarding")
        NsRule()
        NsPanel {
            NsText(user.name + " (" + user.login + ")", size = 14, weight = FontWeight.Bold)
            NsText("role ONBOARDING carries an empty capability bundle.", size = 13)
            NsNote("branch assignment alone grants nothing. a MANAGER must grant a role on TEAM.")
        }
        Spacer(Modifier.height(12.dp))
        NsGhost("BACK") { onBack() }
    }
}

@Composable
private fun NsBranchSelect(
    repo: NightShiftRepo,
    user: NsUser,
    onPick: (NsBranch) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        NsText("night crew · " + user.name, size = 12, color = NsColors.Taupe)
        NsTitle("pick a branch")
        NsNote("overnight coverage follows the branch, not the building.")
        NsRule()
        repo.branches.forEach { b ->
            NsPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NsText(b.name, size = 15, weight = FontWeight.Bold)
                        NsText(b.kind, size = 12, color = NsColors.Taupe)
                    }
                    NsTag(b.dayStatus.name, nsStatusColor(b.dayStatus.name))
                    Spacer(Modifier.width(8.dp))
                    NsGhost("OPEN") { onPick(b) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NsLink("‹ switch crew", onBack)
    }
}

@Composable
private fun NsShell(
    repo: NightShiftRepo,
    user: NsUser,
    branch: NsBranch,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var screen by remember { mutableStateOf(NsScreen.HOME) }
    // rev keeps every mutating widget recomposing on repo change.
    @Suppress("UNUSED_VARIABLE")
    val rev = repo.rev
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(212.dp).fillMaxHeight()
                .padding(14.dp).verticalScroll(rememberScrollState()),
        ) {
            NsNumeral(if (repo.clockedIn) repo.clockInAt else "--:--", repo.dim, 34)
            NsText(user.name, size = 13, weight = FontWeight.Bold)
            NsText(branch.name + " · " + user.role, size = 11, color = NsColors.Taupe)
            NsRule()
            NsScreen.entries.forEach { s ->
                NsRailButton(s.name, s == screen) { screen = s }
            }
            Spacer(Modifier.weight(1f))
            NsNote("dim " + listOf("EMBER", "LOW", "MED", "HIGH")[repo.dim])
            NsRowButtons {
                listOf("E", "L", "M", "H").forEachIndexed { i, name ->
                    NsLink(if (repo.dim == i) "[$name]" else name) {
                        repo.dim = i
                        repo.touch()
                    }
                }
            }
            NsLink("‹ branches", onBranchChange)
            NsLink("logout", onLogout)
            NsLink("exit", onExit)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .padding(18.dp).verticalScroll(rememberScrollState()),
        ) {
            NsDayBanner(branch)
            when (screen) {
                NsScreen.HOME -> NsHome(repo, user, branch)
                NsScreen.SESSIONS -> NsSessions(repo, user, branch)
                NsScreen.CLIENTS -> NsClients(repo, user)
                NsScreen.FINANCE -> NsFinance(repo, user, branch)
                NsScreen.TEAM -> NsTeam(repo, user)
                NsScreen.MAIL -> NsMail(repo)
                NsScreen.AUDIT -> NsAudit(repo)
                NsScreen.PROFILE -> NsProfile(repo, user, onLogout, onExit)
            }
        }
    }
}

@Composable
private fun NsDayBanner(branch: NsBranch) {
    NsPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NsText(branch.name + " · BRANCH DAY", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
                NsText(
                    "boundary 04:00 Asia/Manila — nights past 04:00 belong to the next day.",
                    size = 12,
                    color = NsColors.Taupe,
                )
                val rule = when (branch.dayStatus) {
                    NsDayStatus.OPEN -> "OPEN: tonight is editable, log freely."
                    NsDayStatus.PAST -> "PAST: read-only unless a Coordinator edits with EDIT_PAST."
                    NsDayStatus.REMITTED -> "REMITTED: sealed. nothing here changes without Undo."
                }
                NsText(rule, size = 12)
            }
            Spacer(Modifier.width(8.dp))
            NsTag(branch.dayStatus.name, nsStatusColor(branch.dayStatus.name))
        }
    }
}
