package com.companyb.companyapp.proto.listeverything

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #854 — list-everything shell: one window, one outline. Header holds identity,
// search, expand-all/collapse-all; the branch-day strip sits right below.

@Composable
fun ProtoListEverythingApp(onBack: () -> Unit) {
    val repo = remember { ListEverythingFakeRepo() }
    val tree = remember { LeTreeState() }

    LaunchedEffect(Unit) { logInfo("ListEverythingProto", "list-everything prototype launched") }

    Column(Modifier.fillMaxSize().background(LeColors.Bg)) {
        Column(
            Modifier.fillMaxWidth().background(LeColors.Paper)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▤ List Everything",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = LeColors.Ink,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "UNIVERSAL OUTLINER · FAKE DATA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = LeColors.Accent,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { tree.expandAll() }) { Text("Expand all", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { tree.collapseAll() }) { Text("Collapse all", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onBack) { Text("Exit", fontSize = 12.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${repo.actorName()} · ${repo.currentBranch.name} · ${repo.dayState.label}",
                    fontSize = 12.sp,
                    color = LeColors.Muted,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                TextField(
                    value = tree.query,
                    onValueChange = { tree.query = it },
                    singleLine = true,
                    placeholder = { Text("filter outline…", fontSize = 12.sp) },
                )
            }
        }
        LeDayStrip(repo = repo)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            LeOpsSections(repo = repo, tree = tree)
            LeMoneySections(repo = repo, tree = tree)
            LeNote(0, "end of outline · everything above collapses · search filters leaves")
        }
    }
}

@Composable
private fun LeDayStrip(repo: ListEverythingFakeRepo) {
    val tone = when (repo.dayState) {
        LeDayState.OPEN -> LeTone.GREEN
        LeDayState.PAST -> LeTone.AMBER
        LeDayState.REMITTED -> LeTone.SLATE
    }
    Row(
        Modifier.fillMaxWidth().background(tone.wash)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeBadge(repo.dayState.label, tone = tone)
        Spacer(Modifier.width(10.dp))
        Text(
            "${repo.currentBranch.name} · ${repo.currentBranch.dayDate} · day boundary 04:00 Asia/Manila",
            fontSize = 12.sp,
            color = tone.fg,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { repo.cycleDay() }) { Text("Cycle day", fontSize = 12.sp) }
    }
}
