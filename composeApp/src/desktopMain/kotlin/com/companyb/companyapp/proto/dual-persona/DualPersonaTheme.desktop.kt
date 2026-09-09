package com.companyb.companyapp.proto.dualpersona

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

// #781 — dual-persona theme: one record set, two lenses. Practitioner = warm treatment-room
// light; Coordinator = dark ops-bridge. The palette flips; the data never does.

data class DpPalette(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val ok: Color,
    val okBand: Color,
    val warn: Color,
    val warnBand: Color,
    val danger: Color,
    val dangerBand: Color,
    val infoBand: Color,
)

val DpPractitioner = DpPalette(
    bg = Color(0xFFFFF7EE),
    surface = Color(0xFFFFEFE0),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF2A2118),
    muted = Color(0xFF8A7560),
    line = Color(0xFFEAD9C2),
    accent = Color(0xFFC4552D),
    accentInk = Color(0xFFFFFFFF),
    ok = Color(0xFF2E7D4F),
    okBand = Color(0xFFDFF0E4),
    warn = Color(0xFF9A6200),
    warnBand = Color(0xFFF7E8C4),
    danger = Color(0xFFB3372F),
    dangerBand = Color(0xFFF6DCD7),
    infoBand = Color(0xFFF3E4D3),
)

val DpCoordinator = DpPalette(
    bg = Color(0xFF0E131B),
    surface = Color(0xFF151D29),
    card = Color(0xFF1A2331),
    ink = Color(0xFFE9EEF6),
    muted = Color(0xFF8E9BB0),
    line = Color(0xFF2B3648),
    accent = Color(0xFF6E8BFF),
    accentInk = Color(0xFF0B1020),
    ok = Color(0xFF3ECF8E),
    okBand = Color(0xFF12362A),
    warn = Color(0xFFF5A524),
    warnBand = Color(0xFF3A2C10),
    danger = Color(0xFFF2555A),
    dangerBand = Color(0xFF3D1A1E),
    infoBand = Color(0xFF1B2740),
)

fun DpPersona.palette(): DpPalette = when (this) {
    DpPersona.PRACTITIONER -> DpPractitioner
    DpPersona.COORDINATOR -> DpCoordinator
}

fun DpPersona.lensName(): String = when (this) {
    DpPersona.PRACTITIONER -> "Treatment Room"
    DpPersona.COORDINATOR -> "Ops Bridge"
}

fun DpDayStatus.band(p: DpPalette): Color = when (this) {
    DpDayStatus.OPEN -> p.okBand
    DpDayStatus.PAST -> p.warnBand
    DpDayStatus.REMITTED -> p.infoBand
}

fun DpDayStatus.ink(p: DpPalette): Color = when (this) {
    DpDayStatus.OPEN -> p.ok
    DpDayStatus.PAST -> p.warn
    DpDayStatus.REMITTED -> p.accent
}

fun DpSessionStatus.chipBand(p: DpPalette): Color = when (this) {
    DpSessionStatus.PENDING -> p.warnBand
    DpSessionStatus.COMPLETED -> p.okBand
    DpSessionStatus.NO_SHOW -> p.infoBand
    DpSessionStatus.CANCELLED -> p.dangerBand
}

fun DpSessionStatus.chipInk(p: DpPalette): Color = when (this) {
    DpSessionStatus.PENDING -> p.warn
    DpSessionStatus.COMPLETED -> p.ok
    DpSessionStatus.NO_SHOW -> p.accent
    DpSessionStatus.CANCELLED -> p.danger
}

@Composable
fun DpCard(p: DpPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(p.card, RoundedCornerShape(12.dp))
            .border(1.dp, p.line, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun DpSectionTitle(p: DpPalette, text: String, aside: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (aside != null) Text(aside, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DpChip(p: DpPalette, text: String, band: Color, ink: Color) {
    Text(
        text,
        color = ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(band, RoundedCornerShape(20.dp))
            .border(1.dp, ink.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun DpButton(p: DpPalette, label: String, onClick: () -> Unit, primary: Boolean = true, enabled: Boolean = true) {
    val bg = if (primary) p.accent else p.surface
    val fg = if (primary) p.accentInk else p.ink
    Text(
        label,
        color = if (enabled) fg else p.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(
            if (enabled) bg else p.surface,
            RoundedCornerShape(9.dp),
        )
            .border(1.dp, if (primary) Color.Transparent else p.line, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun DpRowButtons(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun DpKeyValue(p: DpPalette, key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(132.dp))
        Text(value, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DpNote(p: DpPalette, text: String) {
    Text(
        text,
        color = p.muted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
            .background(p.infoBand, RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}
