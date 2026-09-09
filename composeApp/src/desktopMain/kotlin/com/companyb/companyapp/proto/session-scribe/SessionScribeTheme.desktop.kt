package com.companyb.companyapp.proto.sessionscribe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ScribePaper = Color(0xFFFBF7EE)
val ScribeCard = Color(0xFFFFFDF7)
val ScribeInk = Color(0xFF1E1A14)
val ScribeMuted = Color(0xFF7A7264)
val ScribeLine = Color(0xFFE3D9C2)
val ScribeRail = Color(0xFF171310)
val ScribeRailInk = Color(0xFFF5EDDC)
val ScribeRailMuted = Color(0xFFA89C84)
val ScribeAmber = Color(0xFFB7791F)
val ScribeAmberSoft = Color(0xFFF8EBCB)
val ScribeGreen = Color(0xFF2E7D4F)
val ScribeGreenSoft = Color(0xFFDDEEDF)
val ScribeRed = Color(0xFFB3372A)
val ScribeRedSoft = Color(0xFFF7E0DA)
val ScribeTeal = Color(0xFF1F5C55)
val ScribeTealSoft = Color(0xFFDCEBE7)
val ScribeHi = Color(0xFFFFD54D)

val ScribeMono = FontFamily.Monospace

private val ScribeColors =
    lightColorScheme(
        primary = ScribeTeal,
        onPrimary = Color.White,
        secondary = ScribeAmber,
        onSecondary = Color.White,
        tertiary = ScribeGreen,
        background = ScribePaper,
        onBackground = ScribeInk,
        surface = ScribeCard,
        onSurface = ScribeInk,
        surfaceVariant = ScribeAmberSoft,
        error = ScribeRed,
    )

private val ScribeType =
    Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        bodyLarge = TextStyle(fontSize = 14.sp),
        bodyMedium = TextStyle(fontSize = 13.sp),
        bodySmall = TextStyle(fontSize = 12.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
        labelSmall = TextStyle(fontSize = 11.sp),
    )

@Composable
fun SessionScribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScribeColors,
        typography = ScribeType,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp),
            large = RoundedCornerShape(14.dp),
        ),
        content = content,
    )
}

@Composable
fun ScribeCode(text: String) {
    Text(
        text = text,
        fontFamily = ScribeMono,
        fontSize = 11.sp,
        color = ScribeMuted,
    )
}

@Composable
fun SectionHeader(index: String, title: String, aside: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ScribeCode(index)
        Spacer(Modifier.width(8.dp))
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ScribeInk)
        if (aside.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(text = aside, fontSize = 12.sp, color = ScribeMuted)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun ScribeCard(accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScribeCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (accent != null) {
                Modifier.border(1.dp, accent, RoundedCornerShape(10.dp))
            } else {
                Modifier.border(1.dp, ScribeLine, RoundedCornerShape(10.dp))
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun ScribeTag(text: String, color: Color, soft: Color) {
    Box(
        modifier = Modifier
            .background(soft, RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun StatusTag(status: String) {
    val color = when (status) {
        "PENDING" -> ScribeAmber
        "COMPLETED", "SUBMITTED", "OPEN" -> ScribeGreen
        "NO_SHOW", "CANCELLED", "REVOKED", "DENIED", "DECLINED" -> ScribeRed
        "REMITTED", "PAST" -> ScribeMuted
        "DRAFT" -> ScribeTeal
        else -> ScribeTeal
    }
    val soft = when (status) {
        "PENDING" -> ScribeAmberSoft
        "COMPLETED", "SUBMITTED", "OPEN" -> ScribeGreenSoft
        "NO_SHOW", "CANCELLED", "REVOKED", "DENIED", "DECLINED" -> ScribeRedSoft
        else -> ScribeTealSoft
    }
    ScribeTag(text = status, color = color, soft = soft)
}

@Composable
fun ScribeButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = ScribeInk),
        modifier = Modifier.height(44.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScribeGhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(44.dp)) {
        Text(text, color = ScribeInk, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScribeLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = ScribeTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun FieldNote(text: String) {
    Text(text = text, fontSize = 12.sp, color = ScribeMuted)
}

@Composable
fun EmptyScribe(text: String, action: String = "", onAction: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScribeAmberSoft),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, ScribeLine, RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = text, fontSize = 13.sp, color = ScribeInk)
            if (action.isNotEmpty() && onAction != null) {
                Spacer(Modifier.height(6.dp))
                ScribeLink(text = action, onClick = onAction)
            }
        }
    }
}

@Composable
fun DayBanner(state: String, branchName: String, note: String) {
    val accent = when (state) {
        "OPEN" -> ScribeGreen
        "PAST" -> ScribeAmber
        else -> ScribeMuted
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = ScribeInk),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ScribeCode("BRANCH DAY · $branchName")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Today is a $state day — scribe fast, settle later.",
                    color = ScribeRailInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = note, color = ScribeRailMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .background(ScribeHi, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = state, fontWeight = FontWeight.Black, fontSize = 15.sp, color = ScribeInk)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(8.dp).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        ScribeCode("04:00 ASIA/MANILA BOUNDARY")
    }
}

@Composable
fun RailItem(label: String, selected: Boolean, badge: String = "", onClick: () -> Unit) {
    val bg = if (selected) ScribeHi else Color.Transparent
    val fg = if (selected) ScribeInk else ScribeRailInk
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(text = label, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, fontSize = 14.sp, color = fg)
        if (badge.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.background(ScribeRed, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(text = badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
fun QuickStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(ScribeCard, RoundedCornerShape(10.dp))
            .border(1.dp, ScribeLine, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(text = value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = ScribeInk)
        ScribeCode(label.uppercase())
    }
}

@Composable
fun RowScope.StatCell(label: String, value: String) {
    Box(modifier = Modifier.weight(1f)) {
        QuickStat(label = label, value = value)
    }
}
