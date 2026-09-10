package com.companyb.companyapp.proto.roundscart

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CartSteel = Color(0xFF1C2B2D)
val CartSteel2 = Color(0xFF27393C)
val CartRail = Color(0xFF162122)
val ChartPaper = Color(0xFFF7F2E4)
val ChartCard = Color(0xFFFFFDF4)
val ChartEdge = Color(0xFFE2D6B8)
val InkChart = Color(0xFF22302F)
val InkSoftChart = Color(0xFF7A857F)
val CartTeal = Color(0xFF0E7C7B)
val CartTealDeep = Color(0xFF0A5B5A)
val CartTealWash = Color(0xFFD9EFEA)
val CartAmber = Color(0xFF9A6B00)
val CartAmberWash = Color(0xFFFFE9A8)
val CartRed = Color(0xFFB3261E)
val CartGreen = Color(0xFF2E7D46)
val CartSlate = Color(0xFF5B6B6B)
val CartLine = Color(0xFF0E7C7B)

private val RoundsCartColors = lightColorScheme(
    primary = CartTealDeep,
    onPrimary = Color.White,
    primaryContainer = CartTealWash,
    onPrimaryContainer = InkChart,
    secondary = CartSteel2,
    onSecondary = Color.White,
    tertiary = CartTeal,
    background = ChartPaper,
    onBackground = InkChart,
    surface = ChartCard,
    onSurface = InkChart,
    surfaceVariant = CartTealWash,
    onSurfaceVariant = InkChart,
    outline = ChartEdge,
    error = CartRed,
)

private val RoundsCartType = Typography(
    displaySmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
)

private val RoundsCartShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun RoundsCartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoundsCartColors, typography = RoundsCartType, shapes = RoundsCartShapes, content = content)
}
