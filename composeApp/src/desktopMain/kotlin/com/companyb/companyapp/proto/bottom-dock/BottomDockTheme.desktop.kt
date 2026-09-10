package com.companyb.companyapp.proto.bottomdock

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #810 — bottom-dock theme: playful macOS desktop. Grape-to-teal wallpaper,
// warm paper windows with traffic lights, candy dock tiles. Deliberately
// distinct from every other desktop variant: the dock is the only nav.

object DockColors {
    val WallpaperTop = Color(0xFF3A2060)
    val WallpaperBottom = Color(0xFF0E3F46)
    val Bubble = Color(0xFFFFFFFF)
    val MenuBar = Color(0xFF1B1230)
    val MenuBarText = Color(0xFFF4EEFF)
    val Window = Color(0xFFF6F1E6)
    val WindowBar = Color(0xFFE9E0CF)
    val Ink = Color(0xFF262033)
    val InkSoft = Color(0xFF6F6884)
    val CardEdge = Color(0xFFE0D5BE)
    val Grape = Color(0xFF7C5CFF)
    val GrapeInk = Color(0xFFFFFFFF)
    val Pink = Color(0xFFFF6FA5)
    val Sunny = Color(0xFFFFC94D)
    val Mint = Color(0xFF1F9D63)
    val MintBg = Color(0xFFDDF5E7)
    val Coral = Color(0xFFD64545)
    val CoralBg = Color(0xFFF9DEDE)
    val Sky = Color(0xFF2F7FD1)
    val SkyBg = Color(0xFFDCEBFA)
    val Amber = Color(0xFF9A6B00)
    val AmberBg = Color(0xFFF8E8C4)
    val TrafficRed = Color(0xFFFF5F57)
    val TrafficYellow = Color(0xFFFEBC2E)
    val TrafficGreen = Color(0xFF28C840)
    val DockBar = Color(0xB3FFFFFF)
    val DockEdge = Color(0x66FFFFFF)
}

fun DockDayStatus.band(): Color =
    when (this) {
        DockDayStatus.OPEN -> DockColors.Mint
        DockDayStatus.PAST -> DockColors.Sunny
        DockDayStatus.REMITTED -> DockColors.Sky
    }

fun DockDayStatus.bandBg(): Color =
    when (this) {
        DockDayStatus.OPEN -> DockColors.MintBg
        DockDayStatus.PAST -> DockColors.AmberBg
        DockDayStatus.REMITTED -> DockColors.SkyBg
    }

fun DockSessionStatus.dot(): Color =
    when (this) {
        DockSessionStatus.PENDING -> DockColors.Sunny
        DockSessionStatus.COMPLETED -> DockColors.TrafficGreen
        DockSessionStatus.NO_SHOW -> DockColors.Coral
        DockSessionStatus.CANCELLED -> DockColors.InkSoft
    }

@Composable
fun DockWindow(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DockColors.Window)
            .border(1.dp, DockColors.CardEdge, RoundedCornerShape(20.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(DockColors.WindowBar)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(DockColors.TrafficRed)
                        .clickable(enabled = onClose != null) { onClose?.invoke() },
                )
                Box(Modifier.size(14.dp).clip(CircleShape).background(DockColors.TrafficYellow))
                Box(Modifier.size(14.dp).clip(CircleShape).background(DockColors.TrafficGreen))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                color = DockColors.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(50.dp))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = DockColors.InkSoft,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun DockSticky(
    title: String,
    body: String,
    tint: Color = DockColors.Sunny,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(DockColors.Sunny.copy(alpha = 0.35f))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(16.dp),
    ) {
        Text(title, color = DockColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(body, color = DockColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DockHeading(
    text: String,
    size: Int = 34,
) {
    Text(
        text,
        color = DockColors.Ink,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun DockSub(text: String) {
    Text(
        text,
        color = DockColors.InkSoft,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun DockNoteCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DockColors.WindowBar.copy(alpha = 0.55f))
            .border(1.dp, DockColors.CardEdge, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, color = DockColors.InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DockBigButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = DockColors.Grape,
                contentColor = DockColors.GrapeInk,
                disabledContainerColor = DockColors.CardEdge,
                disabledContentColor = DockColors.InkSoft,
            ),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun DockGhostButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, DockColors.Grape, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DockColors.Grape, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun DockPickRow(
    title: String,
    subtitle: String,
    picked: Boolean,
    onPick: () -> Unit,
    trailing: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (picked) DockColors.Grape else Color.White)
            .border(1.dp, if (picked) DockColors.Grape else DockColors.CardEdge, RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) Color.White else DockColors.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                color = if (picked) Color.White.copy(alpha = 0.85f) else DockColors.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (picked) Color.White else DockColors.WindowBar)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                trailing,
                color = if (picked) DockColors.Grape else DockColors.InkSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
fun DockStat(
    label: String,
    value: String,
    tint: Color = DockColors.Grape,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, DockColors.CardEdge, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = tint, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(label, color = DockColors.InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
