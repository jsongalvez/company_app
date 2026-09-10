package com.companyb.companyapp.proto.ledgerbook

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #835 — ledger-book bound-book theme: cream folio, leather spine, sepia fountain-pen
// serif, blue ruled lines, red margin, double-ring rubber stamps.

object LbColors {
    val Page = Color(0xFFFBF3DF)
    val Card = Color(0xFFFDFAF0)
    val Spine = Color(0xFF4A3226)
    val SpineLight = Color(0xFF6B4E3B)
    val Ink = Color(0xFF2E2015)
    val Faint = Color(0xFF8A7A64)
    val Ruled = Color(0xFFA9BFD4)
    val Margin = Color(0xFFC26A5B)
    val StampOpen = Color(0xFF2E7D32)
    val StampPast = Color(0xFF9A6A00)
    val StampRemitted = Color(0xFF1A3E7A)
    val StampVoid = Color(0xFFA31F16)
    val Seal = Color(0xFF2E2015)
}

val LbSerif = FontFamily.Serif

fun LbDayStatus.stamp(): Color = when (this) {
    LbDayStatus.OPEN -> LbColors.StampOpen
    LbDayStatus.PAST -> LbColors.StampPast
    LbDayStatus.REMITTED -> LbColors.StampRemitted
}

@Composable
fun LbPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(LbColors.Card, RoundedCornerShape(2.dp))
            .border(1.dp, LbColors.Ruled, RoundedCornerShape(2.dp))
            .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        content = content,
    )
}

@Composable
fun LbRuled() {
    Spacer(Modifier.height(8.dp))
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).background(LbColors.Ruled),
    )
    Spacer(Modifier.height(2.dp))
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).background(LbColors.Ruled.copy(alpha = 0.45f)),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun LbChapter(text: String) {
    Text(text, color = LbColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        fontFamily = LbSerif)
}

@Composable
fun LbEpigraph(text: String) {
    Text(text, color = LbColors.Faint, fontSize = 14.sp, fontStyle = FontStyle.Italic,
        fontFamily = LbSerif)
}

@Composable
fun LbHead(text: String) {
    Text(text, color = LbColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        fontFamily = LbSerif)
}

@Composable
fun LbMarginalia(text: String) {
    Text(
        "❧  $text",
        color = LbColors.Faint,
        fontSize = 13.sp,
        fontStyle = FontStyle.Italic,
        fontFamily = LbSerif,
    )
}

@Composable
fun LbSum(text: String, color: Color = LbColors.Ink) {
    Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        fontFamily = LbSerif)
}

@Composable
fun LbErratum(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text("✕  $text", color = LbColors.StampVoid, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = LbSerif)
    }
}

@Composable
fun LbStamp(text: String, color: Color) {
    Text(
        "❰ $text ❱",
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = LbSerif,
        modifier = Modifier.border(2.dp, color, RoundedCornerShape(2.dp))
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun LbQuill(text: String, onClick: () -> Unit) {
    Text(
        "✒  $text",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LbSerif,
        modifier = Modifier.background(LbColors.Seal, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun LbPencil(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = LbColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LbSerif,
        modifier = Modifier.border(1.dp, LbColors.Ink, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun LbTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) LbColors.Spine else Color.Transparent
    val fg = if (selected) Color.White else LbColors.Ink
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LbSerif,
        modifier = Modifier.background(bg, RoundedCornerShape(2.dp))
            .border(1.dp, LbColors.Ruled, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun LbEntry(label: String, value: String, valueColor: Color = LbColors.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("·  $label", color = LbColors.Faint, fontSize = 13.sp, fontFamily = LbSerif,
            modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = LbSerif)
    }
    Spacer(Modifier.height(6.dp))
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(LbColors.Ruled.copy(alpha = 0.5f)))
    Spacer(Modifier.height(6.dp))
}

@Composable
fun RowScope.LbTally(label: String, value: String, color: Color = LbColors.Ink) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
            fontFamily = LbSerif)
        Text(label, color = LbColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = LbSerif)
    }
}

@Composable
fun LbButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun LbGap() {
    Spacer(Modifier.height(12.dp))
    Spacer(Modifier.width(0.dp))
}
