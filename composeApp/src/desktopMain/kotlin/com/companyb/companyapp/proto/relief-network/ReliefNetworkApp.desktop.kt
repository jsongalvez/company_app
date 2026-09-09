package com.companyb.companyapp.proto.reliefnetwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.companyb.companyapp.util.logInfo

// #772 — relief-network entry: pass -> branch -> market shell. Fake data only.

@Composable
fun ProtoReliefNetworkApp(onBack: () -> Unit) {
    val repo = remember { RnRepo() }
    var user by remember { mutableStateOf<RnUser?>(null) }
    var branch by remember { mutableStateOf<RnBranch?>(null) }
    var rail by remember { mutableStateOf("board") }
    RnMarketTheme {
        Box(modifier = Modifier.fillMaxSize().background(RnBoard)) {
            val currentUser = user
            val currentBranch = branch
            when {
                currentUser == null -> RnLogin(repo, onPick = { user = it })
                currentUser.locked -> RnLockedOut(currentUser, onBack = { user = null })
                currentBranch == null ->
                    RnBranchSelect(
                        repo,
                        currentUser,
                        onPick = {
                            branch = it
                            rail = "board"
                            logInfo("ReliefNetworkApp", "${currentUser.login} entered ${it.id}")
                        },
                        onBack = { user = null },
                    )
                else ->
                    RnShellChrome(
                        repo = repo,
                        user = currentUser,
                        branch = currentBranch,
                        rail = rail,
                        onRail = { rail = it },
                        onBranchChange = { branch = null },
                        onLogout = {
                            logInfo("ReliefNetworkApp", "${currentUser.login} logged out")
                            user = null
                            branch = null
                        },
                    ) {
                        when (rail) {
                            "board" -> RnBoard(repo, currentUser, currentBranch)
                            "sessions" -> RnSessions(repo, currentUser, currentBranch)
                            "clients" -> RnClients(repo)
                            "finance" -> RnFinance(repo, currentUser)
                            "team" -> RnTeam(repo, currentUser)
                            "mail" -> RnMail(repo)
                            "audit" -> RnAudit(repo)
                            else ->
                                RnProfile(
                                    repo,
                                    currentUser,
                                    currentBranch,
                                    onLogout = {
                                        user = null
                                        branch = null
                                    },
                                    onExit = onBack,
                                )
                        }
                    }
            }
        }
    }
}
