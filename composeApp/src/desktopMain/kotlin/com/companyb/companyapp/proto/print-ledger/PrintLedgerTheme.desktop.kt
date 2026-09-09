package com.companyb.companyapp.proto.printledger

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #776 — print-ledger receipt/report theme: paper sheet, ink type, monospace tabular
// numerals, stamp inks for branch-day state, dashed receipt rules.

object PlColors {
    val Sheet = Color(0xFFF7F4EC)
    val Slip = Color(0xFFFFFFFF)
    val Ink = Color(0xFF191713)
    val Faint = Color(0xFF857C6B)
    val Rule = Color(0xFFD8CFBB)
    val StampOpen = Color(0xFF1E7A3C)
    val StampPast = Color(0xFFB26A00)
    val StampRemitted = Color(0xFF0E5FA8)
    val StampVoid = Color(0xFFB3261E)
    val Key = Color(0xFF191713)
}

val PlMono = FontFamily.Monospace

fun PlDayStatus.stamp(): Color = when (this) {
    PlDayStatus.OPEN -> PlColors.StampOpen
    PlDayStatus.PAST -> PlColors.StampPast
    PlDayStatus.REMITTED -> PlColors.StampRemitted
}

@Composable
fun PlSlip(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(PlColors.Slip, RoundedCornerShape(4.dp))
            .border(1.dp, PlColors.Rule, RoundedCornerShape(4.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun PlRule() {
    Spacer(Modifier.height(10.dp))
    Text(
        "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
        color = PlColors.Rule,
        fontSize = 10.sp,
        fontFamily = PlMono,
        maxLines = 1,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
fun PlTitle(text: String) {
    Text(text, color = PlColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
}

@Composable
fun PlSubtitle(text: String) {
    Text(text, color = PlColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun PlSection(text: String) {
    Text(
        text,
        color = PlColors.Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        fontFamily = PlMono,
    )
}

@Composable
fun PlNote(text: String) {
    Text(text, color = PlColors.Faint, fontSize = 13.sp)
}

@Composable
fun PlFigure(text: String, color: Color = PlColors.Ink) {
    Text(
        text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PlMono,
    )
}

@Composable
fun PlError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, color = PlColors.StampVoid, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlStamp(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = PlMono,
        modifier = Modifier.border(2.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun PlPrimary(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(PlColors.Key, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun PlGhost(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = PlColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.border(1.dp, PlColors.Rule, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun PlChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) PlColors.Ink else Color.Transparent
    val fg = if (selected) Color.White else PlColors.Ink
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PlMono,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, PlColors.Rule, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun PlLine(label: String, value: String, valueColor: Color = PlColors.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PlColors.Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlMono,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun RowScope.PlStat(label: String, value: String, color: Color = PlColors.Ink) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
            fontFamily = PlMono)
        Text(label, color = PlColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = PlMono)
    }
}

@Composable
fun PlButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun PlGap() {
    Spacer(Modifier.height(12.dp))
    Spacer(Modifier.width(0.dp))
}
