package com.companyb.companyapp.proto.ribbontabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RibbonBlue = Color(0xFF2B579A)
val RibbonBlueDark = Color(0xFF1F4173)
val RibbonBlueInk = Color(0xFFFFFFFF)
val RibbonBar = Color(0xFFF3F2F1)
val RibbonBarEdge = Color(0xFFE1DFDD)
val RibbonGroupBg = Color(0xFFFFFFFF)
val RibbonInk = Color(0xFF201F1E)
val RibbonInkSoft = Color(0xFF605E5C)
val RibbonInkFaint = Color(0xFFA19F9D)
val RibbonAccent = Color(0xFFCA5010)
val RibbonGreen = Color(0xFF107C41)
val RibbonAmber = Color(0xFF986F0B)
val RibbonRed = Color(0xFFC00000)
val RibbonPage = Color(0xFFFFFFFF)
val RibbonWash = Color(0xFFF8F7F6)

private val RibbonScheme =
    lightColorScheme(
        primary = RibbonBlue,
        onPrimary = RibbonBlueInk,
        secondary = RibbonBlueDark,
        onSecondary = RibbonBlueInk,
        background = RibbonPage,
        onBackground = RibbonInk,
        surface = RibbonPage,
        onSurface = RibbonInk,
        surfaceVariant = RibbonBar,
        onSurfaceVariant = RibbonInk,
        outline = RibbonBarEdge,
        outlineVariant = RibbonBarEdge,
    )

@Composable
fun RibbonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RibbonScheme, content = content)
}

@Composable
fun RibbonRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RibbonBarEdge))
}

@Composable
fun RibbonKicker(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = RibbonInkSoft,
    )
}

@Composable
fun RibbonTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
}

@Composable
fun RibbonBody(text: String) {
    Text(text, fontSize = 13.sp, color = RibbonInk)
}

@Composable
fun RibbonNote(text: String) {
    Text(text, fontSize = 12.sp, color = RibbonInkSoft)
}

@Composable
fun RibbonSectionCard(
    title: String,
    kicker: String = "",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.background(RibbonPage).border(1.dp, RibbonBarEdge).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (kicker.isNotBlank()) RibbonKicker(kicker)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
        RibbonRule()
        content()
    }
}

@Composable
fun RibbonGroup(
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.background(RibbonGroupBg).border(1.dp, RibbonBarEdge).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Text(
            caption.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = RibbonInkFaint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
fun RibbonButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = RibbonBlue, contentColor = RibbonBlueInk),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RibbonOutline(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RibbonLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RibbonBlue)
    }
}

@Composable
fun RibbonField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RibbonBlue, cursorColor = RibbonBlue),
    )
}

@Composable
fun RibbonStamp(text: String, color: Color) {
    Box(
        modifier = Modifier.border(1.dp, color).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = color)
    }
}

@Composable
fun RibbonChipRow(
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            val on = opt == selected
            if (on) {
                RibbonButton(opt, onClick = { onPick(opt) })
            } else {
                RibbonOutline(opt, onClick = { onPick(opt) })
            }
        }
    }
}

@Composable
fun RibbonEmptyLine(space: Int = 4) {
    Spacer(modifier = Modifier.height(space.dp))
}

@Composable
fun RibbonSideGap(space: Int = 8) {
    Spacer(modifier = Modifier.width(space.dp))
}
