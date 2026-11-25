package com.cyron.manager.presentation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NightBackground = Color(0xFF070A12)
private val NightSurface = Color(0xFF0D1119)
private val NightSurfaceVariant = Color(0xFF141927)
private val ElectricBlue = Color(0xFF8395FF)
private val MidnightOnSurface = Color(0xFFF0F1F8)

private val NightColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF232B48),
    onPrimaryContainer = Color(0xFFE0E4FF),
    secondary = Color(0xFF79D4F6),
    onSecondary = Color(0xFF05222D),
    secondaryContainer = Color(0xFF17303D),
    onSecondaryContainer = Color(0xFFCDEEFF),
    tertiary = Color(0xFF78D7AA),
    onTertiary = Color(0xFF052418),
    tertiaryContainer = Color(0xFF173226),
    onTertiaryContainer = Color(0xFFB8F0D0),
    background = NightBackground,
    onBackground = MidnightOnSurface,
    surface = NightSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = Color(0xFF9BA2B6),
    surfaceTint = ElectricBlue,
    surfaceContainerLowest = Color(0xFF080B13),
    surfaceContainerLow = Color(0xFF0B0F18),
    surfaceContainer = Color(0xFF101520),
    surfaceContainerHigh = Color(0xFF141A26),
    surfaceContainerHighest = Color(0xFF191F2D),
    outline = Color(0xFF2A3247),
    outlineVariant = Color(0xFF1B2130),
    error = Color(0xFFFF8190),
    onError = Color(0xFF2B0A10),
    errorContainer = Color(0xFF3A1620),
    onErrorContainer = Color(0xFFFFD7DC),
    inverseSurface = Color(0xFFE7E9F5),
    inverseOnSurface = Color(0xFF161B26),
    inversePrimary = Color(0xFF4459C7),
    scrim = Color(0xFF000000)
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF4459C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0B1B4D),
    secondary = Color(0xFF146C8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EEF8),
    onSecondaryContainer = Color(0xFF04303F),
    tertiary = Color(0xFF1C6B4A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F2E2),
    onTertiaryContainer = Color(0xFF03291A),
    background = Color(0xFFF1F4FB),
    onBackground = Color(0xFF1C2233),
    surface = Color.White,
    onSurface = Color(0xFF1C2233),
    surfaceVariant = Color(0xFFEDF0F8),
    onSurfaceVariant = Color(0xFF535B72),
    surfaceTint = Color(0xFF4459C7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FD),
    surfaceContainer = Color(0xFFF2F4FA),
    surfaceContainerHigh = Color(0xFFEBEEF6),
    surfaceContainerHighest = Color(0xFFE4E8F2),
    outline = Color(0xFFCBD1E2),
    outlineVariant = Color(0xFFDFE4F0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/** One corner scale for every surface, chip, sheet and dialog in the app. */
val CyronShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun CyronTheme(darkTheme: Boolean = true, persian: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColors else DayColors,
        typography = if (persian) PersianTypography else Typography,
        shapes = CyronShapes,
        content = content
    )
}
