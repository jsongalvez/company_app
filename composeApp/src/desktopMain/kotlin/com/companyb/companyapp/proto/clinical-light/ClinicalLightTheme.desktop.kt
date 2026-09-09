package com.companyb.companyapp.proto.clinicallight

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

// Airy clinical-light tokens: white canvas, soft mint surfaces, calm teal primary,
// generous whitespace, hairline borders instead of shadows.

private val ClinicalPrimary = Color(0xFF0E7C6B)
private val ClinicalOnPrimary = Color(0xFFFFFFFF)
private val ClinicalPrimarySoft = Color(0xFFE2F2ED)
private val ClinicalCanvas = Color(0xFFFFFFFF)
private val ClinicalSurface = Color(0xFFF6FAF9)
private val ClinicalInk = Color(0xFF1B2E2C)
private val ClinicalMuted = Color(0xFF5D7471)
private val ClinicalLine = Color(0xFFE1ECEA)

private val ToneGreenFg = Color(0xFF0B6B4F)
private val ToneGreenBg = Color(0xFFDFF3E7)
private val ToneAmberFg = Color(0xFF8A5E00)
private val ToneAmberBg = Color(0xFFFFF1CF)
private val ToneRedFg = Color(0xFFB3261E)
private val ToneRedBg = Color(0xFFFDECEA)
private val ToneBlueFg = Color(0xFF1D5FC2)
private val ToneBlueBg = Color(0xFFE7EFFD)
private val ToneGreyFg = Color(0xFF4E6260)
private val ToneGreyBg = Color(0xFFEDF2F1)

val ClinicalPadPage = 32.dp
val ClinicalPadCard = 22.dp
val ClinicalGap = 16.dp
val ClinicalRadius = 14.dp

@Composable
fun ClinicalLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ClinicalPrimary,
            onPrimary = ClinicalOnPrimary,
            primaryContainer = ClinicalPrimarySoft,
            onPrimaryContainer = ClinicalPrimary,
            background = ClinicalCanvas,
            onBackground = ClinicalInk,
            surface = ClinicalCanvas,
            onSurface = ClinicalInk,
            surfaceVariant = ClinicalSurface,
            onSurfaceVariant = ClinicalMuted,
            outline = ClinicalLine,
            error = ToneRedFg,
        ),
        content = content,
    )
}

enum class ChipTone {
    GREEN,
    AMBER,
    RED,
    BLUE,
    GREY,
}

@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val bg = when (tone) {
        ChipTone.GREEN -> ToneGreenBg
        ChipTone.AMBER -> ToneAmberBg
        ChipTone.RED -> ToneRedBg
        ChipTone.BLUE -> ToneBlueBg
        ChipTone.GREY -> ToneGreyBg
    }
    val fg = when (tone) {
        ChipTone.GREEN -> ToneGreenFg
        ChipTone.AMBER -> ToneAmberFg
        ChipTone.RED -> ToneRedFg
        ChipTone.BLUE -> ToneBlueFg
        ChipTone.GREY -> ToneGreyFg
    }
    Box(
        modifier = modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

fun sessionTone(status: FakeSessionStatus, voided: Boolean): ChipTone {
    if (voided) return ChipTone.GREY
    return when (status) {
        FakeSessionStatus.PENDING -> ChipTone.AMBER
        FakeSessionStatus.COMPLETED -> ChipTone.GREEN
        FakeSessionStatus.NO_SHOW -> ChipTone.RED
        FakeSessionStatus.CANCELLED -> ChipTone.GREY
    }
}

fun dayTone(state: FakeDayState): ChipTone = when (state) {
    FakeDayState.OPEN -> ChipTone.GREEN
    FakeDayState.PAST -> ChipTone.AMBER
    FakeDayState.REMITTED -> ChipTone.BLUE
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClinicalRadius),
        colors = CardDefaults.cardColors(containerColor = ClinicalCanvas),
        border = BorderStroke(1.dp, ClinicalLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(ClinicalPadCard)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ClinicalMuted)
                    }
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
            Spacer(Modifier.height(ClinicalGap))
            content()
        }
    }
}

@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .background(ClinicalPrimarySoft, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ClinicalPrimary)
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = ClinicalPrimary, contentColor = ClinicalOnPrimary),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun QuietAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, color = ClinicalPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ClinicalMuted)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
