package com.companyb.companyapp.proto.mindmapnav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo
import kotlin.math.cos
import kotlin.math.sin

private enum class MapPhase { LOGIN, ONBOARDING, BRANCHES, MAP }

enum class MapNode(val label: String, val glyph: String, val color: Color, val blurb: String) {
    HOME("Home", "❀", Leaf, "Clock in, relief orbits, day figures"),
    SESSIONS("Sessions", "✦", Brook, "Pending to done, void with reason"),
    CLIENTS("Clients", "◍", Bloom, "Global galaxy, veiled codes"),
    FINANCE("Finance", "⬢", Honey, "Drafts, snapshots, undo 48h"),
    TEAM("Team", "◈", Moss, "Roles and locked nodes"),
    INBOX("Inbox", "✉", Coral, "Mailbox moons"),
    AUDIT("Ledger", "≋", Mist, "Every act on record"),
    PROFILE("You", "●", Chalk, "Shift, day, logout"),
}

fun badgeFor(node: MapNode, repo: MapFakeRepo): String =
    when (node) {
        MapNode.HOME -> if (repo.clockedIn) "IN" else ""
        MapNode.SESSIONS -> if (repo.pendingCount() > 0) "${repo.pendingCount()} pending" else ""
        MapNode.CLIENTS -> "${repo.clients.size}"
        MapNode.FINANCE -> if (repo.drafts.any { !it.submitted }) "${repo.drafts.count { !it.submitted }} open" else ""
        MapNode.TEAM -> "${repo.mates.size}"
        MapNode.INBOX -> if (repo.unreadCount() > 0) "${repo.unreadCount()} new" else ""
        MapNode.AUDIT -> "${repo.audits.size}"
        MapNode.PROFILE -> ""
    }

@Composable
fun MindMapApp() {
    logInfo("MindMap", "mindmap-nav prototype opened")
    MindMapTheme {
        var phase by remember { mutableStateOf(MapPhase.LOGIN) }
        var repo by remember { mutableStateOf(MapFakeRepo()) }
        Box(modifier = Modifier.fillMaxSize().background(Abyss)) {
            when (phase) {
                MapPhase.LOGIN ->
                    MapLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = MapPhase.BRANCHES
                        },
                        onOnboarding = { phase = MapPhase.ONBOARDING },
                    )
                MapPhase.ONBOARDING -> MapOnboardingLocked(onBack = { phase = MapPhase.LOGIN })
                MapPhase.BRANCHES ->
                    MapBranchSelect(
                        repo = repo,
                        onPick = { phase = MapPhase.MAP },
                        onBack = { phase = MapPhase.LOGIN },
                    )
                MapPhase.MAP ->
                    MapShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = MapPhase.LOGIN
                        },
                        onReset = {
                            val keepEmail = repo.email
                            val keepBranch = repo.branchId
                            repo = MapFakeRepo().also { it.email = keepEmail; it.branchId = keepBranch }
                        },
                        onSwitchBranch = { phase = MapPhase.BRANCHES },
                    )
            }
        }
    }
}

@Composable
private fun MapShell(
    repo: MapFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    var focus by remember { mutableStateOf(MapNode.HOME) }
    val branch = repo.currentBranch()
    val ring = branch.day.ring()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(ring))
            Text(branch.name.uppercase(), fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, color = NodeInk, modifier = Modifier.clickable { onSwitchBranch() })
            Text("${branch.kind} · ${branch.place}", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim, modifier = Modifier.weight(1f))
            Text("DAY ${branch.day.name} · rolls 04:00 Asia/Manila", fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ring, modifier = Modifier.clickable { repo.advanceDay() })
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier.width(480.dp).fillMaxHeight().background(Abyss),
                contentAlignment = Alignment.Center,
            ) {
                MindMap(repo = repo, focus = focus, onFocus = { focus = it })
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF161327))) {
                val crumb = "❀ ${branch.name} › ${focus.label}"
                when (focus) {
                    MapNode.HOME -> MapHomeDetail(repo, crumb, { focus = MapNode.HOME }, { focus = it })
                    MapNode.SESSIONS -> MapSessionsDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.CLIENTS -> MapClientsDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.FINANCE -> MapFinanceDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.TEAM -> MapTeamDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.INBOX -> MapInboxDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.AUDIT -> MapAuditDetail(repo, crumb, { focus = MapNode.HOME })
                    MapNode.PROFILE ->
                        MapProfileDetail(
                            repo,
                            crumb,
                            { focus = MapNode.HOME },
                            onLogout = onLogout,
                            onReset = onReset,
                            onSwitchBranch = onSwitchBranch,
                        )
                }
            }
        }
    }
}

