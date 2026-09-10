package com.companyb.companyapp.proto.mindmapnav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapLogin(
    onEnter: (String) -> Unit,
    onOnboarding: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(Abyss), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(460.dp).clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(24.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Leaf))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brook))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Bloom))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Honey))
            }
            Text("THE MINDMAP", fontFamily = MapMono, fontSize = 12.sp, letterSpacing = 4.sp, color = NodeDim)
            Text("One Branch at the center. Everything else orbits it.", fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 30.sp, color = NodeInk)
            Text("Tap a node to zoom in. Zoom out to return. Fake demo — any email works, nothing leaves this window.", fontFamily = MapSans, fontSize = 13.sp, lineHeight = 19.sp, color = NodeDim)
            MapField(address, { address = it }, "Work email")
            MapButton("Enter the map →", onClick = { onEnter(address.ifBlank { "demo@companyb.ph" }) })
            Text("Fresh account with zero capabilities? Preview the ONBOARDING lock →", fontFamily = MapSans, fontSize = 12.sp, color = Moss, modifier = Modifier.clickable { onOnboarding() })
        }
    }
}

@Composable
fun MapOnboardingLocked(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Abyss), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(480.dp).clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Honey, RoundedCornerShape(24.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("◉ LOCKED NODE", fontFamily = MapMono, fontSize = 12.sp, letterSpacing = 3.sp, color = Honey)
            Text("ONBOARDING has no orbit yet", fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 24.sp, color = NodeInk)
            Text("A freshly registered user carries an empty capability bundle: even with a Branch assignment, no section node derives, so the map renders a single grey dot. A MANAGER must grant a real role before anything blooms.", fontFamily = MapSans, fontSize = 13.sp, lineHeight = 20.sp, color = NodeDim)
            NoteCard("Domain rule: ONBOARDING is functionally locked out until MANAGE_USERS grants a real role.", Honey)
            MapGhost("⟨ back to login", onClick = onBack)
        }
    }
}

@Composable
fun MapBranchSelect(
    repo: MapFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Abyss), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(560.dp).clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(24.dp)).padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PICK YOUR CENTER", fontFamily = MapMono, fontSize = 12.sp, letterSpacing = 4.sp, color = NodeDim)
            Text("Which Branch anchors the map?", fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 24.sp, color = NodeInk)
            repo.branches.forEach { branch ->
                val ring = branch.day.ring()
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Abyss).border(2.dp, ring, RoundedCornerShape(16.dp)).clickable { repo.move(branch); onPick() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                        Text(branch.name.take(1), fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Abyss)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NodeInk)
                        Text("${branch.kind} · ${branch.place}", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim)
                    }
                    Text(branch.day.name, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ring)
                }
            }
            NoteCard("Branch Day rolls at 04:00 Asia/Manila. OPEN blooms, PAST ambers, REMITTED rests in violet.")
            MapGhost("⟨ back to login", onClick = onBack)
        }
    }
}
