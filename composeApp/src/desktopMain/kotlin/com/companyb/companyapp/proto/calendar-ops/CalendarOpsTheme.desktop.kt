package com.companyb.companyapp.proto.calendarops

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

// #775 — calendar-ops paper-planner theme: cream sheet, ink type, day-state color bands.

object CoColors {
    val Paper = Color(0xFFFBF6EA)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E1B14)
    val Muted = Color(0xFF7A7261)
    val Line = Color(0xFFE3D9C2)
    val Open = Color(0xFF1E7A3C)
    val OpenBand = Color(0xFFDDF0E2)
    val Past = Color(0xFFB26A00)
    val PastBand = Color(0xFFF7E8C8)
    val Remitted = Color(0xFF0E7C7B)
    val RemittedBand = Color(0xFFD3EDED)
    val Today = Color(0xFFC23B2E)
    val Accent = Color(0xFF2F5FE3)
}

fun CoDayStatus.band(): Color = when (this) {
    CoDayStatus.OPEN -> CoColors.OpenBand
    CoDayStatus.PAST -> CoColors.PastBand
    CoDayStatus.REMITTED -> CoColors.RemittedBand
}

fun CoDayStatus.ink(): Color = when (this) {
    CoDayStatus.OPEN -> CoColors.Open
    CoDayStatus.PAST -> CoColors.Past
    CoDayStatus.REMITTED -> CoColors.Remitted
}

@Composable
fun CoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(CoColors.Card, RoundedCornerShape(10.dp))
            .border(1.dp, CoColors.Line, RoundedCornerShape(10.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun CoTitle(text: String) {
    Text(text, color = CoColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
}

@Composable
fun CoSubtitle(text: String) {
    Text(text, color = CoColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun CoSection(text: String) {
    Text(text, color = CoColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
}

@Composable
fun CoNote(text: String) {
    Text(text, color = CoColors.Muted, fontSize = 13.sp)
}

@Composable
fun CoError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, color = CoColors.Today, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CoPrimary(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(CoColors.Ink, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun CoGhost(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = CoColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.border(1.dp, CoColors.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun CoChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) CoColors.Ink else Color.Transparent
    val fg = if (selected) Color.White else CoColors.Ink
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, CoColors.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun RowScope.CoStat(label: String, value: String, color: Color = CoColors.Ink) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, color = CoColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CoButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun CoGap() {
    Spacer(Modifier.height(12.dp))
    Spacer(Modifier.width(0.dp))
}
