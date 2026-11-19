package dk.berlinsightrun.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy900 = Color(0xFF0A1A33)
val Navy800 = Color(0xFF0E2140)
val Navy700 = Color(0xFF13294B)
val Navy600 = Color(0xFF1A3560)
val BerlinBlue = Color(0xFF2F6FDB)
val Lime = Color(0xFFB5E61D)
val White = Color(0xFFFFFFFF)
val Muted = Color(0xB8FFFFFF)
val Warn = Color(0xFFFFB547)
val StartRed = Color(0xFFFF5A5F)
val StartGreen = Color(0xFF22C55E)

private val SightrunColors = darkColorScheme(
    primary = Lime, onPrimary = Navy900,
    secondary = BerlinBlue, onSecondary = White,
    background = Navy900, onBackground = White,
    surface = Navy700, onSurface = White,
    surfaceVariant = Navy700, onSurfaceVariant = Muted,
    outline = Navy600,
)

private val SightrunType = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

@Composable
fun SightrunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SightrunColors, typography = SightrunType, content = content)
}
