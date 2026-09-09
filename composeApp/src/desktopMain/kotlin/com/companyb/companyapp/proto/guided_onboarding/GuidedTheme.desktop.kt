package com.companyb.companyapp.proto.guided_onboarding

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #766 — guided-onboarding prototype theme: night-sky first-run wizard. Deep
// indigo canvas, parchment step cards, amber lantern accent. Deliberately
// distinct from Linear and every sibling prototype.

object GdColors {
    val Night = Color(0xFF14122B)
    val NightSoft = Color(0xFF1D1A38)
    val Card = Color(0xFFFBF6EA)
    val CardEdge = Color(0xFFE3D5B8)
    val Ink = Color(0xFF262033)
    val InkOnNight = Color(0xFFF4EEDF)
    val Faded = Color(0xFF8B7F63)
    val FadedNight = Color(0xFFA79BC4)
    val Lantern = Color(0xFFF5A524)
    val LanternDeep = Color(0xFFB96A00)
    val Leaf = Color(0xFF2E9E6B)
    val Alarm = Color(0xFFC93A3A)
    val River = Color(0xFF3E7CB1)
    val Slate = Color(0xFF6B6F76)
    val BannerOpen = Color(0xFFDFF0E2)
    val BannerPast = Color(0xFFF6E7C4)
    val BannerRemitted = Color(0xFFDCE9F5)
}

fun gdNightScheme() = darkColorScheme(
    primary = GdColors.Lantern,
    onPrimary = GdColors.Night,
    background = GdColors.Night,
    onBackground = GdColors.InkOnNight,
    surface = GdColors.NightSoft,
    onSurface = GdColors.InkOnNight,
    secondary = GdColors.River,
)

fun GdSessionStatus.lantern(): Color = when (this) {
    GdSessionStatus.PENDING -> GdColors.LanternDeep
    GdSessionStatus.COMPLETED -> GdColors.Leaf
    GdSessionStatus.NO_SHOW -> GdColors.Alarm
    GdSessionStatus.CANCELLED -> GdColors.Slate
}

@Composable
fun GdCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(GdColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, GdColors.CardEdge, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun GdCoachCard(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(GdColors.NightSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GdColors.Lantern, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(14.dp)
                .clip(CircleShape)
                .background(GdColors.Lantern),
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = GdColors.Lantern, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(body, color = GdColors.InkOnNight, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
fun GdHeadline(text: String) {
    Text(text, color = GdColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
}

@Composable
fun GdNightHeadline(text: String) {
    Text(text, color = GdColors.InkOnNight, fontSize = 22.sp, fontWeight = FontWeight.Black)
}

@Composable
fun GdSubhead(text: String) {
    Text(text.uppercase(), color = GdColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun GdNote(text: String) {
    Text(text, color = GdColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun GdNightNote(text: String) {
    Text(text, color = GdColors.FadedNight, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun GdPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = GdColors.Lantern,
            contentColor = GdColors.Night,
            disabledContainerColor = GdColors.CardEdge,
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GdGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 13.sp, color = GdColors.Ink)
    }
}

@Composable
fun GdLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = GdColors.LanternDeep, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GdNightLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = GdColors.Lantern, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GdJourneyProgress(done: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "YOUR FIRST DAY — $done OF $total LANTERNS LIT",
                color = GdColors.Lantern,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Text("$done/$total", color = GdColors.FadedNight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = GdColors.Lantern,
            trackColor = GdColors.NightSoft,
        )
    }
}

@Composable
fun GdStepRow(number: Int, label: String, state: GdStepState, onClick: () -> Unit) {
    val dot = when (state) {
        GdStepState.DONE -> GdColors.Leaf
        GdStepState.CURRENT -> GdColors.Lantern
        GdStepState.NEXT -> GdColors.FadedNight
    }
    val mark = when (state) {
        GdStepState.DONE -> "✓"
        GdStepState.CURRENT -> "$number"
        GdStepState.NEXT -> "$number"
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (state == GdStepState.CURRENT) GdColors.NightSoft else Color.Transparent)
            .border(
                1.dp,
                if (state == GdStepState.CURRENT) GdColors.Lantern else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(dot),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                mark,
                color = if (state == GdStepState.NEXT) GdColors.Night else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (state == GdStepState.NEXT) GdColors.FadedNight else GdColors.InkOnNight,
            fontSize = 13.sp,
            fontWeight = if (state == GdStepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun GdDayBanner(status: GdDayStatus, branchName: String) {
    val bg = when (status) {
        GdDayStatus.OPEN -> GdColors.BannerOpen
        GdDayStatus.PAST -> GdColors.BannerPast
        GdDayStatus.REMITTED -> GdColors.BannerRemitted
    }
    Row(
        Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, GdColors.CardEdge, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(GdColors.LanternDeep))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "$branchName — Branch Day ${status.name}",
                color = GdColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Operational boundary 04:00 Asia/Manila. OPEN stays editable until 04:00 " +
                    "next morning, then turns PAST lazily.",
                color = GdColors.Faded,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun GdStatusChip(status: GdSessionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(status.lantern()))
        Spacer(Modifier.width(6.dp))
        Text(status.name, color = status.lantern(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GdEmptyCoach(title: String, hint: String, cta: String, onCta: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(GdColors.NightSoft, RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = GdColors.Lantern, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = GdColors.FadedNight, fontSize = 12.sp)
        GdPrimary(cta, onClick = onCta)
    }
}

@Composable
fun GdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = gdNightScheme()) {
        Box(Modifier.background(GdColors.Night)) {
            content()
        }
    }
}
