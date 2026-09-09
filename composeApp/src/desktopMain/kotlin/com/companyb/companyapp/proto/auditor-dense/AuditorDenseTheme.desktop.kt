package com.companyb.companyapp.proto.auditordense

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ADTheme {
    val paper = Color(0xFFF6F3EA)
    val paperDeep = Color(0xFFEFEAD9)
    val card = Color(0xFFFCFBF6)
    val zebra = Color(0xFFF1ECDD)
    val header = Color(0xFFE7E0C9)
    val ink = Color(0xFF1C1B17)
    val muted = Color(0xFF6B6656)
    val faint = Color(0xFF8A8471)
    val hairline = Color(0xFFD3CCB2)
    val credit = Color(0xFF1E6B3A)
    val debit = Color(0xFF9B1C1C)
    val amber = Color(0xFF8A5A00)
    val info = Color(0xFF1D4E89)
    val slate = Color(0xFF4A463B)
    val voidWash = Color(0xFFF3E4E2)
    val mono = FontFamily.Monospace
}

fun adPeso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

@Composable
fun ADNum(
    text: String,
    color: Color = ADTheme.ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = ADTheme.mono,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun ADCell(
    text: String,
    weight: FontWeight = FontWeight.Normal,
    color: Color = ADTheme.ink,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = if (mono) ADTheme.mono else FontFamily.Default,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun ADHead(
    text: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        color = ADTheme.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun ADChip(
    text: String,
    color: Color,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(3.dp)
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = mod
            .clip(shape)
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) Color.White else color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ADTheme.mono,
            maxLines = 1,
        )
    }
}

fun statusColor(status: ADSessionStatus): Color =
    when (status) {
        ADSessionStatus.PENDING -> ADTheme.amber
        ADSessionStatus.COMPLETED -> ADTheme.credit
        ADSessionStatus.NO_SHOW -> ADTheme.info
        ADSessionStatus.CANCELLED -> ADTheme.debit
    }

fun dayColor(state: ADDayState): Color =
    when (state) {
        ADDayState.OPEN -> ADTheme.credit
        ADDayState.PAST -> ADTheme.amber
        ADDayState.REMITTED -> ADTheme.info
    }

@Composable
fun RowScope.ADAction(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> ADTheme.faint
        danger -> ADTheme.debit
        else -> ADTheme.info
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .border(1.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun ADLedgerCard(
    title: String,
    right: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ADTheme.card)
            .border(1.dp, ADTheme.hairline, RoundedCornerShape(4.dp))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                color = ADTheme.slate,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ADTheme.mono,
                modifier = Modifier.weight(1f),
            )
            right()
        }
        Spacer(Modifier.height(8.dp))
        body()
    }
}

@Composable
fun ADNote(text: String) {
    Text(
        text = text,
        color = ADTheme.muted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ADRuleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = ADTheme.muted,
            fontSize = 11.sp,
            modifier = Modifier.width(150.dp),
        )
        Text(text = value, color = ADTheme.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ADHeaderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADTheme.header)
            .border(1.dp, ADTheme.hairline)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun ADFilterBar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ADTheme.paperDeep)
            .border(1.dp, ADTheme.hairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
