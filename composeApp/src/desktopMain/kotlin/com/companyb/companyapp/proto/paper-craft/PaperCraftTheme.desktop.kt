package com.companyb.companyapp.proto.papercraft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #842 — paper-craft desk theme: kraft desk, stacked cut-paper cards, washi-tape
// dividers, hand-cut corners, paste-down stamps.

object PcColors {
    val Desk = Color(0xFFE3CFA6)
    val DeskDark = Color(0xFFC7A878)
    val Paper = Color(0xFFFFFEF6)
    val Cream = Color(0xFFFFF3D4)
    val Ink = Color(0xFF33291C)
    val Faint = Color(0xFF8B7B5E)
    val CutEdge = Color(0xFF5B4A3A)
    val Shadow = Color(0xFFB89B62)
    val WashiPink = Color(0xFFE79FB3)
    val WashiTeal = Color(0xFF7FBFB0)
    val WashiYellow = Color(0xFFF0BE4A)
    val WashiBlue = Color(0xFF9CC3E5)
    val WashiGreen = Color(0xFFA8D5A2)
    val StampOpen = Color(0xFF2E7D32)
    val StampPast = Color(0xFF9A6A00)
    val StampRemitted = Color(0xFF1A3E7A)
    val StampVoid = Color(0xFFA31F16)
    val Paste = Color(0xFF4A6B4F)
}

val PcHand = FontFamily.SansSerif

fun PcDayStatus.stamp(): Color = when (this) {
    PcDayStatus.OPEN -> PcColors.StampOpen
    PcDayStatus.PAST -> PcColors.StampPast
    PcDayStatus.REMITTED -> PcColors.StampRemitted
}

fun washiFor(index: Int): Color = when (index % 5) {
    0 -> PcColors.WashiPink
    1 -> PcColors.WashiTeal
    2 -> PcColors.WashiYellow
    3 -> PcColors.WashiBlue
    else -> PcColors.WashiGreen
}

@Composable
fun PcSheet(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .offset(x = 5.dp, y = 5.dp)
                .background(PcColors.Shadow, RoundedCornerShape(3.dp))
                .height(8.dp),
        )
        Column(
            Modifier.fillMaxWidth()
                .background(PcColors.Paper, RoundedCornerShape(3.dp))
                .border(1.dp, PcColors.CutEdge.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            content = content,
        )
    }
}

@Composable
fun PcWashi(color: Color = PcColors.WashiYellow, label: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(22.dp)
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(14) {
                    Spacer(
                        Modifier.width(10.dp).height(22.dp)
                            .background(Color.White.copy(alpha = 0.28f)),
                    )
                }
            }
            if (label != null) {
                Text(
                    label,
                    color = PcColors.Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = PcHand,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.75f),
                        RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
fun PcTitle(text: String) {
    Text(text, color = PcColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black,
        fontFamily = PcHand)
}

@Composable
fun PcSubtitle(text: String) {
    Text(text, color = PcColors.Faint, fontSize = 14.sp, fontStyle = FontStyle.Italic,
        fontFamily = PcHand)
}

@Composable
fun PcHead(text: String) {
    Text(text, color = PcColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black,
        fontFamily = PcHand)
}

@Composable
fun PcNote(text: String) {
    Text(
        "✂  $text",
        color = PcColors.Faint,
        fontSize = 13.sp,
        fontStyle = FontStyle.Italic,
        fontFamily = PcHand,
    )
}

@Composable
fun PcSum(text: String, color: Color = PcColors.Ink) {
    Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black,
        fontFamily = PcHand)
}

@Composable
fun PcError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text("✕  $text", color = PcColors.StampVoid, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = PcHand)
    }
}

@Composable
fun PcSticker(text: String, color: Color) {
    Text(
        "★ $text ★",
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = PcHand,
        modifier = Modifier.background(color, RoundedCornerShape(12.dp))
            .border(1.dp, Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun PcCutPrimary(text: String, onClick: () -> Unit) {
    Text(
        "✂  $text",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PcHand,
        modifier = Modifier.background(PcColors.Paste, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun PcCutGhost(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = PcColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PcHand,
        modifier = Modifier.background(PcColors.Cream, RoundedCornerShape(3.dp))
            .border(1.dp, PcColors.CutEdge, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun PcTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) PcColors.Ink else PcColors.Cream
    val fg = if (selected) Color.White else PcColors.Ink
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PcHand,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, PcColors.CutEdge.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun PcLine(label: String, value: String, valueColor: Color = PcColors.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("▪  $label", color = PcColors.Faint, fontSize = 13.sp, fontFamily = PcHand,
            modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = PcHand)
    }
    Spacer(Modifier.height(6.dp))
    PcWashi(color = PcColors.WashiBlue)
}

@Composable
fun RowScope.PcTally(label: String, value: String, color: Color = PcColors.Ink) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
            fontFamily = PcHand)
        Text(label, color = PcColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = PcHand)
    }
}

@Composable
fun PcButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun PcGap() {
    Spacer(Modifier.height(12.dp))
}
