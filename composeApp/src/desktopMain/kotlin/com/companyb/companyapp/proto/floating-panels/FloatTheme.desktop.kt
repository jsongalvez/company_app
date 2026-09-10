package com.companyb.companyapp.proto.floatingpanels

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FloatCanvas = Color(0xFF16161E)
val FloatCanvasDeep = Color(0xFF0F0F16)
val FloatCanvasLine = Color(0xFF2A2B3A)
val FloatCanvasInk = Color(0xFFEDEAF2)
val FloatCanvasSoft = Color(0xFFA7A3BC)
val FloatPanel = Color(0xFFFCFBF7)
val FloatPanelEdge = Color(0xFFE3DED2)
val FloatPanelDim = Color(0xFFF1EEE5)
val FloatInk = Color(0xFF23222B)
val FloatInkSoft = Color(0xFF6F6A5E)
val FloatInkFaint = Color(0xFFA8A294)
val FloatAccent = Color(0xFF7C5CBF)
val FloatAccentDeep = Color(0xFF5B3FA3)
val FloatGold = Color(0xFFB9861B)
val FloatGreen = Color(0xFF1E7B44)
val FloatShiftOn = Color(0xFF7BD9A3)
val FloatAmber = Color(0xFF96690A)
val FloatRed = Color(0xFFB3261E)

private val FloatScheme =
    lightColorScheme(
        primary = FloatAccent,
        onPrimary = Color.White,
        secondary = FloatAccentDeep,
        onSecondary = Color.White,
        background = FloatCanvas,
        onBackground = FloatCanvasInk,
        surface = FloatPanel,
        onSurface = FloatInk,
        surfaceVariant = FloatPanelDim,
        onSurfaceVariant = FloatInk,
        outline = FloatPanelEdge,
        outlineVariant = FloatPanelEdge,
    )

@Composable
fun FloatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FloatScheme, content = content)
}

@Composable
fun FloatKicker(text: String, onDark: Boolean = false) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = if (onDark) FloatCanvasSoft else FloatInkSoft,
    )
}

@Composable
fun FloatTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FloatInk)
}

@Composable
fun FloatBody(text: String) {
    Text(text, fontSize = 13.sp, color = FloatInk)
}

@Composable
fun FloatNote(text: String) {
    Text(text, fontSize = 12.sp, color = FloatInkSoft)
}

@Composable
fun FloatCanvasNote(text: String) {
    Text(text, fontSize = 12.sp, color = FloatCanvasSoft)
}

@Composable
fun FloatGrip() {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(5.dp)
            .background(FloatPanelEdge, RoundedCornerShape(3.dp)),
    )
}

@Composable
fun FloatPanelCard(
    title: String,
    kicker: String = "",
    focused: Boolean = false,
    onFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val edge = if (focused) FloatAccent else FloatPanelEdge
    val edgeWidth = if (focused) 2.dp else 1.dp
    val lift = if (focused) 16.dp else 8.dp
    Column(
        modifier = modifier
            .shadow(lift, shape)
            .background(FloatPanel, shape)
            .border(edgeWidth, edge, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            FloatGrip()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (kicker.isNotBlank()) FloatKicker(kicker)
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FloatInk)
            }
            if (focused) {
                FloatStamp("IN FOCUS", FloatAccent)
            } else if (onFocus != null) {
                FloatGhostButton("Zoom", onClick = onFocus)
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(FloatPanelEdge),
        )
        content()
    }
}

@Composable
fun FloatDockGroup(
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.background(FloatCanvasLine, RoundedCornerShape(12.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Text(
            caption.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = FloatCanvasSoft,
        )
    }
}

@Composable
fun FloatButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FloatAccent, contentColor = Color.White),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FloatOutline(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FloatLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FloatAccentDeep)
    }
}

@Composable
fun FloatGhostButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FloatAccentDeep)
    }
}

@Composable
fun FloatField(
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
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FloatAccent, cursorColor = FloatAccent),
    )
}

@Composable
fun FloatStamp(text: String, color: Color) {
    Box(
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = color)
    }
}

@Composable
fun FloatCanvasStamp(text: String) {
    Box(
        modifier = Modifier
            .background(FloatCanvasLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = FloatCanvasInk)
    }
}

@Composable
fun FloatGap(space: Int = 4) {
    Spacer(modifier = Modifier.height(space.dp))
}

@Composable
fun FloatSideGap(space: Int = 8) {
    Spacer(modifier = Modifier.width(space.dp))
}
