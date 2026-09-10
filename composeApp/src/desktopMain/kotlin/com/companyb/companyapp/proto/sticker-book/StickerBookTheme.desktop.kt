package com.companyb.companyapp.proto.stickerbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val SbPaper = Color(0xFFFFF7EA)
val SbPaperDeep = Color(0xFFF2E3C8)
val SbInk = Color(0xFF3A3129)
val SbInkSoft = Color(0xFF6B5D4E)
val SbMuted = Color(0xFF9A8874)
val SbCoral = Color(0xFFFF6B5E)
val SbCoralDeep = Color(0xFFD14A3E)
val SbSun = Color(0xFFFFC53D)
val SbLeaf = Color(0xFF3FA45B)
val SbLeafDeep = Color(0xFF2B7A42)
val SbSky = Color(0xFF4AA8E0)
val SbGrape = Color(0xFF9B6BD3)
val SbGrapeDeep = Color(0xFF7448A8)
val SbBubble = Color(0xFFF471B5)
val SbCard = Color(0xFFFFFFFF)
val SbUnread = Color(0xFFFFE9A8)
val SbOpen = Color(0xFF4AA8E0)
val SbPast = Color(0xFF9A8874)
val SbRemitted = Color(0xFFFFC53D)

private val SbTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 17.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun StickerBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SbCoral,
            onPrimary = Color.White,
            secondary = SbSky,
            tertiary = SbLeaf,
            background = SbPaper,
            surface = SbCard,
            onBackground = SbInk,
            onSurface = SbInk,
        ),
        typography = SbTypography,
        content = content,
    )
}

@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    edge: Color = Color.White,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val inner = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .background(edge)
            .padding(4.dp)
            .clip(inner)
            .background(SbCard)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun SbKicker(text: String, color: Color = SbCoral) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 2.sp)
}

@Composable
fun SbPill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .shadow(if (selected) 4.dp else 0.dp, shape)
            .clip(shape)
            .background(if (selected) color else Color.White)
            .border(2.dp, SbInk, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (selected && color != SbSun) Color.White else SbInk,
        )
    }
}

@Composable
fun SbStatusSticker(text: String, color: Color) {
    val shape = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .shadow(3.dp, shape)
            .clip(shape)
            .background(Color.White)
            .padding(2.dp)
            .clip(inner)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
fun BadgeStar(label: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(104.dp)) {
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color(0x33000000),
                    radius = size.minDimension * 0.48f,
                    center = androidx.compose.ui.geometry.Offset(c.x, c.y + 4f),
                )
                drawCircle(color = Color.White, radius = size.minDimension * 0.48f, center = c)
                val path = Path()
                val points = 12
                repeat(points * 2) { i ->
                    val r = (if (i % 2 == 0) 0.42f else 0.33f) * size.minDimension
                    val a = (PI.toFloat() * i / points) - PI.toFloat() / 2f
                    val x = c.x + r * cos(a)
                    val y = c.y + r * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path = path, color = color)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun SbMeter(label: String, value: Int, target: Int, color: Color) {
    val frac = if (target <= 0) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SbInkSoft)
            Text("$value / $target", style = MaterialTheme.typography.labelSmall, color = SbInk)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, SbInk, RoundedCornerShape(8.dp))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .height(12.dp),
            )
        }
    }
}
