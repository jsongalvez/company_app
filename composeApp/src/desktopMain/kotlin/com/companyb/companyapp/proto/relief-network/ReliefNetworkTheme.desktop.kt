package com.companyb.companyapp.proto.reliefnetwork

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #772 — relief-network night-market pinboard: espresso board, kraft-paper slips,
// vermilion broadcast ink. Deliberately distinct from Linear and sibling variants.

val RnBoard = Color(0xFF171009)
val RnSurface = Color(0xFF241811)
val RnEdge = Color(0xFF4A3421)
val RnPaper = Color(0xFFF7ECD4)
val RnPaperDim = Color(0xFFE7D5B3)
val RnInk = Color(0xFF2A1D10)
val RnInkSoft = Color(0xFF6B5942)
val RnCream = Color(0xFFF5E9D3)
val RnVermilion = Color(0xFFE4572E)
val RnTeal = Color(0xFF2A9D8F)
val RnMarigold = Color(0xFFF5A524)
val RnViolet = Color(0xFF9D7BEA)
val RnMuted = Color(0xFF8A7B66)
val RnDanger = Color(0xFFD64545)

@Composable
fun RnMarketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                background = RnBoard,
                surface = RnSurface,
                surfaceVariant = RnSurface,
                primary = RnVermilion,
                secondary = RnTeal,
                tertiary = RnMarigold,
                onBackground = RnCream,
                onSurface = RnCream,
                outline = RnEdge,
            ),
        typography =
            Typography(
                headlineSmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 22.sp),
                titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                bodyMedium = TextStyle(fontSize = 13.sp),
                bodySmall = TextStyle(fontSize = 12.sp),
                labelSmall = TextStyle(fontSize = 11.sp),
            ),
        shapes = Shapes(medium = RoundedCornerShape(10.dp)),
        content = content,
    )
}

@Composable
fun RnStamp(
    text: String,
    color: Color,
    dark: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (dark) color else color.copy(alpha = 0.16f))
                .border(1.dp, color, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = if (dark) RnBoard else color,
        )
    }
}

fun rnStatusColor(status: String): Color =
    when (status) {
        "COMPLETED" -> RnTeal
        "NO_SHOW" -> RnViolet
        "CANCELLED" -> RnMuted
        else -> RnMarigold
    }

fun rnDayColor(state: String): Color =
    when (state) {
        "OPEN" -> RnTeal
        "PAST" -> RnMarigold
        else -> RnViolet
    }

@Composable
fun RnSlip(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RnPaper)
                .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun RnSectionTitle(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).background(RnEdge).padding(top = 1.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = RnMarigold,
        )
        Box(Modifier.weight(1f).background(RnEdge).padding(top = 1.dp))
    }
}

@Composable
fun RnTickerTape(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0805))
                .border(1.dp, RnEdge)
                .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "WIRE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = RnBoard,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RnVermilion)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = RnCream,
                maxLines = 1,
            )
        }
    }
}
