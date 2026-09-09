package com.companyb.companyapp.proto.glasspanels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Abyss = Color(0xFF0D0B26)
val IndigoDeep = Color(0xFF232055)
val VioletGlow = Color(0xFF7C6CF8)
val AquaGlow = Color(0xFF67E8F9)
val MintGlow = Color(0xFF6EE7B7)
val PeachGlow = Color(0xFFFDBA74)
val RoseGlow = Color(0xFFFDA4AF)
val FrostText = Color(0xFFF4F2FF)
val FrostDim = Color(0xFFB9B4D9)

val GlassSoft = Color.White.copy(alpha = 0.08f)
val GlassFirm = Color.White.copy(alpha = 0.14f)
val GlassStroke = Color.White.copy(alpha = 0.22f)
val GlassFaintStroke = Color.White.copy(alpha = 0.12f)

private val GlassColors = darkColorScheme(
    primary = AquaGlow,
    onPrimary = Abyss,
    primaryContainer = VioletGlow,
    onPrimaryContainer = FrostText,
    secondary = VioletGlow,
    onSecondary = FrostText,
    tertiary = MintGlow,
    onTertiary = Abyss,
    background = Abyss,
    onBackground = FrostText,
    surface = Color(0xFF17143A),
    onSurface = FrostText,
    surfaceVariant = Color(0xFF221E4E),
    onSurfaceVariant = FrostDim,
    outline = GlassStroke,
    error = RoseGlow,
    onError = Abyss,
)

private val GlassType = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraLight, fontSize = 52.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp),
)

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassColors, typography = GlassType, content = content)
}

@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(IndigoDeep, Abyss, Color(0xFF0A2E2C)))),
    ) {
        Box(
            modifier = Modifier.size(520.dp).offset((-140).dp, (-160).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(VioletGlow.copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(
            modifier = Modifier.size(460.dp).offset(880.dp, (-120).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(AquaGlow.copy(alpha = 0.35f), Color.Transparent))),
        )
        Box(
            modifier = Modifier.size(560.dp).offset(240.dp, 560.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF0EA5A4).copy(alpha = 0.30f), Color.Transparent))),
        )
        Box(
            modifier = Modifier.size(300.dp).offset(1020.dp, 480.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(PeachGlow.copy(alpha = 0.22f), Color.Transparent))),
        )
        content()
    }
}

@Composable
fun GlassSheet(modifier: Modifier = Modifier, radius: Dp = 22.dp, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(radius))
            .background(GlassSoft)
            .border(1.dp, GlassStroke, RoundedCornerShape(radius))
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun GlassButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = AquaGlow) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), VioletGlow.copy(alpha = 0.85f))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Abyss)
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FrostText)
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) AquaGlow.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) AquaGlow else GlassFaintStroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) FrostText else FrostDim,
        )
    }
}

@Composable
fun FaintLabel(text: String) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelSmall, color = FrostDim)
}

@Composable
fun SheetTitle(text: String, sub: String = "") {
    Column {
        Text(text = text, style = MaterialTheme.typography.titleLarge, color = FrostText)
        if (sub.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, style = MaterialTheme.typography.bodyMedium, color = FrostDim)
        }
    }
}

@Composable
fun GlassField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(text = hint, color = FrostDim) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun StatusPill(text: String, tint: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.22f))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FrostText)
    }
}

@Composable
fun TabPill(label: String, badge: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) GlassStroke else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) FrostText else FrostDim,
            modifier = Modifier.weight(1f),
        )
        if (badge.isNotEmpty()) {
            Text(text = badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AquaGlow)
        }
    }
}

@Composable
fun PaneRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 12.sp, color = FrostDim, modifier = Modifier.width(118.dp))
        Text(text = value, fontSize = 13.sp, color = FrostText, modifier = Modifier.weight(1f))
    }
}

@Composable
fun FlowRow(items: List<String>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in items) {
            Text(text = item, fontSize = 12.sp, color = FrostDim)
        }
    }
}
