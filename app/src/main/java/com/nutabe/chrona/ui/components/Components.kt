package com.nutabe.chrona.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ChronaIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}

/** DESIGN.md §5 double-bezel card: an outer shell with a hairline and an inner core with a top highlight. */
@Composable
fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.secondary.copy(alpha = 0.18f), shape)
            .padding(6.dp),
    ) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(scheme.surfaceContainer)
                .drawBehind { drawLine(scheme.onSurface.copy(alpha = 0.06f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
            content = content,
        )
    }
}

/** Pill with a Second outline and an uppercase label, above section groups and on mission screens. */
@Composable
fun EyebrowTag(text: String, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.secondary
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = secondary,
        modifier = modifier
            .border(1.dp, secondary.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** The one Accent action of a screen: Accent fill, Main text, optional trailing icon in a faint circle. */
@Composable
fun PillCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
    height: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill-press",
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .height(height)
            .clip(CircleShape)
            .background(scheme.primary)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(start = 28.dp, end = if (trailingIcon != null) 12.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
        if (trailingIcon != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(scheme.onPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { ChronaIcon(trailingIcon, tint = scheme.onPrimary, size = 20.dp) }
        }
    }
}

/** Transparent pill with a Second outline: "Snooze", "Configure", secondary actions. */
@Composable
fun SecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    height: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val secondary = MaterialTheme.colorScheme.secondary
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .defaultMinSize(minHeight = height)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, secondary), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) ChronaIcon(leadingIcon, tint = secondary, size = 20.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = secondary)
    }
}

/** Outlined pill used for the mission summary and the locked-alarm note. */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    textColor: Color = MaterialTheme.colorScheme.secondary,
    borderColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    Row(
        modifier
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) ChronaIcon(icon, tint = textColor, size = 18.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

/** On: Accent track and Main thumb. Off: raised track with a Second outline and thumb. */
@Composable
fun ChronaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = scheme.primary,
            checkedThumbColor = scheme.onPrimary,
            checkedBorderColor = scheme.primary,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedThumbColor = scheme.secondary,
            uncheckedBorderColor = scheme.outline,
        ),
    )
}

@Composable
fun ChronaFab(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) { ChronaIcon(icon, tint = MaterialTheme.colorScheme.onPrimary, contentDescription = contentDescription) }
}
