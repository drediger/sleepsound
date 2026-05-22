package com.sleepsound.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Moonlit night" palette (May 2026). PureBlack stays as the bg so
// AMOLED true-black is preserved; surfaces + content are cool-tinted to
// harmonize with the slate-blue / cream launcher icon. Hex names are
// historical — the values are now slate-blue, not neutral grey.
val PureBlack = Color(0xFF000000)
val DimGrey = Color(0xFFA8B0C0)       // cool moonlit grey for inactive content
val DimmerGrey = Color(0xFF2D3548)    // slate tertiary (borders, scrims)
val SurfaceDark = Color(0xFF1A2236)   // slate-blue card, ~3.6:1 on PureBlack
val IconGrey = Color(0xFFA8B0C0)      // matches DimGrey (cool)
val SoftWhite = Color(0xFFE0E4EC)     // cool moonlight primary text

private val AmoledColors = darkColorScheme(
    primary = IconGrey,
    onPrimary = PureBlack,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = IconGrey,
    secondary = DimGrey,
    background = PureBlack,
    onBackground = DimGrey,
    surface = PureBlack,
    onSurface = DimGrey,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = Color(0xFF555555),
    outline = DimmerGrey,
)

private val AmoledTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun AmoledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColors,
        typography = AmoledTypography,
        content = content,
    )
}
