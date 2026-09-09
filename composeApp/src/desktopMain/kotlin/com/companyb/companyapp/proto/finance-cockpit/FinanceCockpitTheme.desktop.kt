package com.companyb.companyapp.proto.financecockpit

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CockpitColors {
    val Paper = Color(0xFFF7F2E6)
    val PaperDeep = Color(0xFFEFE6D2)
    val Ink = Color(0xFF1B1A14)
    val Muted = Color(0xFF6B6350)
    val Hairline = Color(0xFFD9CDAE)
    val Brass = Color(0xFF8A6D1B)
    val BrassDeep = Color(0xFF4A3608)
    val Gold = Color(0xFFB8912C)
    val Pine = Color(0xFF10382B)
    val PineDeep = Color(0xFF0A261D)
    val Emerald = Color(0xFF0E7C4B)
    val Cream = Color(0xFFFFFBEE)
    val Danger = Color(0xFFA33A2A)
    val Slate = Color(0xFF3E4A52)
}

val CockpitPadPage = 28.dp
val CockpitGap = 12.dp
val CockpitRadius = 14.dp

@Composable
fun CockpitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CockpitColors.Brass,
            onPrimary = Color.White,
            surface = CockpitColors.Paper,
            onSurface = CockpitColors.Ink,
            surfaceVariant = CockpitColors.PaperDeep,
            onSurfaceVariant = CockpitColors.Muted,
            outline = CockpitColors.Hairline,
        ),
        content = content,
    )
}

fun cockpitPeso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

@Composable
fun CockpitLedgerCard(
    title: String,
    subtitle: String? = null,
    accent: Color = CockpitColors.Brass,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CockpitRadius))
            .background(CockpitColors.Cream)
            .border(1.dp, CockpitColors.Hairline, RoundedCornerShape(CockpitRadius))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).background(accent, RoundedCornerShape(3.dp)).padding(vertical = 14.dp)) { }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CockpitColors.Ink)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = CockpitColors.Muted)
            }
        }
        content()
    }
}

@Composable
fun CockpitChip(text: String, tone: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(999.dp)
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        mod
            .clip(shape)
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone)
    }
}

fun cockpitToneFor(status: CockpitSessionStatus): Color = when (status) {
    CockpitSessionStatus.PENDING -> CockpitColors.Gold
    CockpitSessionStatus.COMPLETED -> CockpitColors.Emerald
    CockpitSessionStatus.NO_SHOW -> CockpitColors.Slate
    CockpitSessionStatus.CANCELLED -> CockpitColors.Danger
}

fun cockpitToneFor(day: CockpitDayState): Color = when (day) {
    CockpitDayState.OPEN -> CockpitColors.Emerald
    CockpitDayState.PAST -> CockpitColors.Gold
    CockpitDayState.REMITTED -> CockpitColors.BrassDeep
}

@Composable
fun CockpitPrimary(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = CockpitColors.BrassDeep, contentColor = Color.White),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CockpitSecondary(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(text)
    }
}

@Composable
fun CockpitQuiet(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = CockpitColors.BrassDeep, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CockpitNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CockpitColors.PaperDeep)
            .border(1.dp, CockpitColors.Hairline, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, fontSize = 13.sp, color = CockpitColors.Muted)
    }
}

@Composable
fun CockpitMoneyRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = CockpitColors.Muted, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = if (strong) 18.sp else 14.sp,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = CockpitColors.Ink,
        )
    }
}

@Composable
fun CockpitRowButtons(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}
