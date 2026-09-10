package com.companyb.companyapp.proto.monthclose

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #851 — month-close theme: archive-box close pack. Ink navy + kraft + seal red, tabular figures.

object McTheme {
    val Ink = Color(0xFF1B2437)
    val InkSoft = Color(0xFF2A3550)
    val Paper = Color(0xFFF4EFE4)
    val Kraft = Color(0xFFE4D7BE)
    val KraftDark = Color(0xFFC9B78F)
    val SealRed = Color(0xFFB3372F)
    val SealedGreen = Color(0xFF2E7D4F)
    val PendingAmber = Color(0xFF9A6B1A)
    val Line = Color(0xFFD8CCAF)
    val Muted = Color(0xFF6B7080)
    val Card = Color(0xFFFFFCF3)
    val Tab = Color(0xFF232E47)
}

@Composable
fun McSectionTitle(text: String, aside: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 15.sp, color = McTheme.Ink)
        if (aside != null) Text(aside, fontSize = 12.sp, color = McTheme.Muted)
    }
}

@Composable
fun McCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(McTheme.Card)
            .border(1.dp, McTheme.Line, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun McDayStamp(status: McDayStatus) {
    val (label, color) = when (status) {
        McDayStatus.OPEN -> "OPEN" to McTheme.SealedGreen
        McDayStatus.PAST -> "PAST" to McTheme.PendingAmber
        McDayStatus.REMITTED -> "REMITTED" to McTheme.Ink
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun McStatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun McCheckRow(label: String, detail: String, done: Boolean, onClick: (() -> Unit)? = null) {
    val mod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().then(mod).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (done) McTheme.SealedGreen else McTheme.Card)
                .border(1.dp, if (done) McTheme.SealedGreen else McTheme.KraftDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(if (done) "SEALED" else "OPEN", fontSize = 10.sp, fontWeight = FontWeight.Black,
                color = if (done) Color.White else McTheme.PendingAmber)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = McTheme.Ink)
            Text(detail, fontSize = 12.sp, color = McTheme.Muted)
        }
    }
}

@Composable
fun McCloseProgress(done: Int, total: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MONTH CLOSE PACK", fontWeight = FontWeight.Black, fontSize = 13.sp, color = McTheme.Ink)
            Text("$done / $total sealed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = McTheme.SealRed)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
            color = McTheme.SealRed,
            trackColor = McTheme.Kraft,
        )
    }
}

@Composable
fun McBanner(day: McDay, branchName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(McTheme.Ink)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Branch day · $branchName · ${day.dateLabel}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("04:00 Asia/Manila boundary · day rolls at 4am, not midnight", color = Color(0xFFB9C1D6), fontSize = 11.sp)
        }
        McDayStamp(day.status)
    }
}

@Composable
fun McNavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) McTheme.SealRed else McTheme.Tab
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal)
    }
}
