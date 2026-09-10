package com.companyb.companyapp.proto.forestcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

// #807 — forest-calm forest-floor theme. Moss-bark neutrals on warm paper,
// organic rounded shapes, dotted-trail dividers. Light, quiet, distinct.

val FcPaper = Color(0xFFF3EFE2)
val FcPaperDeep = Color(0xFFE9E2CE)
val FcCardBg = Color(0xFFFAF7EC)
val FcMoss = Color(0xFF3E5C3A)
val FcMossDeep = Color(0xFF2A4227)
val FcFern = Color(0xFF6B8F5E)
val FcFernPale = Color(0xFFDCE5CB)
val FcBark = Color(0xFF4A3B2C)
val FcBarkSoft = Color(0xFF7A6A53)
val FcBarkPale = Color(0xFFE3D8C2)
val FcAmbercap = Color(0xFFB07A2A)
val FcBerry = Color(0xFF9C4A3C)
val FcInk = Color(0xFF2E2A22)
val FcInkSoft = Color(0xFF6E675A)

val FcSerif = FontFamily.Serif
val FcSans = FontFamily.SansSerif

@Composable
fun FcRoot(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = FcPaper,
            surface = FcCardBg,
            surfaceVariant = FcPaperDeep,
            primary = FcMoss,
            onPrimary = Color.White,
            secondary = FcFern,
            tertiary = FcAmbercap,
            error = FcBerry,
            onBackground = FcInk,
            onSurface = FcInk,
            outline = FcBarkPale,
        ),
        content = content,
    )
}

@Composable
fun FcSectionTitle(text: String, trail: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(FcMoss),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            fontFamily = FcSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            color = FcMossDeep,
        )
        if (trail.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(text = trail, fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft)
        }
    }
}

@Composable
fun FcCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FcCardBg)
            .border(1.dp, FcBarkPale, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun FcChip(text: String, bg: Color, fg: Color = Color.White) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontFamily = FcSans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun FcStatusChip(status: String) {
    val bg = when (status) {
        "COMPLETED", "OPEN", "SUBMITTED", "SEALED", "active", "read" -> FcMoss
        "PENDING", "DRAFT", "live", "pending" -> FcAmbercap
        "NO_SHOW", "PAST" -> FcBarkSoft
        "CANCELLED", "REMITTED", "withdrawn", "declined" -> FcBerry
        else -> FcFern
    }
    FcChip(status, bg)
}

@Composable
fun FcTrailDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(28) { i ->
            Box(
                modifier = Modifier
                    .size(if (i % 4 == 0) 5.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (i % 4 == 0) FcFern else FcBarkPale),
            )
        }
    }
}

@Composable
fun FcNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FcFernPale)
            .padding(10.dp),
    ) {
        Text(text = "~ " + text, fontFamily = FcSans, fontSize = 12.sp, color = FcMossDeep)
    }
}

@Composable
fun FcNavItem(label: String, glyph: String, selected: Boolean, unread: Int = 0, onClick: () -> Unit) {
    val bg = if (selected) FcMoss else Color.Transparent
    val fg = if (selected) Color.White else FcBark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = glyph, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = FcSans,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
            modifier = Modifier.weight(1f),
        )
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White else FcAmbercap)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(
                    text = unread.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) FcMoss else Color.White,
                )
            }
        }
    }
}

@Composable
fun FcHairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FcBarkPale))
}
