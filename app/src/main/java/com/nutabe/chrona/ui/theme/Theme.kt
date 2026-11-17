package com.nutabe.chrona.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** DESIGN.md §5: 28 dp outer / 22 dp inner cards, 16 dp fields, 20 dp numpad keys. */
val ChronaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Dark only, no dynamic color (ADR-020). */
@Composable
fun ChronaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChronaColors, typography = ChronaTypography, shapes = ChronaShapes, content = content)
}
