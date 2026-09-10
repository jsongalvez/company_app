package com.companyb.companyapp.proto.graveyardcalm

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun CalmSection(title: String, whisper: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("◦ ", style = GraveyardCalmType.labelMedium, color = GraveyardCalmPalette.Lamp)
        Text(title, style = GraveyardCalmType.titleMedium, color = GraveyardCalmPalette.Ink)
        if (whisper.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(whisper, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun CalmCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(GraveyardCalmPalette.Card, RoundedCornerShape(10.dp))
            .padding(CalmPadMd),
    ) {
        Column { content() }
    }
}

@Composable
internal fun CalmNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(GraveyardCalmPalette.CardSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text("✎ $text", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Dim)
    }
}

@Composable
internal fun CalmTag(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = GraveyardCalmType.labelSmall, color = color)
    }
}

@Composable
internal fun CalmStatusTag(status: CalmSessionStatus) {
    val color =
        when (status) {
            CalmSessionStatus.PENDING -> GraveyardCalmPalette.Moon
            CalmSessionStatus.COMPLETED -> GraveyardCalmPalette.Sage
            CalmSessionStatus.NO_SHOW -> GraveyardCalmPalette.Violet
            CalmSessionStatus.CANCELLED -> GraveyardCalmPalette.Ember
        }
    CalmTag(status.label, color)
}

@Composable
internal fun CalmDayTag(state: CalmDayState) {
    val color =
        when (state) {
            CalmDayState.OPEN -> GraveyardCalmPalette.Lamp
            CalmDayState.PAST -> GraveyardCalmPalette.Moon
            CalmDayState.REMITTED -> GraveyardCalmPalette.Sage
        }
    CalmTag(state.label, color)
}

@Composable
internal fun CalmPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = GraveyardCalmPalette.Lamp,
                contentColor = GraveyardCalmPalette.Night,
                disabledContainerColor = GraveyardCalmPalette.Border,
                disabledContentColor = GraveyardCalmPalette.Faint,
            ),
    ) {
        Text(label, style = GraveyardCalmType.labelLarge)
    }
}

@Composable
internal fun CalmGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, style = GraveyardCalmType.labelLarge, color = GraveyardCalmPalette.Dim)
    }
}

@Composable
internal fun CalmQuiet(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = GraveyardCalmType.labelMedium, color = GraveyardCalmPalette.Moon)
    }
}

@Composable
internal fun CalmRowButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) GraveyardCalmPalette.Lamp.copy(alpha = 0.16f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            ).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = GraveyardCalmType.labelMedium,
            color = if (selected) GraveyardCalmPalette.Lamp else GraveyardCalmPalette.Dim,
        )
    }
}

@Composable
internal fun CalmDivider() {
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = GraveyardCalmPalette.BorderFaint)
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun CalmStat(figure: String, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text(figure, style = GraveyardCalmType.displaySmall, color = GraveyardCalmPalette.Ink)
        Text(caption, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
    }
}

@Composable
internal fun CalmEmpty(hint: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text("— $hint —", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Faint)
    }
}

@Composable
internal fun CalmTwoCol(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}
