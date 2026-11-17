package com.nutabe.chrona.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** DESIGN.md §3. Every role is set explicitly so nothing falls back to Material's baseline purple. */
val ChronaColors = darkColorScheme(
    primary = Color(0xFFFF8A1F), onPrimary = Color(0xFF16292B),
    primaryContainer = Color(0xFFFF8A1F), onPrimaryContainer = Color(0xFF16292B),
    inversePrimary = Color(0xFFA84F00),
    secondary = Color(0xFF9FB3AB), onSecondary = Color(0xFF16292B),
    secondaryContainer = Color(0xFF2B3E3E), onSecondaryContainer = Color(0xFFE6EEEB),
    tertiary = Color(0xFF9FB3AB), onTertiary = Color(0xFF16292B),
    tertiaryContainer = Color(0xFF2B3E3E), onTertiaryContainer = Color(0xFFE6EEEB),
    background = Color(0xFF16292B), onBackground = Color(0xFFE6EEEB),
    surface = Color(0xFF16292B), onSurface = Color(0xFFE6EEEB),
    surfaceVariant = Color(0xFF2B3E3E), onSurfaceVariant = Color(0xFF9FB3AB),
    surfaceTint = Color(0xFF9FB3AB),
    inverseSurface = Color(0xFFE6EEEB), inverseOnSurface = Color(0xFF16292B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF16292B),
    errorContainer = Color(0xFF5C1A16), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9FB3AB), outlineVariant = Color(0xFF4D605E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF344747), surfaceDim = Color(0xFF16292B),
    surfaceContainerLowest = Color(0xFF101F20), surfaceContainerLow = Color(0xFF1E3133),
    surfaceContainer = Color(0xFF243738), surfaceContainerHigh = Color(0xFF2B3E3E),
    surfaceContainerHighest = Color(0xFF344747),
)
