package com.companyb.companyapp.proto.timeline

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #760 — timeline prototype theme: editorial paper chronicle. Cream paper, ink text,
// oxblood rail, wax-seal status dots. Deliberately distinct from Linear and siblings.

object TlColors {
    val Paper = Color(0xFFF7F2E7)
    val Card = Color(0xFFFFFDF6)
    val CardEdge = Color(0xFFE2D7BF)
    val Ink = Color(0xFF211B12)
    val Faded = Color(0xFF8A7D64)
    val Rail = Color(0xFFB4552D)
    val RailFaint = Color(0xFFE7CDB4)
    val Oxblood = Color(0xFF8E2F22)
    val Teal = Color(0xFF1F6F6B)
    val Gold = Color(0xFF9A6B0F)
    val Slate = Color(0xFF6B6F76)
    val BannerOpen = Color(0xFFDDEEDD)
    val BannerPast = Color(0xFFF3E4C2)
    val BannerRemitted = Color(0xFFDCE9F5)
}

fun tlDayScheme() = lightColorScheme(
    primary = TlColors.Oxblood,
    onPrimary = Color.White,
    background = TlColors.Paper,
    onBackground = TlColors.Ink,
    surface = TlColors.Card,
    onSurface = TlColors.Ink,
    surfaceVariant = TlColors.CardEdge,
    secondary = TlColors.Teal,
)

fun TlSessionStatus.seal(): Color = when (this) {
    TlSessionStatus.PENDING -> TlColors.Rail
    TlSessionStatus.COMPLETED -> TlColors.Teal
    TlSessionStatus.NO_SHOW -> TlColors.Oxblood
    TlSessionStatus.CANCELLED -> TlColors.Slate
}

@Composable
fun TlCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(TlColors.Card, RoundedCornerShape(10.dp))
            .border(1.dp, TlColors.CardEdge, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun TlHeadline(text: String) {
    Text(text, color = TlColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
}

@Composable
fun TlSubhead(text: String) {
    Text(text.uppercase(), color = TlColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun TlNote(text: String) {
    Text(text, color = TlColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun TlPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = TlColors.Oxblood),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun TlGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 13.sp, color = TlColors.Ink)
    }
}

@Composable
fun TlLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = TlColors.Oxblood, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TlDayBanner(status: TlDayStatus, branchName: String) {
    val bg = when (status) {
        TlDayStatus.OPEN -> TlColors.BannerOpen
        TlDayStatus.PAST -> TlColors.BannerPast
        TlDayStatus.REMITTED -> TlColors.BannerRemitted
    }
    Row(
        Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, TlColors.CardEdge, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(12.dp)
                .clip(CircleShape)
                .background(if (status == TlDayStatus.OPEN) TlColors.Teal else TlColors.Gold),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "$branchName — Branch Day ${status.name}",
                color = TlColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Operational boundary 04:00 Asia/Manila. OPEN stays editable until 04:00 next morning, then turns PAST lazily.",
                color = TlColors.Faded,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun TlRailDot(color: Color, hollow: Boolean = false) {
    Box(
        Modifier.size(16.dp)
            .clip(CircleShape)
            .then(if (hollow) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color)),
    )
}

@Composable
fun TlNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) TlColors.Ink else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp)
                .clip(CircleShape)
                .background(if (selected) TlColors.RailFaint else TlColors.Rail),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (selected) TlColors.Paper else TlColors.Ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun TlStatusSeal(status: TlSessionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TlRailDot(status.seal())
        Spacer(Modifier.width(6.dp))
        Text(status.name, color = status.seal(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TlEmptyLine(label: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = TlColors.Faded, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = TlColors.Faded, fontSize = 12.sp)
    }
}

@Composable
fun TlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = tlDayScheme()) {
        Box(Modifier.background(TlColors.Paper)) {
            content()
        }
    }
}
