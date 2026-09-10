package com.companyb.companyapp.proto.contextrail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val RailPaper = Color(0xFFFAF6EE)
val RailCard = Color(0xFFFFFFFF)
val RailLine = Color(0xFFE5DCC8)
val RailInk = Color(0xFF1B1A17)
val RailMuted = Color(0xFF6B645A)
val RailForest = Color(0xFF1A2E1F)
val RailForestText = Color(0xFFEDE6D3)
val RailForestDim = Color(0xFF9DB09A)
val RailInspector = Color(0xFF101828)
val RailInspectorLine = Color(0xFF2A3348)
val RailInspectorText = Color(0xFFF5F1E6)
val RailInspectorMuted = Color(0xFFB8B2A7)
val RailAccent = Color(0xFFB3541E)
val RailOnAccent = Color(0xFFFFFFFF)
val RailAccentSoft = Color(0xFFF5E0CC)
val RailAmber = Color(0xFFE8A838)

private val ToneGreenFg = Color(0xFF0B6B4F)
private val ToneGreenBg = Color(0xFFDFF3E7)
private val ToneAmberFg = Color(0xFF8A5E00)
private val ToneAmberBg = Color(0xFFFFF1CF)
private val ToneRedFg = Color(0xFFB3261E)
private val ToneRedBg = Color(0xFFFDECEA)
private val ToneBlueFg = Color(0xFF1D5FC2)
private val ToneBlueBg = Color(0xFFE7EFFD)
private val ToneGreyFg = Color(0xFF57534E)
private val ToneGreyBg = Color(0xFFEDE8DC)

val RailPadPage = 24.dp
val RailPadCard = 18.dp
val RailGap = 14.dp
val RailRadius = 12.dp
val RailNavWidth = 172.dp
val RailInspectorWidth = 360.dp

@Composable
fun ContextRailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RailAccent,
            onPrimary = RailOnAccent,
            primaryContainer = RailAccentSoft,
            onPrimaryContainer = RailAccent,
            background = RailPaper,
            onBackground = RailInk,
            surface = RailCard,
            onSurface = RailInk,
            surfaceVariant = RailPaper,
            onSurfaceVariant = RailMuted,
            outline = RailLine,
            error = ToneRedFg,
        ),
        content = content,
    )
}

enum class RailTone {
    GREEN,
    AMBER,
    RED,
    BLUE,
    GREY,
}

@Composable
fun RailChip(text: String, tone: RailTone, modifier: Modifier = Modifier) {
    val bg = when (tone) {
        RailTone.GREEN -> ToneGreenBg
        RailTone.AMBER -> ToneAmberBg
        RailTone.RED -> ToneRedBg
        RailTone.BLUE -> ToneBlueBg
        RailTone.GREY -> ToneGreyBg
    }
    val fg = when (tone) {
        RailTone.GREEN -> ToneGreenFg
        RailTone.AMBER -> ToneAmberFg
        RailTone.RED -> ToneRedFg
        RailTone.BLUE -> ToneBlueFg
        RailTone.GREY -> ToneGreyFg
    }
    Box(
        modifier = modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RailDarkChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(RailInspectorLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = RailAmber,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun sessionTone(status: RailSessionStatus, voided: Boolean): RailTone {
    if (voided) return RailTone.GREY
    return when (status) {
        RailSessionStatus.PENDING -> RailTone.AMBER
        RailSessionStatus.COMPLETED -> RailTone.GREEN
        RailSessionStatus.NO_SHOW -> RailTone.RED
        RailSessionStatus.CANCELLED -> RailTone.GREY
    }
}

fun dayTone(state: RailDayState): RailTone = when (state) {
    RailDayState.OPEN -> RailTone.GREEN
    RailDayState.PAST -> RailTone.AMBER
    RailDayState.REMITTED -> RailTone.BLUE
}

@Composable
fun PaperCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RailRadius),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = BorderStroke(1.dp, RailLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(RailPadCard)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RailMuted)
            }
            Spacer(Modifier.height(RailGap))
            content()
        }
    }
}

@Composable
fun RailNote(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .background(RailAccentSoft, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RailAccent)
    }
}

@Composable
fun RailInspectorNote(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .background(RailInspectorLine, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RailInspectorText)
    }
}

@Composable
fun RailPrimary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = RailAccent, contentColor = RailOnAccent),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RailOutline(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(label)
    }
}

@Composable
fun RailQuiet(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, color = RailAccent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RailKeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RailMuted)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RailDarkKeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RailInspectorMuted)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = RailInspectorText)
    }
}
