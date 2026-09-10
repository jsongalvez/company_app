package com.companyb.companyapp.proto.readonlyaudit

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

object AuditGlass {
    val Abyss = Color(0xFF060B14)
    val Ink = Color(0xFF0C1526)
    val Paper = Color(0xFFE9EFF7)
    val Muted = Color(0xFF96A3B9)
    val Faint = Color(0xFF5F6D85)
    val Ledger = Color(0xFF3AD08A)
    val LedgerDeep = Color(0xFF0E3B26)
    val Amber = Color(0xFFF2B544)
    val Rose = Color(0xFFF47C7C)
    val Sky = Color(0xFF7CC4F4)
    val Violet = Color(0xFFB79CFF)

    val CanvasBrush = Brush.linearGradient(
        0.0f to Color(0xFF0A1322),
        0.45f to Color(0xFF0B1A2E),
        1.0f to Color(0xFF07101D),
    )
    val LedgerBrush = Brush.linearGradient(
        0.0f to Color(0xFF123527),
        1.0f to Color(0xFF0C1F33),
    )
}

@Composable
fun AuditGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AuditGlass.Abyss,
            surface = Color(0xFF101B2E),
            surfaceVariant = Color(0xFF16263D),
            onBackground = AuditGlass.Paper,
            onSurface = AuditGlass.Paper,
            onSurfaceVariant = AuditGlass.Muted,
            primary = AuditGlass.Ledger,
            onPrimary = Color(0xFF04120B),
            secondary = AuditGlass.Sky,
            tertiary = AuditGlass.Amber,
            error = AuditGlass.Rose,
            outline = Color(0xFF2A3B55),
            outlineVariant = Color(0xFF1B2940),
        ),
        content = content,
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    alpha: Float = 0.055f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = alpha))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)), RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun AuditHeader(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, color = AuditGlass.Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = AuditGlass.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun LockReason(reason: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(AuditGlass.Amber),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "LOCKED — $reason",
            color = AuditGlass.Amber,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun LockedAction(
    label: String,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Button(
            onClick = {},
            enabled = false,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Color.White.copy(alpha = 0.07f),
                disabledContentColor = AuditGlass.Muted,
            ),
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        LockReason(reason)
    }
}

@Composable
fun AuditChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AuditGlass.Ledger else Color.White.copy(alpha = 0.07f))
            .border(
                BorderStroke(1.dp, if (selected) AuditGlass.Ledger else Color.White.copy(alpha = 0.16f)),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF04120B) else AuditGlass.Paper,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AuditStat(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = AuditGlass.Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = AuditGlass.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun StatusPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.55f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier, tint: Color = AuditGlass.Sky) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.09f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = AuditGlass.Paper, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
fun StatStrip(cells: List<Triple<String, String, String?>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        cells.forEach { (label, value, sub) -> AuditStat(label, value, sub) }
    }
}

@Composable
fun RowScope.NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AuditGlass.Ledger else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF04120B) else AuditGlass.Muted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
