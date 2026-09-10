package com.companyb.companyapp.proto.solarizedcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SolBase3 = Color(0xFFFDF6E3)
val SolBase2 = Color(0xFFEEE8D5)
val SolBase1 = Color(0xFF93A1A1)
val SolBase0 = Color(0xFF839496)
val SolBase00 = Color(0xFF657B83)
val SolBase01 = Color(0xFF586E75)
val SolBase02 = Color(0xFF073642)
val SolBase03 = Color(0xFF002B36)
val SolYellow = Color(0xFFB58900)
val SolOrange = Color(0xFFCB4B16)
val SolRed = Color(0xFFDC322F)
val SolMagenta = Color(0xFFD33682)
val SolViolet = Color(0xFF6C71C4)
val SolBlue = Color(0xFF268BD2)
val SolCyan = Color(0xFF2AA198)
val SolGreen = Color(0xFF859900)

private val SolScheme =
    lightColorScheme(
        background = SolBase3,
        onBackground = SolBase01,
        surface = SolBase3,
        onSurface = SolBase01,
        surfaceVariant = SolBase2,
        onSurfaceVariant = SolBase00,
        primary = SolBlue,
        onPrimary = Color.White,
        primaryContainer = SolBase2,
        onPrimaryContainer = SolBase02,
        secondary = SolCyan,
        onSecondary = Color.White,
        tertiary = SolGreen,
        outline = SolBase1,
        outlineVariant = SolBase2,
        error = SolRed,
        onError = Color.White,
    )

val SolMono: FontFamily = FontFamily.Monospace
val SolSans: FontFamily = FontFamily.SansSerif

@Composable
fun SolarizedCalmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SolScheme, content = content)
}

@Composable
fun CalmPage(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    topNote: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(SolBase3),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text(text = "← back", color = SolBase00, fontSize = 14.sp)
                }
            }
            Text(
                text = title,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = SolBase02,
            )
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, fontSize = 14.sp, lineHeight = 21.sp, color = SolBase00)
            }
            if (topNote != null) {
                topNote()
            }
            content()
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "○ ○ ○", fontSize = 12.sp, color = SolBase1, fontFamily = SolMono)
            }
        }
    }
}

@Composable
fun CalmRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SolBase2))
}

@Composable
fun CalmSection(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(SolBase2))
            Text(
                text = title,
                fontSize = 12.sp,
                fontFamily = SolMono,
                fontWeight = FontWeight.Medium,
                color = SolBase00,
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(SolBase2))
        }
    }
}

@Composable
fun CalmBody(text: String) {
    Text(text = text, fontSize = 14.sp, lineHeight = 22.sp, color = SolBase01)
}

@Composable
fun CalmQuiet(text: String) {
    Text(text = text, fontSize = 13.sp, lineHeight = 20.sp, color = SolBase00)
}

@Composable
fun CalmMono(text: String) {
    Text(text = text, fontSize = 13.sp, lineHeight = 19.sp, color = SolBase00, fontFamily = SolMono)
}

@Composable
fun CalmRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = SolBase00, fontFamily = SolMono)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = SolBase02,
        )
    }
}

@Composable
fun CalmPrimary(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SolBlue,
                contentColor = Color.White,
                disabledContainerColor = SolBase2,
                disabledContentColor = SolBase0,
            ),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
fun CalmGhost(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = SolBase01,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

@Composable
fun CalmSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(SolBase2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun CalmChip(
    text: String,
    tone: Color,
) {
    Box(
        modifier = Modifier.background(tone.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontFamily = SolMono, color = tone)
    }
}

@Composable
fun CalmDayBanner(
    branchName: String,
    day: CalmDay,
) {
    val (dot, label) =
        when (day) {
            CalmDay.OPEN -> SolGreen to "OPEN — receiving today"
            CalmDay.PAST -> SolYellow to "PAST — read only, remit soon"
            CalmDay.REMITTED -> SolViolet to "REMITTED — sealed snapshot"
        }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(SolBase2, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalmChip(text = "● ${day.name}", tone = dot)
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SolBase02,
            )
        }
        Text(
            text = "$branchName · the Branch Day turns at 04:00 Asia/Manila. Before that hour it is still yesterday.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = SolBase00,
        )
    }
}

fun calmMoney(amount: Double): String {
    val whole = amount.toLong()
    val text = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$text"
}
