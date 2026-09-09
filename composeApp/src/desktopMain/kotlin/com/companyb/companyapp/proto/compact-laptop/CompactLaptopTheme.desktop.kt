package com.companyb.companyapp.proto.compactlaptop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #778 — compact-laptop dense workbench theme: slate ledger, graphite bar, micro type.
// Everything targets 1280x800 with zero scroll marathons: 11sp body, 10sp micro labels,
// 6-8dp paddings, hairline borders. Distinct from Linear dark canvas on purpose.

object ClColors {
    val Canvas = Color(0xFFE9EDF2)
    val Card = Color(0xFFFFFFFF)
    val Bar = Color(0xFF232B36)
    val BarInk = Color(0xFFF2F5F9)
    val BarMuted = Color(0xFF9AA6B5)
    val Ink = Color(0xFF17202A)
    val Muted = Color(0xFF66727F)
    val Line = Color(0xFFD4DBE3)
    val Accent = Color(0xFF2456E0)
    val AccentSoft = Color(0xFFE1E9FD)
    val Open = Color(0xFF1E7A3C)
    val OpenBand = Color(0xFFCBEBD4)
    val Past = Color(0xFFB26A00)
    val PastBand = Color(0xFFF7E3BC)
    val Remitted = Color(0xFF0E7C7B)
    val RemittedBand = Color(0xFFC7E9E8)
    val Danger = Color(0xFFB3261E)
    val DangerSoft = Color(0xFFF9DEDC)
}

fun ClDayStatus.band(): Color = when (this) {
    ClDayStatus.OPEN -> ClColors.OpenBand
    ClDayStatus.PAST -> ClColors.PastBand
    ClDayStatus.REMITTED -> ClColors.RemittedBand
}

fun ClDayStatus.ink(): Color = when (this) {
    ClDayStatus.OPEN -> ClColors.Open
    ClDayStatus.PAST -> ClColors.Past
    ClDayStatus.REMITTED -> ClColors.Remitted
}

fun ClSessionStatus.ink(): Color = when (this) {
    ClSessionStatus.PENDING -> ClColors.Accent
    ClSessionStatus.COMPLETED -> ClColors.Open
    ClSessionStatus.NO_SHOW -> ClColors.Past
    ClSessionStatus.CANCELLED -> ClColors.Muted
}

@Composable
fun ClCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(ClColors.Card, RoundedCornerShape(6.dp))
            .border(1.dp, ClColors.Line, RoundedCornerShape(6.dp))
            .padding(8.dp),
        content = content,
    )
}

@Composable
fun ClTitle(text: String) {
    Text(text, color = ClColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ClSection(text: String) {
    Text(text, color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ClMicro(text: String) {
    Text(text.uppercase(), color = ClColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ClBody(text: String) {
    Text(text, color = ClColors.Ink, fontSize = 11.sp)
}

@Composable
fun ClMuted(text: String) {
    Text(text, color = ClColors.Muted, fontSize = 11.sp)
}

@Composable
fun ClGap(h: Int = 6) {
    Spacer(Modifier.height(h.dp))
}

@Composable
fun ClChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) ClColors.Accent else ClColors.Card
    val fg = if (selected) Color.White else ClColors.Ink
    Text(
        label,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) ClColors.Accent else ClColors.Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ClBtn(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (danger) ClColors.Danger else ClColors.Accent
    Text(
        label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun ClGhostBtn(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = ClColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.border(1.dp, ClColors.Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun RowScope.ClCell(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        ClMicro(label)
        Text(value, color = ClColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ClNote(text: String) {
    Text(
        text,
        color = ClColors.Muted,
        fontSize = 10.sp,
        modifier = Modifier.background(ClColors.Canvas, RoundedCornerShape(4.dp))
            .fillMaxWidth()
            .padding(6.dp),
    )
}

@Composable
fun ClChipRow(items: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item -> ClChip(label = item, selected = item == selected, onClick = { onPick(item) }) }
    }
}

@Composable
fun ClKeyRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ClColors.Muted, fontSize = 11.sp, modifier = Modifier.width(118.dp))
        Text(value, color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
