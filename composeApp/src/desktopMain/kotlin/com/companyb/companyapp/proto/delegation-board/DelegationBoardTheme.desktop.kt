package com.companyb.companyapp.proto.delegationboard

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

val ManifestPaper = Color(0xFFF4F1E6)
val ManifestCard = Color(0xFFFFFDF6)
val ManifestInk = Color(0xFF22302C)
val ManifestMuted = Color(0xFF6B7672)
val ManifestLine = Color(0xFFD8D2C0)
val TriageRed = Color(0xFFB3372A)
val TriageRedSoft = Color(0xFFF7E3DF)
val TriageAmber = Color(0xFF9A6B14)
val TriageAmberSoft = Color(0xFFF6ECD4)
val TriageGreen = Color(0xFF2E7D4F)
val TriageGreenSoft = Color(0xFFDFEFE4)
val TagBlack = Color(0xFF2B2B28)
val DispatchTeal = Color(0xFF1F5C55)
val DispatchTealSoft = Color(0xFFDCEBE7)

val Mono = FontFamily.Monospace

private val DelegationColors =
    lightColorScheme(
        primary = TriageRed,
        onPrimary = Color.White,
        secondary = DispatchTeal,
        onSecondary = Color.White,
        tertiary = TriageAmber,
        background = ManifestPaper,
        onBackground = ManifestInk,
        surface = ManifestCard,
        onSurface = ManifestInk,
        surfaceVariant = DispatchTealSoft,
        error = TriageRed,
    )

private val DelegationType =
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

private val DelegationShapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
    )

@Composable
fun DelegationBoardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DelegationColors,
        typography = DelegationType,
        shapes = DelegationShapes,
        content = content,
    )
}

@Composable
fun ManifestCode(text: String) {
    Text(
        text = text,
        fontFamily = Mono,
        fontSize = 11.sp,
        color = ManifestMuted,
    )
}

@Composable
fun SectionHeader(
    index: String,
    title: String,
    aside: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.White,
            modifier =
                Modifier
                    .background(
                        TagBlack,
                        RoundedCornerShape(4.dp),
                    ).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = ManifestInk,
        )
        if (aside.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(text = aside, fontSize = 12.sp, color = ManifestMuted)
        }
    }
}

@Composable
fun TriageTag(
    text: String,
    color: Color,
    soft: Color,
) {
    Text(
        text = text.uppercase(),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier.background(soft, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusTag(status: String) {
    val lowered = status.uppercase()
    val color =
        when (lowered) {
            "COMPLETED", "OPEN", "ACCEPTED", "GRANTED", "SUBMITTED", "READ" -> TriageGreen
            "PENDING", "PAST", "DRAFT" -> TriageAmber
            "NO_SHOW", "CANCELLED", "REMITTED", "DECLINED", "DENIED", "REVOKED", "UNREAD" -> TriageRed
            else -> DispatchTeal
        }
    val soft =
        when (lowered) {
            "COMPLETED", "OPEN", "ACCEPTED", "GRANTED", "SUBMITTED", "READ" -> TriageGreenSoft
            "PENDING", "PAST", "DRAFT" -> TriageAmberSoft
            "NO_SHOW", "CANCELLED", "REMITTED", "DECLINED", "DENIED", "REVOKED", "UNREAD" -> TriageRedSoft
            else -> DispatchTealSoft
        }
    TriageTag(text = lowered.replace('_', ' '), color = color, soft = soft)
}

@Composable
fun ManifestCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = ManifestCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManifestLine),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (accent != null) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(accent))
            }
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun StampBanner(
    state: String,
    branchName: String,
    note: String,
) {
    val color =
        when (state.uppercase()) {
            "OPEN" -> TriageGreen
            "PAST" -> TriageAmber
            else -> TriageRed
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(2.dp, color, RoundedCornerShape(6.dp))
                .background(ManifestCard)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = color,
            modifier =
                Modifier
                    .border(
                        2.dp,
                        color,
                        RoundedCornerShape(4.dp),
                    ).padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = branchName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ManifestInk)
            Text(text = note, fontSize = 12.sp, color = ManifestMuted)
        }
    }
}

@Composable
fun RailItem(
    label: String,
    selected: Boolean,
    badge: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (selected) DispatchTealSoft else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(if (selected) DispatchTeal else Color.Transparent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (selected) DispatchTeal else ManifestInk,
            modifier = Modifier.weight(1f),
        )
        if (badge.isNotEmpty()) {
            Text(
                text = badge,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .background(
                            TriageRed,
                            RoundedCornerShape(8.dp),
                        ).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun BoardButton(
    text: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (danger) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = TriageRed),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(text = text, fontSize = 12.sp)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = DispatchTeal),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(text = text, fontSize = 12.sp)
        }
    }
}

@Composable
fun BoardGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = DispatchTeal)
    }
}

@Composable
fun BoardLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text = text, fontSize = 12.sp, color = TriageRed, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FieldNote(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(DispatchTealSoft, RoundedCornerShape(6.dp))
                .padding(10.dp),
    ) {
        Text(text = "FIELD NOTE — ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DispatchTeal)
        Text(text = text, fontSize = 12.sp, color = ManifestInk)
    }
}

@Composable
fun EmptyManifest(
    line: String,
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, ManifestLine, RoundedCornerShape(6.dp))
                .background(ManifestCard)
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "[ ___ ]", fontFamily = Mono, fontSize = 16.sp, color = ManifestMuted)
        Spacer(Modifier.height(6.dp))
        Text(text = line, fontSize = 13.sp, color = ManifestMuted)
        if (actionLabel.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            BoardGhostButton(text = actionLabel, onClick = onAction)
        }
    }
}