private const val MAP_W = 480.0
private const val MAP_H = 600.0
private const val CENTER_X = 240.0
private const val CENTER_Y = 292.0
private const val RADIUS_X = 152.0
private const val RADIUS_Y = 218.0
private const val NODE_W = 148.0
private const val NODE_H = 62.0

@Composable
private fun MindMap(
    repo: MapFakeRepo,
    focus: MapNode,
    onFocus: (MapNode) -> Unit,
) {
    val branch = repo.currentBranch()
    val ring = branch.day.ring()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(MAP_W.dp).height(MAP_H.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = (CENTER_X / MAP_W * size.width).toFloat()
                val cy = (CENTER_Y / MAP_H * size.height).toFloat()
                val rx = (RADIUS_X / MAP_W * size.width).toFloat()
                val ry = (RADIUS_Y / MAP_H * size.height).toFloat()
                MapNode.entries.forEachIndexed { i, node ->
                    val a = Math.toRadians(-90.0 + i * 45.0)
                    val tx = cx + (cos(a) * rx).toFloat()
                    val ty = cy + (sin(a) * ry).toFloat()
                    drawLine(Vine.copy(alpha = if (node == focus) 0.95f else 0.45f), start = androidx.compose.ui.geometry.Offset(cx, cy), end = androidx.compose.ui.geometry.Offset(tx, ty), strokeWidth = if (node == focus) 3f else 1.5f)
                    drawCircle(node.color.copy(alpha = if (node == focus) 1f else 0.55f), radius = if (node == focus) 6f else 4f, center = androidx.compose.ui.geometry.Offset(tx, ty))
                }
                drawCircle(ring.copy(alpha = 0.25f), radius = 86f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 2f))
            }
            Column(
                modifier = Modifier.offset(x = (CENTER_X - 80).dp, y = (CENTER_Y - 96).dp).width(160.dp).clip(RoundedCornerShape(28.dp)).background(Panel).border(3.dp, ring, RoundedCornerShape(28.dp)).clickable { onFocus(MapNode.HOME) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                    Text(branch.name.take(1), fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Abyss)
                }
                Text(branch.name, fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 13.sp, color = NodeInk)
                Text(branch.day.name, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp, color = ring)
            }
            MapNode.entries.forEachIndexed { i, node ->
                val a = Math.toRadians(-90.0 + i * 45.0)
                val nx = CENTER_X + cos(a) * RADIUS_X - NODE_W / 2
                val ny = CENTER_Y + sin(a) * RADIUS_Y - NODE_H / 2
                val on = node == focus
                val badge = badgeFor(node, repo)
                Column(
                    modifier =
                        Modifier.offset(x = nx.dp, y = ny.dp).width(NODE_W.dp)
                            .graphicsLayer(scaleX = if (on) 1.12f else 1f, scaleY = if (on) 1.12f else 1f)
                            .clip(RoundedCornerShape(999.dp)).background(if (on) node.color else Panel)
                            .border(if (on) 0.dp else 2.dp, if (on) node.color else node.color.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                            .clickable { onFocus(node) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(node.glyph, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Abyss else node.color)
                        Text(node.label, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Abyss else NodeInk)
                    }
                    if (badge.isNotBlank()) {
                        Text(badge, fontFamily = MapMono, fontSize = 9.sp, color = if (on) Abyss else NodeDim)
                    }
                }
            }
        }
        Text("tap a node to zoom in · tap the center to go home", fontFamily = MapMono, fontSize = 11.sp, color = NodeDim, modifier = Modifier.padding(bottom = 10.dp))
    }
}
