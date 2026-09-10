package com.companyb.companyapp.proto.weekreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #850 — Friday Review broadsheet tokens: cream paper, ink rules, oxblood flag,
// moss-green wins, amber misses, gold payouts. No Linear tokens on purpose.

object WrColors {
    val Bg = Color(0xFFFAF5EA)
    val Paper = Color(0xFFFFFDF6)
    val Ink = Color(0xFF262015)
    val Muted = Color(0xFF6E6353)
    val Line = Color(0xFFE0D5BE)
    val Accent = Color(0xFF8C2F1B)
    val Moss = Color(0xFF3E6B4E)
    val MossWash = Color(0xFFE7EFE6)
    val Amber = Color(0xFF9A5B00)
    val AmberWash = Color(0xFFF8ECD4)
    val Gold = Color(0xFF8A6A12)
    val GoldWash = Color(0xFFF6EBCB)
    val StampRed = Color(0xFFA93226)
    val Slate = Color(0xFF4A5560)
    val SlateWash = Color(0xFFE9EDF0)
}

fun peso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

@Composable
fun MastRule() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(WrColors.Ink))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(WrColors.Ink))
    }
}

@Composable
fun ThinRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WrColors.Line))
}

@Composable
fun SectionFlag(kicker: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(WrColors.Accent)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                kicker.uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(WrColors.Accent))
    }
}

@Composable
fun StatusStamp(
    label: String,
    color: Color,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            label.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun NoteCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(WrColors.Paper)
            .border(1.dp, WrColors.Line, RoundedCornerShape(3.dp))
            .padding(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(34.dp).background(WrColors.Accent))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = WrColors.Muted,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun WashPill(
    text: String,
    fg: Color,
    wash: Color,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(wash)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
