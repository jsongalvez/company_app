package com.companyb.companyapp.proto.roundscart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

private enum class RcPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class RcTab { ROUTE, CHART, CLIENTS, DRAWER, TEAM, PAGER, LOGBOOK, PROFILE }

@Composable
fun ProtoRoundsCartApp(onBack: () -> Unit = {}) {
    RoundsCartTheme {
        var phase by remember { mutableStateOf(RcPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(CartSteel)) {
            when (phase) {
                RcPhase.LOGIN -> RcLogin(
                    onLogin = { phase = RcPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = RcPhase.ONBOARDING_LOCKED },
                )
                RcPhase.ONBOARDING_LOCKED -> RcOnboardingLocked(
                    onGrant = { phase = RcPhase.BRANCH_SELECT },
                    onBack = { phase = RcPhase.LOGIN },
                )
                RcPhase.BRANCH_SELECT -> RcBranchSelect(
                    onPick = { branchId ->
                        RoundsCartFakeRepo.clockedBranchId.value = branchId
                        val first = RoundsCartFakeRepo.routeStops()
                            .firstOrNull { it.status == RcSessionStatus.PENDING }?.id
                        if (first != null) RoundsCartFakeRepo.chartSessionId.value = first
                        phase = RcPhase.APP
                    },
                    onBack = { phase = RcPhase.LOGIN },
                )
                RcPhase.APP -> RcShell(onLogout = { phase = RcPhase.LOGIN }, onExit = onBack)
            }
        }
    }
}

@Composable
internal fun RcShell(onLogout: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(RcTab.ROUTE) }
    Column(modifier = Modifier.fillMaxSize()) {
        RcDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RcRail(current = tab, onPick = { tab = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(ChartPaper)) {
                when (tab) {
                    RcTab.ROUTE -> RcRouteTab(onOpenChart = { tab = RcTab.CHART })
                    RcTab.CHART -> RcChartTab()
                    RcTab.CLIENTS -> RcClientsTab()
                    RcTab.DRAWER -> RcDrawerTab()
                    RcTab.TEAM -> RcTeamTab()
                    RcTab.PAGER -> RcPagerTab()
                    RcTab.LOGBOOK -> RcLogbookTab()
                    RcTab.PROFILE -> RcProfileTab(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

@Composable
internal fun RcDayBanner() {
    val day by RoundsCartFakeRepo.dayState
    val branch = RoundsCartFakeRepo.branchName(RoundsCartFakeRepo.clockedBranchId.value)
    Row(
        modifier = Modifier.fillMaxWidth().background(CartSteel2).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ROUNDS-CART", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FD8D2))
        Spacer(Modifier.width(12.dp))
        Text(branch, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.width(12.dp))
        RcDayState.entries.forEach { state ->
            val selected = state == day
            Box(
                modifier = Modifier.padding(end = 6.dp).clip(MaterialTheme.shapes.small)
                    .background(if (selected) CartTeal else CartSteel)
                    .clickable {
                        RoundsCartFakeRepo.dayState.value = state
                        RoundsCartFakeRepo.stamp(
                            RoundsCartFakeRepo.currentUser.value.name,
                            "DAY_STATE",
                            "Branch Day -> $state",
                        )
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    state.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else Color(0xFFB9C6C4),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Boundary 04:00 Asia/Manila",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB9C6C4),
        )
    }
}

@Composable
internal fun RcRail(current: RcTab, onPick: (RcTab) -> Unit) {
    val unread = RoundsCartFakeRepo.unreadCount
    val groups = listOf(
        "WARD ROUND" to listOf(RcTab.ROUTE, RcTab.CHART, RcTab.CLIENTS),
        "CART" to listOf(RcTab.DRAWER, RcTab.TEAM),
        "HOUSE" to listOf(RcTab.PAGER, RcTab.LOGBOOK, RcTab.PROFILE),
    )
    Column(
        modifier = Modifier.width(168.dp).fillMaxHeight().background(CartRail)
            .verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
    ) {
        Text(
            "DOCTOR ROUNDS",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8FD8D2),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        groups.forEach { (label, tabs) ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6E8583),
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
            )
            tabs.forEach { tab ->
                val selected = tab == current
                val tag = when (tab) {
                    RcTab.ROUTE -> "ROUTE"
                    RcTab.CHART -> "CHART"
                    RcTab.CLIENTS -> "CLIENTS"
                    RcTab.DRAWER -> "DRAWER"
                    RcTab.TEAM -> "TEAM"
                    RcTab.PAGER -> if (unread > 0) "PAGER ($unread)" else "PAGER"
                    RcTab.LOGBOOK -> "LOGBOOK"
                    RcTab.PROFILE -> "PROFILE"
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) CartTeal else Color.Transparent)
                        .clickable { onPick(tab) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Color(0xFFC9D6D3),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RcLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ROUNDS-CART", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FD8D2))
        Text(
            "Push the cart. Next Client first, chart at hand.",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            "A Practitioner route through the day: stops in stop order, the chart clipped to the cart, " +
                "the drawer counted at the end. Fake directory — pick who is pushing.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFB9C6C4),
        )
        Spacer(Modifier.height(16.dp))
        RoundsCartFakeRepo.directory.forEach { user ->
            Row(
                modifier = Modifier.width(560.dp).clip(MaterialTheme.shapes.medium).background(CartSteel2)
                    .border(1.dp, CartTeal, MaterialTheme.shapes.medium)
                    .clickable {
                        RoundsCartFakeRepo.currentUser.value = user
                        RoundsCartFakeRepo.stamp(user.name, "LOGIN", user.role)
                        if (user.onboarding) onOnboardingDemo() else onLogin()
                    }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "${user.role} · home ${RoundsCartFakeRepo.branchName(user.homeBranchId)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB9C6C4),
                    )
                }
                Text("PUSH →", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8FD8D2))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun RcOnboardingLocked(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ONBOARDING · CART LOCKED", style = MaterialTheme.typography.labelLarge, color = CartAmber)
        Text("The wheels are chocked.", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text(
            "ONBOARDING holds zero capabilities — no route, no chart, no drawer — even with a Branch " +
                "assignment. Nothing derives until MANAGE_USERS grants a real role.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFB9C6C4),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    RoundsCartFakeRepo.stamp("Ramon Sy", "GRANT_ROLE", "New Hire Ona -> Practitioner")
                    onGrant()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CartTeal),
            ) { Text("Simulate MANAGE_USERS grant") }
            OutlinedButton(onClick = onBack) { Text("Back to sign-in", color = Color.White) }
        }
    }
}

@Composable
internal fun RcBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PICK TODAY'S WARD", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FD8D2))
        Text("Where does the cart roll?", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(12.dp))
        RoundsCartFakeRepo.branches.forEach { branch ->
            val waiting = RoundsCartFakeRepo.waitingCount(branch.id)
            Row(
                modifier = Modifier.width(560.dp).clip(MaterialTheme.shapes.medium).background(CartSteel2)
                    .border(1.dp, CartTeal, MaterialTheme.shapes.medium)
                    .clickable { onPick(branch.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(branch.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(branch.kind, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9C6C4))
                }
                Text(
                    "$waiting waiting",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF8FD8D2),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onBack) { Text("Back", color = Color.White) }
    }
}

@Composable
internal fun RcCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ChartCard)
            .border(1.dp, ChartEdge, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
internal fun RcSmallButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(CartTealDeep)
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
internal fun RcGhostButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small).border(1.dp, CartTealDeep, MaterialTheme.shapes.small)
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CartTealDeep)
    }
}
