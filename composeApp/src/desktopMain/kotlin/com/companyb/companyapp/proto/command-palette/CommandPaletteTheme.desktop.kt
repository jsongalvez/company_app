package com.companyb.companyapp.proto.commandpalette

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CpNight {
    val Canvas = Color(0xFF161C27)
    val Sidebar = Color(0xFF10151E)
    val Panel = Color(0xFF1D2431)
    val PanelSoft = Color(0xFF222B3B)
    val Hairline = Color(0xFF2C3547)
    val Ink = Color(0xFFE9EEF4)
    val Muted = Color(0xFF93A0B4)
    val Faint = Color(0xFF647082)
    val Accent = Color(0xFFA9CBBE)
    val AccentInk = Color(0xFF0E1A16)
    val Gold = Color(0xFFE3C878)
    val Green = Color(0xFF8FD0A4)
    val Amber = Color(0xFFE3B778)
    val Red = Color(0xFFE39A93)
    val Blue = Color(0xFF9DBDE8)
    val Violet = Color(0xFFC2AEE0)
    val Scrim = Color(0xB30B0F16)
}

fun cpScheme() =
    darkColorScheme(
        background = CpNight.Canvas,
        surface = CpNight.Panel,
        surfaceVariant = CpNight.PanelSoft,
        primary = CpNight.Accent,
        onPrimary = CpNight.AccentInk,
        secondary = CpNight.Blue,
        tertiary = CpNight.Gold,
        onBackground = CpNight.Ink,
        onSurface = CpNight.Ink,
        onSurfaceVariant = CpNight.Muted,
        outline = CpNight.Hairline,
        error = CpNight.Red,
        onError = CpNight.AccentInk,
    )

fun CpDayState.tint(): Color =
    when (this) {
        CpDayState.OPEN -> CpNight.Green
        CpDayState.PAST -> CpNight.Amber
        CpDayState.REMITTED -> CpNight.Blue
    }

fun CpSessionStatus.tint(): Color =
    when (this) {
        CpSessionStatus.PENDING -> CpNight.Gold
        CpSessionStatus.COMPLETED -> CpNight.Green
        CpSessionStatus.NO_SHOW -> CpNight.Amber
        CpSessionStatus.CANCELLED -> CpNight.Red
    }

fun cpMoney(amount: Int): String =
    "P" +
        amount
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

@Composable
fun CpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var box =
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CpNight.Panel)
            .border(1.dp, CpNight.Hairline, RoundedCornerShape(14.dp))
    if (onClick != null) box = box.clickable(onClick = onClick)
    Column(box.padding(18.dp), content = content)
}

@Composable
fun CpChip(
    text: String,
    color: Color,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun CpSectionTitle(
    text: String,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = CpNight.Faint,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontSize = 12.sp, color = CpNight.Faint)
        }
    }
}

@Composable
fun CpPageTitle(
    title: String,
    subtitle: String,
) {
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CpNight.Ink)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, fontSize = 14.sp, color = CpNight.Muted)
    Spacer(Modifier.height(18.dp))
}

@Composable
fun CpKeyHint(keys: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CpNight.PanelSoft)
            .border(1.dp, CpNight.Hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(keys, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Muted)
    }
}

@Composable
fun CpNavRow(
    label: String,
    hint: String,
    active: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val bg = if (active) CpNight.Accent.copy(alpha = 0.13f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) CpNight.Accent else CpNight.Ink,
            )
            Text(hint, fontSize = 11.sp, color = CpNight.Faint)
        }
        if (badge != null) CpChip(badge, CpNight.Gold)
    }
}

@Composable
fun CpEmptyNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CpNight.Panel)
            .border(1.dp, CpNight.Hairline, RoundedCornerShape(12.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = CpNight.Muted)
    }
}

@Composable
fun CpStatRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = CpNight.Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
    }
}

@Composable
fun CpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = cpScheme(), content = content)
}

@Composable
fun CpPrimaryButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = CpNight.Accent, contentColor = CpNight.AccentInk),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CpSecondaryButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) CpNight.Ink else CpNight.Faint)
    }
}

@Composable
fun CpSmallButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 12.sp, color = CpNight.Accent)
    }
}

@Composable
fun CpTwoCol(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), content = left)
        Column(Modifier.weight(1f), content = right)
    }
}

@Composable
fun RowScope.CpSpacer() {
    Spacer(Modifier.weight(1f))
}

@Composable
fun CpVSpace(height: Int = 16) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun CpHSpace(width: Int = 12) {
    Spacer(Modifier.width(width.dp))
}
