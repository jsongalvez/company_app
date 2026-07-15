package com.companyb.companyapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Canvas = Color(0xFF010102)
private val Surface1 = Color(0xFF0F1011)
private val Surface2 = Color(0xFF141516)
private val Surface3 = Color(0xFF18191A)
private val Surface4 = Color(0xFF191A1B)
private val Primary = Color(0xFF5E6AD2)
private val OnPrimary = Color(0xFFFFFFFF)
private val Ink = Color(0xFFF7F8F8)
private val InkMuted = Color(0xFFD0D6E0)
private val InkSubtle = Color(0xFF8A8F98)
private val Hairline = Color(0xFF23252A)

private val LinearDarkColors =
    darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        surface = Surface1,
        onSurface = Ink,
        surfaceVariant = Surface2,
        onSurfaceVariant = InkMuted,
        background = Canvas,
        onBackground = Ink,
        secondary = Surface3,
        onSecondary = Ink,
        tertiary = Surface4,
        onTertiary = InkMuted,
        outline = Hairline,
        outlineVariant = Hairline,
        error = Color(0xFFCF6679),
        onError = Color(0xFF000000),
        inverseSurface = Surface1,
        inverseOnSurface = Ink,
        surfaceTint = Primary,
    )

@Composable
fun LinearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinearDarkColors,
        content = content,
    )
}
