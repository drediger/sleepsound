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

val PureBlack = Color(0xFF000000)
val DimGrey = Color(0xFF666666)
val DimmerGrey = Color(0xFF333333)
val SurfaceDark = Color(0xFF111111)
val IconGrey = Color(0xFF888888)

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
