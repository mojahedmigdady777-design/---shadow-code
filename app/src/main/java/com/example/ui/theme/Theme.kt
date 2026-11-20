package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ShadowColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = MidnightOps,
    primaryContainer = Color(0xFF0A2A4A),
    onPrimaryContainer = TextPrimary,
    secondary = OrgBlue,
    onSecondary = TextPrimary,
    secondaryContainer = ElevatedCard,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberAlert,
    onTertiary = MidnightOps,
    error = SpyCrimson,
    onError = TextPrimary,
    errorContainer = Color(0xFF420A16),
    onErrorContainer = Color(0xFFFFD9DF),
    background = MidnightOps,
    onBackground = TextPrimary,
    surface = PanelSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderGlow
)

private val ShadowShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ShadowCodeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShadowColorScheme,
        typography = Typography,
        shapes = ShadowShapes,
        content = content
    )
}
