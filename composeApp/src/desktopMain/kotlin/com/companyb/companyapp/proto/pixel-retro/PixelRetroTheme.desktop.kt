package com.companyb.companyapp.proto.pixelretro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PxVoid = Color(0xFF0F0F1B)
val PxPanel = Color(0xFF1D2B53)
val PxPanelHi = Color(0xFF29366F)
val PxInk = Color(0xFFFFF1E8)
val PxDim = Color(0xFF83769C)
val PxGreen = Color(0xFF00E436)
val PxYellow = Color(0xFFFFEC27)
val PxPink = Color(0xFFFF77A8)
val PxCyan = Color(0xFF29ADFF)
val PxRed = Color(0xFFFF004D)
val PxOrange = Color(0xFFFFA300)
val PxPurple = Color(0xFF7E2553)

val PxMono: FontFamily = FontFamily.Monospace

private val PxScheme =
    darkColorScheme(
        background = PxVoid,
        surface = PxPanel,
        primary = PxGreen,
        secondary = PxCyan,
        tertiary = PxPink,
        onBackground = PxInk,
        onSurface = PxInk,
    )

@Composable
fun PixelRetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PxScheme,
        content = content,
    )
}

@Composable
fun PxTitle(
    text: String,
    size: Int = 22,
    color: Color = PxYellow,
) {
    Text(
        text = text.uppercase(),
        fontFamily = PxMono,
        fontWeight = FontWeight.Black,
        fontSize = size.sp,
        letterSpacing = 2.sp,
        color = color,
    )
}

@Composable
fun PxBody(
    text: String,
    color: Color = PxInk,
    size: Int = 13,
) {
    Text(
        text = text,
        fontSize = size.sp,
        color = color,
        lineHeight = 18.sp,
    )
}

@Composable
fun PxDim(text: String) {
    Text(
        text = text,
        fontFamily = PxMono,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = PxDim,
    )
}

@Composable
fun PxRule(color: Color = PxPurple) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(color),
    )
}

@Composable
fun PxPanelBox(
    modifier: Modifier = Modifier,
    border: Color = PxCyan,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .background(PxPanel)
                .border(width = 3.dp, color = border, shape = RectangleShape)
                .padding(12.dp),
    ) {
        content()
    }
}

@Composable
fun PxButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fg: Color = PxVoid,
    bg: Color = PxGreen,
    modifier: Modifier = Modifier,
) {
    val back = if (enabled) bg else PxDim
    val front = if (enabled) fg else PxVoid
    Box(
        modifier =
            modifier
                .background(PxVoid)
                .padding(start = 0.dp, top = 0.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(back, RectangleShape)
                    .border(width = 3.dp, color = PxInk, shape = RectangleShape)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = label.uppercase(),
                fontFamily = PxMono,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = front,
            )
        }
    }
}

@Composable
fun PxTag(
    text: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .background(Color.Transparent)
                .border(width = 2.dp, color = color, shape = RectangleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = PxMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = color,
        )
    }
}

@Composable
fun PxMarquee(
    title: String,
    subtitle: String,
    right: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PxPurple)
                .border(width = 3.dp, color = PxPink, shape = RectangleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "◄►",
                fontFamily = PxMono,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = PxYellow,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                PxTitle(title, size = 20, color = PxYellow)
                PxDim("▓ $subtitle")
            }
            PxTag(right, PxGreen)
        }
    }
    Spacer(Modifier.height(4.dp))
    PxRule(PxPink)
    Spacer(Modifier.height(4.dp))
    PxRule(PxCyan)
}

@Composable
fun PxNavButton(
    label: String,
    selected: Boolean,
    badge: String = "",
    onClick: () -> Unit,
) {
    val marker = if (selected) "▶" else "▷"
    val bg = if (selected) PxPanelHi else PxPanel
    val edge = if (selected) PxYellow else PxDim
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg)
                .border(width = 2.dp, color = edge, shape = RectangleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = marker,
                fontFamily = PxMono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (selected) PxYellow else PxDim,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                fontFamily = PxMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = if (selected) PxInk else PxDim,
                modifier = Modifier.weight(1f),
            )
            if (badge.isNotEmpty()) {
                PxTag(badge, PxPink)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
