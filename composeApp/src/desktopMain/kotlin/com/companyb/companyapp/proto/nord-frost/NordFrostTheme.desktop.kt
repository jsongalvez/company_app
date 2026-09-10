package com.companyb.companyapp.proto.nordfrost

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NfSnow = Color(0xFFECEFF4)
val NfSnowSoft = Color(0xFFE5E9F0)
val NfDrift = Color(0xFFD8DEE9)
val NfPanel = Color(0xFFF7F9FC)
val NfNight = Color(0xFF2E3440)
val NfNightSoft = Color(0xFF3B4252)
val NfSlate = Color(0xFF4C566A)
val NfMist = Color(0xFF7B8AA0)
val NfFrost = Color(0xFF88C0D0)
val NfFrostDeep = Color(0xFF81A1C1)
val NfGlacier = Color(0xFF5E81AC)
val NfTeal = Color(0xFF8FBCBB)
val NfTealInk = Color(0xFF2F6B6B)
val NfAmber = Color(0xFFB9882F)
val NfAmberWash = Color(0xFFFAF3E2)
val NfRose = Color(0xFFB45F6D)
val NfRoseWash = Color(0xFFF9E9EC)
val NfGlacierWash = Color(0xFFE3EDF6)
val NfTealWash = Color(0xFFE2F0EF)

private val NfScheme =
    lightColorScheme(
        primary = NfGlacier,
        onPrimary = Color.White,
        secondary = NfFrostDeep,
        background = NfSnow,
        surface = NfPanel,
        onBackground = NfNight,
        onSurface = NfNight,
    )

private val NfMicro =
    TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
    )

@Composable
fun NordFrostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NfScheme, content = content)
}

@Composable
fun NfTitle(
    text: String,
    size: Int = 20,
) {
    Text(
        text = text.uppercase(),
        style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        color = NfNight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun NfBody(text: String) {
    Text(text = text, fontSize = 14.sp, color = NfNightSoft)
}

@Composable
fun NfDim(text: String) {
    Text(text = text, fontSize = 12.sp, color = NfMist)
}

@Composable
fun NfMicroLabel(text: String) {
    Text(text = text.uppercase(), style = NfMicro, color = NfGlacier)
}

@Composable
fun NfFrostRule(color: Color = NfFrost) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color.copy(alpha = 0.55f)),
    )
}

@Composable
fun NfPanelBox(
    border: Color = NfDrift,
    wash: Color = NfPanel,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(wash)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(14.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun NfButton(
    text: String,
    onClick: () -> Unit,
    bg: Color = NfGlacier,
    fg: Color = Color.White,
    enabled: Boolean = true,
) {
    val back = if (enabled) bg else NfDrift
    val front = if (enabled) fg else NfMist
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(back)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
            color = front,
        )
    }
}

@Composable
fun NfGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, NfFrostDeep, RoundedCornerShape(9.dp))
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
            color = NfGlacier,
        )
    }
}

@Composable
fun NfTag(
    text: String,
    dot: Color = NfFrostDeep,
    wash: Color = NfGlacierWash,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(wash)
                .border(1.dp, dot.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = NfNight,
        )
    }
}

@Composable
fun NfMarquee(
    title: String,
    subtitle: String,
    right: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, NfDrift)
                .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "❄", fontSize = 20.sp, color = NfFrostDeep)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.0.sp),
                    color = NfNight,
                )
                NfDim(subtitle)
            }
            NfTag(right, dot = NfTeal, wash = NfTealWash)
        }
        Spacer(Modifier.height(8.dp))
        NfFrostRule()
    }
}

@Composable
fun NfNavButton(
    text: String,
    selected: Boolean,
    badge: String = "",
    onClick: () -> Unit,
) {
    val back = if (selected) NfGlacier else Color.Transparent
    val front = if (selected) Color.White else NfNightSoft
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(back)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selected) "❄ $text" else "◇ $text",
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = front,
                modifier = Modifier.weight(1f),
            )
            if (badge.isNotEmpty()) {
                Text(
                    text = badge,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp),
                    color = if (selected) Color.White else NfGlacier,
                )
            }
        }
    }
}
