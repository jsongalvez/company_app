package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun VenetianBlinds() {
    Column(Modifier.fillMaxWidth()) {
        repeat(4) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (index % 2 == 0) NoirPalette.BlindLight else NoirPalette.BlindDark),
            )
        }
    }
}

@Composable
internal fun CaseStamp(
    text: String,
    color: Color,
) {
    Box(
        Modifier
            .border(2.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .rotate(-4f),
    ) {
        Text(text.uppercase(), style = NoirType.labelMedium, color = color)
    }
}

@Composable
internal fun NoirBadge(
    text: String,
    color: Color = NoirPalette.NeonBlue,
) {
    Box(
        Modifier
            .background(NoirPalette.FileRaised, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = NoirType.labelSmall, color = color)
    }
}

@Composable
internal fun NoirBadgeClickable(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val color = if (active) NoirPalette.LampAmber else NoirPalette.Dim
    Box(
        Modifier
            .background(if (active) NoirPalette.LampAmber.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = NoirType.labelSmall, color = color)
    }
}

@Composable
internal fun CaseFolder(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(NoirPalette.CaseFile, RoundedCornerShape(6.dp))
            .border(1.dp, NoirPalette.ChalkLine, RoundedCornerShape(6.dp))
            .padding(NoirPadMd),
        content = content,
    )
}

@Composable
internal fun FileRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) NoirPalette.LampAmber.copy(alpha = 0.10f) else Color.Transparent,
                    RoundedCornerShape(4.dp),
                )
                .border(
                    1.dp,
                    if (selected) NoirPalette.LampAmber else Color.Transparent,
                    RoundedCornerShape(4.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun FolderTab(
    title: String,
    right: String,
    action: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = NoirPadSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(NoirPalette.LampAmber, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(title.uppercase(), style = NoirType.labelLarge, color = NoirPalette.NightRain)
        }
        Spacer(Modifier.width(NoirPadSm))
        Text(right, style = NoirType.bodySmall, color = NoirPalette.Dim)
        Spacer(Modifier.weight(1f))
        action?.invoke(this)
    }
}

@Composable
internal fun DeskLampNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NoirPalette.LampAmber.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .border(1.dp, NoirPalette.LampAmber.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▸ ", style = NoirType.bodySmall, color = NoirPalette.LampAmber)
        Text(text, style = NoirType.bodySmall, color = NoirPalette.Ink)
    }
}

@Composable
internal fun RainyEmpty(
    title: String,
    hint: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("⁂ ⁂ ⁂", style = NoirType.bodyMedium, color = NoirPalette.NeonBlue)
        Text(title, style = NoirType.bodyMedium, color = NoirPalette.Dim)
        Text(hint, style = NoirType.bodySmall, color = NoirPalette.Faint)
    }
}

@Composable
internal fun StatusInk(
    status: CaseStatus,
    voided: Boolean,
) {
    when {
        voided -> CaseStamp("void", NoirPalette.SirenRed)
        status == CaseStatus.PENDING -> CaseStamp("open", NoirPalette.LampAmber)
        status == CaseStatus.COMPLETED -> CaseStamp("closed", NoirPalette.EvidenceGreen)
        status == CaseStatus.NO_SHOW -> CaseStamp("no-show", NoirPalette.NeonBlue)
        else -> CaseStamp("dropped", NoirPalette.Dim)
    }
}

@Composable
internal fun NumberCell(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(NoirPalette.FileRaised, RoundedCornerShape(4.dp))
            .border(1.dp, NoirPalette.ChalkLine, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, style = NoirType.displaySmall, color = color)
        Text(caption, style = NoirType.bodySmall, color = NoirPalette.Dim)
    }
}

@Composable
internal fun DayInk(state: DayState) {
    val color =
        when (state) {
            DayState.OPEN -> NoirPalette.EvidenceGreen
            DayState.PAST -> NoirPalette.NeonBlue
            DayState.REMITTED -> NoirPalette.LampAmber
        }
    NoirBadge(state.label, color)
}
