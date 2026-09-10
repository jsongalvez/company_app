package com.companyb.companyapp.proto.desertsand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #808 — desert-sand theme. Sun-baked sandstone backdrop, adobe cards with
// scorched top edges, turquoise jewelry accents, high-noon contrast ink.

val DsDune = Color(0xFFEFE0BE)
val DsDuneDeep = Color(0xFFE4CDA0)
val DsAdobe = Color(0xFFFBF4E3)
val DsAdobeEdge = Color(0xFFD9BE8C)
val DsScorch = Color(0xFFB4552D)
val DsScorchDeep = Color(0xFF8A3E20)
val DsTurquoise = Color(0xFF1F7A78)
val DsTurquoisePale = Color(0xFFD7EAE4)
val DsSunGold = Color(0xFFD99A1F)
val DsSunPale = Color(0xFFF7E7C3)
val DsInk = Color(0xFF3A2A1B)
val DsInkSoft = Color(0xFF7C6A52)
val DsMesa = Color(0xFF6B4A2E)
val DsPalm = Color(0xFF5C6E3C)
val DsDanger = Color(0xFF9C2F1F)

val DsSans = FontFamily.SansSerif

@Composable
fun DsRoot(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = DsDune,
            surface = DsAdobe,
            surfaceVariant = DsDuneDeep,
            primary = DsScorch,
            onPrimary = Color.White,
            secondary = DsTurquoise,
            onSecondary = Color.White,
            tertiary = DsSunGold,
            onBackground = DsInk,
            onSurface = DsInk,
        ),
        content = {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(listOf(DsDune, DsDuneDeep)),
                ),
            ) { content() }
        },
    )
}

@Composable
fun DsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = DsSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = DsMesa,
    )
}

@Composable
fun DsAdobeCard(
    modifier: Modifier = Modifier,
    accent: Color = DsScorch,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var card = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(DsAdobe)
    if (onClick != null) card = card.clickable(onClick = onClick)
    Column(modifier = modifier.then(card).padding(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(accent),
        )
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun DsChip(
    text: String,
    bg: Color,
    fg: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = DsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = fg,
        )
    }
}

fun DsStatusColor(status: DsSessionStatus): Color = when (status) {
    DsSessionStatus.PENDING -> DsSunGold
    DsSessionStatus.COMPLETED -> DsPalm
    DsSessionStatus.NO_SHOW -> DsInkSoft
    DsSessionStatus.CANCELLED -> DsDanger
}

fun DsDayColor(day: DsDayStatus): Color = when (day) {
    DsDayStatus.OPEN -> DsTurquoise
    DsDayStatus.PAST -> DsInkSoft
    DsDayStatus.REMITTED -> DsMesa
}

@Composable
fun DsButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) DsScorch else DsTurquoisePale)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = DsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (primary) Color.White else DsTurquoise,
        )
    }
}

@Composable
fun DsNavRail(
    items: List<Pair<DsScreen, String>>,
    current: DsScreen,
    unread: Int,
    onPick: (DsScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .background(DsInk)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "DESERT SAND",
            fontFamily = DsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
            color = DsSunGold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            text = "high-noon ops board",
            fontFamily = DsSans,
            fontSize = 11.sp,
            color = DsAdobeEdge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        for ((screen, label) in items) {
            val selected = screen == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) DsScorch else Color.Transparent)
                    .clickable { onPick(screen) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Box(Modifier.size(8.dp).background(DsSunGold, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    fontFamily = DsSans,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else DsAdobeEdge,
                )
                if (screen == DsScreen.MAIL && unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DsSunGold)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = unread.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DsInk,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4A3826))
                .padding(10.dp),
        ) {
            Text(
                text = "04:00 Asia/Manila cuts the Branch Day",
                fontFamily = DsSans,
                fontSize = 11.sp,
                color = DsSunPale,
            )
        }
    }
}
