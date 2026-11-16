package com.radardisco.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RadarDarkColorScheme = darkColorScheme(
    primary = RadarColors.Sky400,
    onPrimary = RadarColors.BgRoot,
    secondary = RadarColors.Fuchsia400,
    onSecondary = RadarColors.BgRoot,
    tertiary = RadarColors.Emerald400,
    background = RadarColors.BgRoot,
    onBackground = RadarColors.TextPrimary,
    surface = RadarColors.BgSurface,
    onSurface = RadarColors.TextPrimary,
    surfaceVariant = RadarColors.BgSurfaceAlt,
    onSurfaceVariant = RadarColors.TextSecondary,
    outline = RadarColors.BorderSubtle,
    error = RadarColors.Rose400,
    onError = RadarColors.BgRoot
)

@Composable
fun RadarDiscoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RadarDarkColorScheme,
        content = content
    )
}
