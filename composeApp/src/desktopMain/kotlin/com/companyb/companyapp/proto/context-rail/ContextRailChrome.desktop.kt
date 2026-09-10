package com.companyb.companyapp.proto.contextrail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BranchDayBanner(
    day: RailBranchDay?,
    branch: RailBranch?,
    onPickDay: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RailCard).border(1.dp, RailLine)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Branch Day", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RailMuted)
        Spacer(Modifier.width(12.dp))
        for (date in listOf("2026-09-08", "2026-09-09", "2026-09-10")) {
            val selected = day?.date == date
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (selected) RailForest else RailPaper)
                    .border(1.dp, if (selected) RailForest else RailLine, RoundedCornerShape(999.dp))
                    .clickable { onPickDay(date) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    date,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) RailForestText else RailInk,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.weight(1f))
        if (day != null) {
            RailChip(day.state.label, dayTone(day.state))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            branch?.name ?: "No branch",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(RailAccentSoft)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            "Operational-day boundary 04:00 Asia/Manila — OPEN stays editable until 04:00 next morning, then turns PAST. REMITTED needs a Coordinator edit with flagged audit.",
            fontSize = 12.sp,
            color = RailAccent,
        )
    }
}

@Composable
fun NavRail(
    current: RailScreen,
    unread: Int,
    pending: Int,
    onPick: (RailScreen) -> Unit,
) {
    Column(
        modifier = Modifier.width(RailNavWidth).fillMaxHeight().background(RailForest)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("CONTEXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RailForestDim)
        Text("rail", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RailForestText)
        Text("list stays · rail follows", fontSize = 11.sp, color = RailForestDim)
        Spacer(Modifier.height(8.dp))
        for (screen in RailScreen.entries) {
            val active = screen == current
            val badge = when (screen) {
                RailScreen.SESSIONS -> if (pending > 0) "$pending" else null
                RailScreen.MAIL -> if (unread > 0) "$unread" else null
                else -> null
            }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) RailForestText else RailForest)
                    .clickable { onPick(screen) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        screen.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (active) RailForest else RailForestText,
                        modifier = Modifier.weight(1f),
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier.background(
                                if (active) RailAccent else RailAmber,
                                RoundedCornerShape(999.dp),
                            ).padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RailForest)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Fake data only — no network.", fontSize = 11.sp, color = RailForestDim)
    }
}

@Composable
fun InspectorFrame(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.width(RailInspectorWidth).fillMaxHeight().background(RailInspector)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            RailDarkChip("inspector · follows selection")
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            Text(subtitle, fontSize = 13.sp, color = RailInspectorMuted)
        }
        content()
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(RailRadius))
            .background(if (selected) RailCard else RailCard)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) RailAccent else RailLine,
                RoundedCornerShape(RailRadius),
            )
            .clickable(onClick = onClick)
            .padding(RailPadCard),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
fun CenterColumn(title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RailInk)
                Text(hint, fontSize = 13.sp, color = RailMuted)
            }
            RailChip("list stays put", RailTone.GREY)
        }
        content()
    }
}

@Composable
fun StatusBar(
    user: RailUser?,
    branch: RailBranch?,
    day: RailBranchDay?,
    pending: Int,
    unread: Int,
    clockedIn: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RailForest).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${user?.name ?: "—"} · ${user?.role?.label ?: "—"}",
            fontSize = 12.sp,
            color = RailForestText,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${branch?.name ?: "—"} · ${day?.date ?: "—"} ${day?.state?.label ?: ""} · PENDING $pending · unread $unread · ${if (clockedIn) "clocked in" else "clocked out"}",
            fontSize = 12.sp,
            color = RailForestDim,
        )
    }
}
