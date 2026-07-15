package com.companyb.companyapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

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

private val LinearFontFamily = FontFamily.SansSerif // Inter when bundled

private val LinearTypography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = LinearFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
    )

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
        typography = LinearTypography,
        content = content,
    )
}
