package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val FocusPulseDarkColorScheme = darkColorScheme(
    primary = VermilionPulseDark,
    onPrimary = Color(0xFF280D04),
    primaryContainer = VermilionSoftDark,
    onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = EucalyptusSageDark,
    onSecondary = Color(0xFF042113),
    secondaryContainer = EucalyptusSoftDark,
    onSecondaryContainer = Color(0xFFC2F0D8),
    tertiary = TwilightIndigoDark,
    onTertiary = Color(0xFF0B1C2F),
    tertiaryContainer = TwilightSoftDark,
    onTertiaryContainer = Color(0xFFD1E4FA),
    background = DeepObsidian,
    onBackground = WarmIvory,
    surface = DeepObsidian,
    onSurface = WarmIvory,
    surfaceVariant = WarmGraphite,
    onSurfaceVariant = MutedSand,
    surfaceContainerLowest = DeepObsidian,
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainer = WarmGraphite,
    surfaceContainerHigh = ElevatedGraphite,
    surfaceContainerHighest = Color(0xFF352F29),
    outline = HairlineDark,
    outlineVariant = Color(0xFF2C2722),
    error = ErrorCrimsonDark,
    onError = Color(0xFF3E0A00),
)

private val FocusPulseLightColorScheme = lightColorScheme(
    primary = VermilionPulse,
    onPrimary = Color.White,
    primaryContainer = VermilionSoftLight,
    onPrimaryContainer = Color(0xFF5A1D06),
    secondary = EucalyptusSage,
    onSecondary = Color.White,
    secondaryContainer = EucalyptusSoftLight,
    onSecondaryContainer = Color(0xFF0E3524),
    tertiary = TwilightIndigo,
    onTertiary = Color.White,
    tertiaryContainer = TwilightSoftLight,
    onTertiaryContainer = Color(0xFF172C46),
    background = WarmAlabaster,
    onBackground = DeepCharcoal,
    surface = WarmAlabaster,
    onSurface = DeepCharcoal,
    surfaceVariant = WarmLinen,
    onSurfaceVariant = WarmSlate,
    surfaceContainerLowest = WarmCardLight,
    surfaceContainerLow = Color(0xFFF7F2EA),
    surfaceContainer = WarmLinen,
    surfaceContainerHigh = WarmStone,
    surfaceContainerHighest = Color(0xFFE2D7C7),
    outline = HairlineLight,
    outlineVariant = Color(0xFFEBE3D7),
    error = ErrorCrimson,
    onError = Color.White,
)

private val FocusPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FocusPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FocusPulseDarkColorScheme else FocusPulseLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusPulseTypography,
        shapes = FocusPulseShapes,
        content = content,
    )
}
