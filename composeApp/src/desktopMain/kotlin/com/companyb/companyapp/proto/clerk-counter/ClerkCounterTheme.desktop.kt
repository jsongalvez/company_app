package com.companyb.companyapp.proto.clerkcounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #826 — clerk-counter theme: till receipt + scan lane. Paper counter, dark till
// header, teal scan beam, stamped variance callouts. Deliberately distinct from
// Linear and every sibling prototype: scan first, sessions second.

object CcColors {
    val Paper = Color(0xFFF6F4EC)
    val Lane = Color(0xFFFFFFFF)
    val LaneEdge = Color(0xFFD9D4C2)
    val Dashed = Color(0xFFC4BC9F)
    val Ink = Color(0xFF1E1C16)
    val Stencil = Color(0xFF4C4636)
    val Muted = Color(0xFF8A8270)
    val Till = Color(0xFF22303C)
    val TillSoft = Color(0xFF2E4152)
    val TillInk = Color(0xFFF6F4EC)
    val Scan = Color(0xFF0E7C6B)
    val ScanWash = Color(0xFFD3ECE5)
    val Short = Color(0xFFB3261E)
    val ShortWash = Color(0xFFF9DAD6)
    val Over = Color(0xFF8A5A00)
    val OverWash = Color(0xFFF5E3B8)
    val Match = Color(0xFF2E7D32)
    val MatchWash = Color(0xFFD9EAD3)
    val Low = Color(0xFFA33D00)
    val LowWash = Color(0xFFF7DCC3)
    val Steel = Color(0xFF3D5A73)
    val SteelWash = Color(0xFFD5E3EE)
    val Plum = Color(0xFF7A3B5D)
    val Slate = Color(0xFF6B6F76)
    val BannerOpen = Color(0xFFD3ECE5)
    val BannerPast = Color(0xFFF5E3B8)
    val BannerRemitted = Color(0xFFD5E3EE)
}

fun ccScheme() = lightColorScheme(
    primary = CcColors.Scan,
    onPrimary = Color.White,
    background = CcColors.Paper,
    onBackground = CcColors.Ink,
    surface = CcColors.Lane,
    onSurface = CcColors.Ink,
    surfaceVariant = CcColors.LaneEdge,
    secondary = CcColors.Till,
)

fun CcSessionStatus.dot(): Color = when (this) {
    CcSessionStatus.PENDING -> CcColors.Low
    CcSessionStatus.COMPLETED -> CcColors.Match
    CcSessionStatus.NO_SHOW -> CcColors.Plum
    CcSessionStatus.CANCELLED -> CcColors.Slate
}

fun CcDayStatus.wash(): Color = when (this) {
    CcDayStatus.OPEN -> CcColors.BannerOpen
    CcDayStatus.PAST -> CcColors.BannerPast
    CcDayStatus.REMITTED -> CcColors.BannerRemitted
}

@Composable
fun CcCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(CcColors.Lane, RoundedCornerShape(8.dp))
            .border(1.dp, CcColors.LaneEdge, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
fun CcKicker(text: String) {
    Text(text.uppercase(), color = CcColors.Stencil, fontSize = 12.sp, fontWeight = FontWeight.Black)
}

@Composable
fun CcHeadline(text: String) {
    Text(text, color = CcColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
}

@Composable
fun CcNote(text: String) {
    Text(text, color = CcColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

// Scan-feel row tag: beam bar + monospace code, used on every stock row.
@Composable
fun CcScanCode(code: String) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(CcColors.Till)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(3.dp).height(14.dp).background(CcColors.Scan)) { }
        Text("SCAN $code", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
    }
}

// Variance callout: stamped ticket for MATCH / SHORT n / OVER n.
@Composable
fun CcStamp(text: String, color: Color, wash: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(wash)
            .border(2.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("★ $text", color = color, fontSize = 11.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun CcFlag(text: String, color: Color, wash: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(wash)
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CcPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CcColors.Scan),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CcTill(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CcColors.Till),
    ) {
        Text(label, color = CcColors.TillInk, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CcDanger(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CcColors.Short),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CcGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = CcColors.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CcLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = CcColors.Steel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// Tape row: label left, monospace value right — the unit x quantity rail.
@Composable
fun CcTape(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CcColors.Stencil, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = CcColors.Ink,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (strong) FontWeight.Black else FontWeight.Normal,
        )
    }
}

@Composable
fun CcPerforation() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(CcColors.Dashed),
    ) { }
}

// Lane tab for the counter header strip.
@Composable
fun RowScope.CcLaneTab(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) CcColors.Scan else CcColors.Lane
    val fg = if (selected) Color.White else CcColors.Ink
    Column(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (selected) CcColors.Scan else CcColors.LaneEdge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace)
        Text(sub, color = if (selected) CcColors.ScanWash else CcColors.Muted, fontSize = 11.sp)
    }
}

@Composable
fun CcSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CcKicker(title)
        content()
    }
}

@Composable
fun CcThemeWrap(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ccScheme()) {
        Box(Modifier.fillMaxSize().background(CcColors.Paper)) { content() }
    }
}

@Composable
fun CcScreenTitle() {
    Text(
        "CLERK COUNTER ▸ SCAN LANE",
        color = CcColors.Scan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
    )
}
