package com.companyb.companyapp.proto.tradingdesk

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun TdLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(PitBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("COMPANYAPP // TRADING-DESK", style = MaterialTheme.typography.displaySmall, color = TapeGreen)
            Text("FINANCE TERMINAL — FAKE FEED, NO NETWORK", color = TapeFaint)
            Spacer(Modifier.height(20.dp))
            TradingDeskFakeRepo.directory.forEach { u ->
                Row(
                    modifier = Modifier.width(460.dp).padding(vertical = 3.dp).clip(MaterialTheme.shapes.small)
                        .background(if (u.onboarding) PitPanel else BidGreen)
                        .clickable {
                            if (u.onboarding) {
                                onOnboardingDemo()
                            } else {
                                TradingDeskFakeRepo.currentUser.value = u
                                TradingDeskFakeRepo.clockedIn.value = true
                                TradingDeskFakeRepo.clockedBranchId.value = u.homeBranchId
                                TradingDeskFakeRepo.stamp(u.name, "LOGIN", "desk terminal")
                                onLogin()
                            }
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(u.name, style = MaterialTheme.typography.titleMedium, color = TapePaper, modifier = Modifier.weight(1f))
                    Text(u.role, style = MaterialTheme.typography.labelSmall, color = if (u.onboarding) TapeRed else TapeGreen)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("ONBOARDING ROW OPENS THE LOCKED-OUT DEMO", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
        }
    }
}

@Composable
internal fun TdOnboardingLocked(onGrant: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(PitBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("⛔ ACCOUNT LOCKED: ONBOARDING", style = MaterialTheme.typography.titleLarge, color = TapeRed)
            Spacer(Modifier.height(8.dp))
            Text("ZERO CAPABILITIES — ROLE BUNDLE EMPTY.", color = TapePaper)
            Text("EVEN WITH A BRANCH ASSIGNMENT, NOTHING DERIVES.", color = TapePaper)
            Text("ORDER ENTRY BLOCKED UNTIL MANAGE_USERS GRANTS A REAL ROLE.", color = TapeFaint)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TdAction("SIMULATE MANAGE_USERS GRANT") {
                    TradingDeskFakeRepo.currentUser.value = TradingDeskFakeRepo.directory.first { !it.onboarding }
                    TradingDeskFakeRepo.stamp("M. Sy", "GRANT_ROLE", "u-onb -> Practitioner")
                    onGrant()
                }
                TdGhost("BACK TO LOGIN", onClick = onBack)
            }
        }
    }
}

@Composable
internal fun TdBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(PitBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("SELECT VENUE", style = MaterialTheme.typography.titleLarge, color = TapeGreen)
            Spacer(Modifier.height(12.dp))
            TradingDeskFakeRepo.branches.forEach { b ->
                val open = TradingDeskFakeRepo.sessions.count { it.branchId == b.id && it.status == TdSessionStatus.PENDING }
                Column(
                    modifier = Modifier.width(520.dp).padding(vertical = 4.dp).clip(MaterialTheme.shapes.small)
                        .background(PitPanel).clickable { onPick(b.id) }.padding(14.dp),
                ) {
                    Text("${b.code} // ${b.name}", style = MaterialTheme.typography.titleMedium, color = TapePaper)
                    Text(
                        "${b.kind} — $open OPEN ORDERS — GROSS ₱${TradingDeskFakeRepo.dayGross(b.id)}",
                        color = TapeFaint,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TdGhost("BACK", onClick = onBack)
        }
    }
}
