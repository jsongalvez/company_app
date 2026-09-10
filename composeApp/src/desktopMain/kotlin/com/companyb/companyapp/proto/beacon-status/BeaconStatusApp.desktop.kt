package com.companyb.companyapp.proto.beaconstatus

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp

private enum class BsPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class BsTab { BEACON, SIGNALS, LEDGER, CLIENTS, VAULT, CREW, MAIL, LOGBOOK, PROFILE }

@Composable
fun ProtoBeaconStatusApp(onBack: () -> Unit = {}) {
    BeaconStatusTheme {
        var phase by remember { mutableStateOf(BsPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(HarborNight)) {
            when (phase) {
                BsPhase.LOGIN -> {
                    BsLogin(
                        onLogin = { phase = BsPhase.BRANCH_SELECT },
                        onOnboardingDemo = { phase = BsPhase.ONBOARDING_LOCKED },
                    )
                }

                BsPhase.ONBOARDING_LOCKED -> {
                    BsOnboardingLocked(
                        onGrant = { phase = BsPhase.BRANCH_SELECT },
                        onBack = { phase = BsPhase.LOGIN },
                    )
                }

                BsPhase.BRANCH_SELECT -> {
                    BsBranchSelect(
                        onPick = { branchId ->
                            BeaconStatusFakeRepo.clockedBranchId.value = branchId
                            phase = BsPhase.APP
                        },
                        onBack = { phase = BsPhase.LOGIN },
                    )
                }

                BsPhase.APP -> {
                    BsShell(onLogout = { phase = BsPhase.LOGIN }, onExit = onBack)
                }
            }
        }
    }
}

@Composable
internal fun BsShell(
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var tab by remember { mutableStateOf(BsTab.BEACON) }
    val unread = BeaconStatusFakeRepo.mailbox.count { !it.read }
    Column(modifier = Modifier.fillMaxSize()) {
        BsDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BsHarborRail(current = tab, unread = unread, onPick = { tab = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(HarborNight)) {
                when (tab) {
                    BsTab.BEACON -> BsBeaconTab()
                    BsTab.SIGNALS -> BsSignalsTab()
                    BsTab.LEDGER -> BsLedgerTab()
                    BsTab.CLIENTS -> BsClientsTab()
                    BsTab.VAULT -> BsVaultTab()
                    BsTab.CREW -> BsCrewTab()
                    BsTab.MAIL -> BsMailTab()
                    BsTab.LOGBOOK -> BsLogbookTab()
                    BsTab.PROFILE -> BsProfileTab(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

@Composable
internal fun BsDayBanner() {
    val day by BeaconStatusFakeRepo.dayState
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    HarborDeep,
                ).border(1.dp, HarborEdge)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(BeaconGold))
            Spacer(Modifier.width(10.dp))
            Text("BEACON-STATUS · HARBOR OVERVIEW", style = MaterialTheme.typography.labelLarge, color = BeaconGold)
            Spacer(Modifier.width(12.dp))
            Text(
                BeaconStatusFakeRepo.branchName(BeaconStatusFakeRepo.clockedBranchId.value),
                style = MaterialTheme.typography.titleMedium,
                color = FogWhite,
            )
            Spacer(Modifier.weight(1f))
            BsDayState.values().forEach { state ->
                val selected = day == state
                val label =
                    when (state) {
                        BsDayState.OPEN -> "OPEN"
                        BsDayState.PAST -> "PAST"
                        BsDayState.REMITTED -> "REMITTED"
                    }
                Box(
                    modifier =
                        Modifier
                            .padding(end = 6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (selected) BeaconGold else HarborPanel)
                            .clickable { BeaconStatusFakeRepo.dayState.value = state }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) HarborNight else FogDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Branch Day ${day.name} · boundary 04:00 Asia/Manila · one lamp per Branch, red beats amber beats green",
            style = MaterialTheme.typography.bodySmall,
            color = FogDim,
        )
    }
}

@Composable
internal fun BsHarborRail(
    current: BsTab,
    unread: Int,
    onPick: (BsTab) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(248.dp)
                .fillMaxHeight()
                .background(HarborDeep)
                .border(1.dp, HarborEdge)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("HARBOR CHART", style = MaterialTheme.typography.labelLarge, color = BeaconGold)
        BeaconStatusFakeRepo.branches.forEach { branch ->
            val (lamp, reason) = BeaconStatusFakeRepo.lampFor(branch.id)
            val lampColor =
                when (lamp) {
                    BsLamp.GREEN -> LampGreen
                    BsLamp.AMBER -> LampAmber
                    BsLamp.RED -> LampRed
                }
            val berthed = BeaconStatusFakeRepo.clockedBranchId.value == branch.id
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (berthed) HarborCard else HarborDeep,
                        ).border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                        .clickable { BeaconStatusFakeRepo.clockedBranchId.value = branch.id }
                        .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(lampColor))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                        Text(
                            branch.berth + " · " + branch.kind,
                            style = MaterialTheme.typography.labelMedium,
                            color = FogDim,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(reason, style = MaterialTheme.typography.bodySmall, color = FogDim)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("WATCH DECK", style = MaterialTheme.typography.labelLarge, color = BeaconGold)
        BsTab.values().forEach { tab ->
            val selected = tab == current
            val badge = if (tab == BsTab.MAIL && unread > 0) " ($unread)" else ""
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) BeaconWash else HarborDeep)
                        .clickable { onPick(tab) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    tab.name + badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) BeaconGold else FogDim,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "GREEN open-clear · AMBER past/queue · RED sealed/surge",
            style = MaterialTheme.typography.bodySmall,
            color = FogDim,
        )
    }
}

@Composable
internal fun BsLogin(
    onLogin: () -> Unit,
    onOnboardingDemo: () -> Unit,
) {
    var picked by remember { mutableStateOf(BeaconStatusFakeRepo.directory[0]) }
    BsCenterCard(title = "HARBOR BEACON", subtitle = "Sign the watch bill — fake directory, no network.") {
        BeaconStatusFakeRepo.directory.forEach { user ->
            val selected = picked.id == user.id
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) HarborCard else HarborDeep)
                        .border(1.dp, if (selected) BeaconGold else HarborEdge, MaterialTheme.shapes.small)
                        .clickable { picked = user }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("${user.name} · ${user.role}", style = MaterialTheme.typography.bodyLarge, color = FogWhite)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                BeaconStatusFakeRepo.currentUser.value = picked
                if (picked.onboarding) onOnboardingDemo() else onLogin()
            },
            colors = ButtonDefaults.buttonColors(containerColor = BeaconGold, contentColor = HarborNight),
        ) {
            Text("Light the lamp")
        }
    }
}

