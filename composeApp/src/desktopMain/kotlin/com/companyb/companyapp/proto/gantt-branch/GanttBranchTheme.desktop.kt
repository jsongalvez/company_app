package com.companyb.companyapp.proto.ganttbranch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #815 — gantt-branch theme. Night-dispatch blueprint: ink-navy field,
// hairline grid, paper lanes, amber 04:00 boundary, signal blocks per status.

val GbInk = Color(0xFF10141B)
val GbPanel = Color(0xFF1A2230)
val GbPanelEdge = Color(0xFF2A3549)
val GbGrid = Color(0xFF232E42)
val GbPaper = Color(0xFFFBFAF6)
val GbPaperDim = Color(0xFFE9E4D8)
val GbAxisInk = Color(0xFF8B93A7)
val GbSignalAmber = Color(0xFFF5A623)
val GbSignalAmberPale = Color(0xFF3A2E14)
val GbPending = Color(0xFF4C8DFF)
val GbCompleted = Color(0xFF2FA36B)
val GbNoShow = Color(0xFF8A8F9E)
val GbCancelled = Color(0xFFD64545)
val GbVoid = Color(0xFF6B4A2E)
val GbText = Color(0xFFEDEFF5)
val GbTextDim = Color(0xFFA7AEC1)
val GbDarkRow = Color(0xFF161D2B)
val GbBlockInk = Color(0xFF0D1117)

val GbSans = FontFamily.SansSerif
val GbMono = FontFamily.Monospace

@Composable
fun GbRoot(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = GbInk,
            surface = GbPanel,
            surfaceVariant = GbPanelEdge,
            primary = GbSignalAmber,
            onPrimary = GbBlockInk,
            secondary = GbPending,
            onSecondary = Color.White,
            tertiary = GbCompleted,
            onBackground = GbText,
            onSurface = GbText,
        ),
        content = {
            Box(modifier = Modifier.background(GbInk)) { content() }
        },
    )
}

@Composable
fun GbSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = GbMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = GbAxisInk,
    )
}

@Composable
fun GbCard(
    modifier: Modifier = Modifier,
    edge: Color = GbPanelEdge,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var card = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(GbPanel)
    if (onClick != null) card = card.clickable(onClick = onClick)
    Column(modifier = modifier.then(card).padding(14.dp)) {
        content()
    }
}

@Composable
fun GbChip(
    text: String,
    bg: Color,
    fg: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = GbMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = fg,
        )
    }
}

fun GbStatusColor(status: GbSessionStatus): Color = when (status) {
    GbSessionStatus.PENDING -> GbPending
    GbSessionStatus.COMPLETED -> GbCompleted
    GbSessionStatus.NO_SHOW -> GbNoShow
    GbSessionStatus.CANCELLED -> GbCancelled
}

fun GbDayColor(day: GbDayStatus): Color = when (day) {
    GbDayStatus.OPEN -> GbCompleted
    GbDayStatus.PAST -> GbNoShow
    GbDayStatus.REMITTED -> GbSignalAmber
}

@Composable
fun GbButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (primary) GbSignalAmber else GbPanelEdge)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = GbSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (primary) GbBlockInk else GbText,
        )
    }
}

@Composable
fun GbNavRail(
    items: List<Pair<GbScreen, String>>,
    current: GbScreen,
    unread: Int,
    onPick: (GbScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(208.dp)
            .background(GbBlockInk)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "GANTT BRANCH",
            fontFamily = GbMono,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
            color = GbSignalAmber,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            text = "branch week on a time axis",
            fontFamily = GbSans,
            fontSize = 11.sp,
            color = GbTextDim,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        for ((screen, label) in items) {
            val selected = screen == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) GbSignalAmberPale else Color.Transparent)
                    .clickable { onPick(screen) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Box(Modifier.size(8.dp).background(GbSignalAmber, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    fontFamily = GbSans,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    color = if (selected) GbSignalAmber else GbTextDim,
                )
                if (screen == GbScreen.MAIL && unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GbSignalAmber)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = unread.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GbBlockInk,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(GbSignalAmberPale)
                .padding(10.dp),
        ) {
            Text(
                text = "04:00 Asia/Manila cuts the Branch Day",
                fontFamily = GbMono,
                fontSize = 11.sp,
                color = GbSignalAmber,
            )
        }
    }
}
