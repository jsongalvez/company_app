package com.companyb.companyapp.proto.searchonly

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #855 — search-only shell: no nav chrome at all. One glowing box runs the
// branch; recent objects sit under it; hits open in the stage below.

@Composable
fun ProtoSearchOnlyApp(onBack: () -> Unit) {
    val repo = remember { SearchOnlyFakeRepo() }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<SoSel?>(null) }

    LaunchedEffect(Unit) { logInfo("SearchOnlyProto", "search-only prototype launched") }

    fun open(next: SoSel) {
        if (next.runNeedsTouch()) repo.touch(next)
        sel = next
    }

    val hits = remember(query, repo.version, repo.actorId) {
        soSearch(repo, query)
    }
    val top = hits.firstOrNull()

    Column(Modifier.fillMaxSize().background(SoColors.Abyss)) {
        // Slim identity + day strip — the only chrome in the branch.
        Row(
            Modifier.fillMaxWidth().background(SoColors.Panel)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⌁ SEARCH-ONLY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = SoColors.Lime,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${repo.actorName()} · ${repo.currentBranch.name}",
                fontSize = 12.sp,
                color = SoColors.Dim,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            SoPill(repo.dayState.label, when (repo.dayState) {
                SoDayState.OPEN -> SoTone.GREEN
                SoDayState.PAST -> SoTone.AMBER
                SoDayState.REMITTED -> SoTone.VIOLET
            })
            Spacer(Modifier.width(8.dp))
            Text(
                "04:00 Asia/Manila",
                fontSize = 11.sp,
                color = SoColors.Faint,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            SoChip("exit") { onBack() }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            if (repo.actor == null) {
                Text(
                    "Who is on shift?",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = SoColors.Ink,
                )
                Text(
                    "Type a name — or pick a login command. No buttons lead here; the box is the branch.",
                    fontSize = 13.sp,
                    color = SoColors.Dim,
                )
            } else {
                Text(
                    "Run the branch.",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = SoColors.Ink,
                )
                Text(
                    "Sessions, clients, money, people — everything answers to the box. ⏎ runs the top hit.",
                    fontSize = 13.sp,
                    color = SoColors.Dim,
                )
            }
            Spacer(Modifier.height(12.dp))
            SoBarField(
                query = query,
                onQuery = { query = it },
                onEnter = { top?.let { open(it.sel) } },
                hint = if (repo.actor == null) "login…" else "Type to run the branch…",
            )
            Spacer(Modifier.height(10.dp))
            // Verb shortcuts: clicking fills the box so the human sees the language.
            Row {
                soVerbs(repo).take(6).forEach {
                    SoChip(it) { query = it }
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (repo.recents.isNotEmpty()) {
                SoSection("RECENT", "tap to reopen")
                Row {
                    repo.recents.take(6).forEach { r ->
                        SoChip("◷ ${r.label(repo)}") { open(r) }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            val current = sel
            if (current != null) {
                SoSection("STAGE", current.label(repo))
                SoStage(repo, current, query, ::open)
            }
            SoSection("RESULTS", "${hits.size} hits${if (query.isBlank()) " · browse everything" else " · for \"$query\""}")
            if (hits.isEmpty()) {
                SoCard { SoBody("Nothing answers to that. Try login, clock in, s-101, remit, void, team, notifications, audit, branch.") }
            } else {
                var lastGroup = ""
                hits.forEachIndexed { i, h ->
                    if (h.group != lastGroup) {
                        SoSection(h.group)
                        lastGroup = h.group
                    }
                    // Instant commands run their stage; object jumps open detail.
                    SoHitRow(h.glyph, h.title, h.sub, h.tone, hot = i == 0, onClick = { open(h.sel) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "FAKE DATA · NO NETWORK · ⌁ search-only ref #855",
                    fontSize = 11.sp,
                    color = SoColors.Faint,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun SoSel.runNeedsTouch(): Boolean = kind in setOf(
    "session", "client", "finance", "team", "notices", "audit", "profile", "branches", "home", "onboarding",
)