@Composable
internal fun BsOnboardingLocked(
    onGrant: () -> Unit,
    onBack: () -> Unit,
) {
    BsCenterCard(title = "LAMPLIGHTER LOCKED", subtitle = "ONBOARDING holds zero capabilities — every hatch shut.") {
        Text("No lamp, no berth, no drawer. The harbor does not know you yet.", color = FogDim)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = BeaconGold, contentColor = HarborNight),
        ) {
            Text("Simulate MANAGE_USERS grant")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onBack) { Text("Back to sign-in", color = FogWhite) }
    }
}

@Composable
internal fun BsBranchSelect(
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    BsCenterCard(title = "PICK YOUR BERTH", subtitle = "One Branch per watch. The rail keeps every lamp live.") {
        BeaconStatusFakeRepo.branches.forEach { branch ->
            val (lamp, reason) = BeaconStatusFakeRepo.lampFor(branch.id)
            val lampColor =
                when (lamp) {
                    BsLamp.GREEN -> LampGreen
                    BsLamp.AMBER -> LampAmber
                    BsLamp.RED -> LampRed
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(HarborDeep)
                        .border(1.dp, HarborEdge, MaterialTheme.shapes.small)
                        .clickable { onPick(branch.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(lampColor))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(branch.name, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                    Text("${branch.kind} · $reason", style = MaterialTheme.typography.bodySmall, color = FogDim)
                }
                Text(
                    "${BeaconStatusFakeRepo.pendingFor(branch.id)} PENDING",
                    style = MaterialTheme.typography.labelLarge,
                    color = BeaconGold,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(onClick = onBack) { Text("Back", color = FogWhite) }
    }
}

@Composable
internal fun BsCenterCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(HarborNight), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(HarborDeep)
                    .border(1.dp, HarborEdge, MaterialTheme.shapes.large)
                    .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(BeaconGold))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, color = FogWhite)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FogDim)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
