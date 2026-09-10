package com.companyb.companyapp.proto.comicbold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import com.companyb.companyapp.util.logInfo

private enum class BoomAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class BoomTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun ComicBoldProtoApp() {
    ComicBoldTheme {
        var phase by remember { mutableStateOf(BoomAuthPhase.LOGIN) }
        val repo = remember { ComicBoldFakeRepo() }
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            BoomHalftoneStrip()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    BoomAuthPhase.LOGIN -> BoomLogin(
                        onLogin = {
                            logInfo("ComicBold", "login POW-in")
                            phase = BoomAuthPhase.BRANCH_SELECT
                        },
                        onOnboarding = { phase = BoomAuthPhase.ONBOARDING_LOCKED },
                    )
                    BoomAuthPhase.ONBOARDING_LOCKED -> BoomOnboardingLocked(
                        onBack = { phase = BoomAuthPhase.LOGIN },
                    )
                    BoomAuthPhase.BRANCH_SELECT -> BoomBranchSelect(
                        repo = repo,
                        onPick = { phase = BoomAuthPhase.APP },
                        onBack = { phase = BoomAuthPhase.LOGIN },
                    )
                    BoomAuthPhase.APP -> BoomShell(
                        repo = repo,
                        onLogout = { phase = BoomAuthPhase.LOGIN },
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoomHalftoneStrip() {
    val dot = BoomInk
    val bg = BoomBurst
    Box(modifier = Modifier.fillMaxWidth().height(22.dp).background(bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 18f
            var y = 8f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 8f else 17f
                while (x < size.width) {
                    drawCircle(color = dot, radius = 3.2f, center = androidx.compose.ui.geometry.Offset(x, y))
                    x += step
                }
                y += 12f
                row += 1
            }
        }
    }
}

@Composable
private fun BoomShell(repo: ComicBoldFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(BoomTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(248.dp).background(BoomInk).padding(16.dp),
        ) {
            Text(text = "POW!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = BoomBurst)
            Text(
                text = "COMIC-BOLD",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Text(text = repo.branchName(repo.branchId.value), fontSize = 13.sp, color = BoomBurstSoft)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (repo.clockedIn.value) ">>> CLOCKED IN!" else "/// CLOCKED OUT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (repo.clockedIn.value) BoomKapowSoft else BoomPowSoft,
            )
            Spacer(Modifier.height(16.dp))
            BoomTab.entries.forEach { t ->
                val on = tab == t
                val unread = t == BoomTab.MAIL && repo.notes.any { !it.read }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (on) BoomBurst else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tab = t }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "▸ ${t.name}" + if (unread) " (!!)" else "",
                        fontWeight = FontWeight.Black,
                        color = if (on) BoomInk else androidx.compose.ui.graphics.Color.White,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(text = "DAY: ${repo.dayStatus.value.name}", fontSize = 13.sp, color = BoomBurstSoft)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (tab) {
                    BoomTab.HOME -> BoomHome(repo)
                    BoomTab.SESSIONS -> BoomSessions(repo)
                    BoomTab.CLIENTS -> BoomClients(repo)
                    BoomTab.FINANCE -> BoomFinance(repo)
                    BoomTab.TEAM -> BoomTeam(repo)
                    BoomTab.MAIL -> BoomMailbox(repo)
                    BoomTab.AUDIT -> BoomAuditLog(repo)
                    BoomTab.PROFILE -> BoomProfile(repo, onLogout)
                }
            }
        }
    }
}
