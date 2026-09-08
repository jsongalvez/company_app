package com.companyb.companyapp.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import company_app.composeapp.generated.resources.Inter_Bold
import company_app.composeapp.generated.resources.Inter_Regular
import company_app.composeapp.generated.resources.Inter_SemiBold
import company_app.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

private val Canvas = Color(0xFF010102)
private val Surface1 = Color(0xFF0F1011)
private val Surface2 = Color(0xFF141516)
private val Surface3 = Color(0xFF18191A)
private val Surface4 = Color(0xFF191A1B)
private val Primary = Color(0xFF5E6AD2)
private val OnPrimary = Color(0xFFFFFFFF)
private val Ink = Color(0xFFF7F8F8)
private val InkMuted = Color(0xFFD0D6E0)
val InkSubtle = Color(0xFF8A8F98)

// DESIGN.md:8 — primary-hover tier: the brighter lavender used for selected-row text/badge
// (clears AA at 14sp where Primary undershoots 4.5:1). Exposed for #398; DrawerContent
// previously hardcoded this value as a raw literal.
val PrimaryHover = Color(0xFF828FFF)
private val Hairline = Color(0xFF23252A)
private val HairlineStrong = Color(0xFF34343A)
private val HairlineTertiary = Color(0xFF3E3E44)

object CornerRadius {
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val pill = 9999.dp
}

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

private val LinearShapes =
    Shapes(
        extraSmall = RoundedCornerShape(CornerRadius.xs),
        small = RoundedCornerShape(CornerRadius.sm),
        medium = RoundedCornerShape(CornerRadius.md),
        large = RoundedCornerShape(CornerRadius.lg),
        extraLarge = RoundedCornerShape(CornerRadius.xl),
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
        outlineVariant = HairlineTertiary,
        error = Color(0xFFCF6679),
        onError = Color(0xFF000000),
        inverseSurface = Surface1,
        inverseOnSurface = Ink,
        surfaceTint = Primary,
    )

@Composable
fun LinearTheme(content: @Composable () -> Unit) {
    val fontFamily =
        FontFamily(
            Font(Res.font.Inter_Regular, FontWeight.Normal),
            Font(Res.font.Inter_SemiBold, FontWeight.SemiBold),
            Font(Res.font.Inter_Bold, FontWeight.Bold),
        )

    val typography =
        Typography(
            bodyLarge = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 16.sp),
            // DESIGN.md:54-59 — Linear card-title = 22px / 600 (SemiBold) / lineHeight 28sp; mapped onto
            // Material3's titleLarge slot (Material3 Typography has no cardTitle slot — fog: theme hardening).
            // Mapping #107 Q4 reads `CardTitle` from LinearTheme.kt at call site ↔ titleLarge now carries SemiBold.
            titleLarge =
                TextStyle(
                    fontFamily = fontFamily,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            titleMedium = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 24.sp),
            // #670 — titleSmall/labelMedium keep Inter too: branch names ("Select branch"
            // cards) and row affordances previously fell back to M3 defaults.
            titleSmall = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
            // #670 — secondary labels floor at 12sp: labelSmall was 11sp and read as fine
            // print under the contract; badges/pickers inherit the floor now.
            labelSmall = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 16.sp),
        )

    MaterialTheme(
        colorScheme = LinearDarkColors,
        typography = typography,
        shapes = LinearShapes,
    ) {
        // #576 (gap from #449): paint the dark canvas at the root — without this the
        // native window background (light) shows through bare Columns and white Ink
        // text renders on light grey. One central Surface covers all platforms/screens.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
