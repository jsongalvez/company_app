package com.companyb.companyapp.proto.checkinkiosk

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #782 — checkin-kiosk theme: night-lobby self check-in, dark pine canvas, cream
// ticket stubs, marigold CTA. Deliberately distinct from kiosk-touch (paper/left-rail).

object CheckinColors {
    val Night = Color(0xFF0B2420)
    val NightDeep = Color(0xFF071915)
    val Panel = Color(0xFF12332D)
    val PanelLine = Color(0xFF2A5249)
    val Cream = Color(0xFFFFF6E3)
    val CreamDim = Color(0xFFF1E4C6)
    val Ink = Color(0xFF1C2420)
    val InkSoft = Color(0xFF4E5D57)
    val Marigold = Color(0xFFFFB627)
    val MarigoldInk = Color(0xFF231600)
    val Mint = Color(0xFF57D9A3)
    val Coral = Color(0xFFFF7A6B)
    val Sky = Color(0xFF7CC4FF)
    val Amber = Color(0xFFFFC94D)
    val FaintOnNight = Color(0xFFBFD4CC)
}

fun CheckinDayStatus.band(): Color = when (this) {
    CheckinDayStatus.OPEN -> CheckinColors.Mint
    CheckinDayStatus.PAST -> CheckinColors.Amber
    CheckinDayStatus.REMITTED -> CheckinColors.Sky
}

fun CheckinSessionStatus.dot(): Color = when (this) {
    CheckinSessionStatus.PENDING -> CheckinColors.Marigold
    CheckinSessionStatus.COMPLETED -> CheckinColors.Mint
    CheckinSessionStatus.NO_SHOW -> CheckinColors.Coral
    CheckinSessionStatus.CANCELLED -> CheckinColors.FaintOnNight
}

@Composable
fun CheckinPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CheckinColors.Panel)
            .border(1.dp, CheckinColors.PanelLine, RoundedCornerShape(22.dp))
            .padding(22.dp),
    ) {
        content()
    }
}

@Composable
fun CheckinHeading(
    text: String,
    size: Int = 30,
) {
    Text(
        text,
        color = CheckinColors.Cream,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun CheckinSub(
    text: String,
    size: Int = 18,
) {
    Text(
        text,
        color = CheckinColors.FaintOnNight,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun CheckinNoteCard(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CheckinColors.NightDeep)
            .border(1.dp, CheckinColors.PanelLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, color = CheckinColors.FaintOnNight, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CheckinTicketStub(
    ticketNo: String,
    guest: String,
    care: String,
    branch: String,
    ahead: Int,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CheckinColors.Cream)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("QUEUE TICKET", color = CheckinColors.InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            ticketNo,
            color = CheckinColors.Ink,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        CheckinPerforation()
        Text(guest, color = CheckinColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(
            "$care · $branch",
            color = CheckinColors.InkSoft,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (ahead == 0) "You're next — please take a seat." else "$ahead ahead of you — please take a seat.",
            color = CheckinColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CheckinPerforation() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(36) {
            Box(Modifier.size(8.dp, 3.dp).background(CheckinColors.InkSoft.copy(alpha = 0.55f)))
        }
    }
}

@Composable
fun CheckinStepDots(
    step: Int,
    total: Int,
    labels: List<String>,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..total) {
            val active = i == step
            val done = i < step
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active || done) CheckinColors.Marigold else CheckinColors.NightDeep)
                    .border(1.dp, if (active || done) CheckinColors.Marigold else CheckinColors.PanelLine, RoundedCornerShape(14.dp))
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$i · ${labels.getOrElse(i - 1) { "" }}",
                    color = if (active || done) CheckinColors.MarigoldInk else CheckinColors.FaintOnNight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun CheckinBigButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CheckinColors.Marigold,
            contentColor = CheckinColors.MarigoldInk,
            disabledContainerColor = CheckinColors.PanelLine,
            disabledContentColor = CheckinColors.FaintOnNight,
        ),
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CheckinGhostButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, CheckinColors.Marigold),
    ) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.Black, color = CheckinColors.Marigold)
    }
}

@Composable
fun CheckinChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) CheckinColors.Marigold else CheckinColors.NightDeep)
            .border(1.dp, if (selected) CheckinColors.Marigold else CheckinColors.PanelLine, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (selected) CheckinColors.MarigoldInk else CheckinColors.Cream,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CheckinPickRow(
    title: String,
    subtitle: String,
    picked: Boolean,
    onPick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (picked) CheckinColors.Cream else CheckinColors.NightDeep)
            .border(2.dp, if (picked) CheckinColors.Marigold else CheckinColors.PanelLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp)
                .clip(CircleShape)
                .background(if (picked) CheckinColors.Marigold else Color.Transparent)
                .border(2.dp, if (picked) CheckinColors.Marigold else CheckinColors.FaintOnNight, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) CheckinColors.Ink else CheckinColors.Cream,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                color = if (picked) CheckinColors.InkSoft else CheckinColors.FaintOnNight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = if (picked) CheckinColors.Ink else CheckinColors.Marigold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
fun CheckinStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CheckinColors.NightDeep)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = CheckinColors.Cream, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text(label, color = CheckinColors.FaintOnNight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CheckinTextLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, color = CheckinColors.Marigold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
