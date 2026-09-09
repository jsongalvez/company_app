package com.companyb.companyapp.proto.zenfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ZenPaper = Color(0xFFF6F3EC)
val ZenSurface = Color(0xFFFDFBF6)
val ZenStone = Color(0xFFEAE4D4)
val ZenHairline = Color(0xFFDCD3BE)
val ZenInk = Color(0xFF2B2620)
val ZenSoftInk = Color(0xFF6E6656)
val ZenMoss = Color(0xFF5F6F52)
val ZenDeepMoss = Color(0xFF48563E)
val ZenClay = Color(0xFFA2673F)

private val ZenScheme =
    lightColorScheme(
        background = ZenPaper,
        onBackground = ZenInk,
        surface = ZenSurface,
        onSurface = ZenInk,
        surfaceVariant = ZenStone,
        onSurfaceVariant = ZenSoftInk,
        primary = ZenMoss,
        onPrimary = Color.White,
        primaryContainer = ZenStone,
        onPrimaryContainer = ZenInk,
        secondary = ZenClay,
        onSecondary = Color.White,
        outline = ZenHairline,
    )

val ZenSerif: FontFamily = FontFamily.Serif

@Composable
fun ZenFocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ZenScheme, content = content)
}

@Composable
fun ZenPage(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    topNote: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(ZenPaper),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text(text = "← return", color = ZenSoftInk, fontSize = 14.sp)
                }
            }
            Text(
                text = title,
                fontFamily = ZenSerif,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                color = ZenInk,
            )
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, fontSize = 15.sp, lineHeight = 22.sp, color = ZenSoftInk)
            }
            if (topNote != null) {
                topNote()
            }
            content()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "· · ·",
                fontSize = 14.sp,
                color = ZenHairline,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun ZenRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ZenHairline))
}

@Composable
fun ZenSection(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontFamily = ZenSerif,
            fontSize = 20.sp,
            color = ZenInk,
        )
        ZenRule()
    }
}

@Composable
fun ZenBody(text: String) {
    Text(text = text, fontSize = 15.sp, lineHeight = 23.sp, color = ZenInk)
}

@Composable
fun ZenQuiet(text: String) {
    Text(text = text, fontSize = 13.sp, lineHeight = 20.sp, color = ZenSoftInk)
}

@Composable
fun ZenRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = ZenSoftInk)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ZenInk,
        )
    }
}

@Composable
fun ZenPrimary(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ZenMoss,
                contentColor = Color.White,
                disabledContainerColor = ZenStone,
                disabledContentColor = ZenSoftInk,
            ),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
fun ZenGhost(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = ZenInk,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
fun ZenSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(ZenSurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun ZenDayBanner(
    branchName: String,
    day: ZenDay,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(ZenStone, RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$branchName · branch day ${day.name}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZenInk,
        )
        Text(
            text = "The day turns at 04:00 Asia/Manila. Before that hour it is still today.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = ZenSoftInk,
        )
    }
}

fun zenMoney(amount: Double): String {
    val whole = amount.toLong()
    val text = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$text"
}
