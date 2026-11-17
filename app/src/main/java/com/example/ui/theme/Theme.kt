package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SimulatorDarkColorScheme = darkColorScheme(
    primary = SapphireBlue,
    onPrimary = TextPrimary,
    primaryContainer = IndigoGlass,
    onPrimaryContainer = TextPrimary,
    secondary = DesertTitanium,
    onSecondary = ObsidianBg,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = TitaniumSilver,
    tertiary = ProAmber,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = CrimsonRecord,
    onError = TextPrimary
)

val SimulatorShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SimulatorDarkColorScheme,
        typography = Typography,
        shapes = SimulatorShapes,
        content = content
    )
}
