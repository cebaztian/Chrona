package com.nutabe.chrona.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.PillCta
import com.nutabe.chrona.ui.components.SecondaryPill
import com.nutabe.chrona.ui.components.hapticClickable

const val ONBOARDING_STEPS = 3

/** What the steps show; the host reads it from the system. */
data class OnboardingStatus(
    val notifications: Boolean = false,
    val fullScreen: Boolean = false,
    val exactAlarms: Boolean = false,
    val battery: Boolean = false,
)

/** Stateless: [step] is 1-based and every action goes out as a callback. */
@Composable
fun OnboardingScreen(
    step: Int,
    status: OnboardingStatus,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
    onNotNow: () -> Unit,
    onConfigureFullScreen: () -> Unit,
    onConfigureBattery: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bar, title, body) = when (step) {
        1 -> Triple(R.string.onboarding_welcome_bar, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
        2 -> Triple(R.string.onboarding_permissions_bar, R.string.onboarding_permissions_title, R.string.onboarding_permissions_body)
        else -> Triple(R.string.onboarding_ready_bar, R.string.onboarding_ready_title, R.string.onboarding_ready_body)
    }
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step > 1) {
                        Box(Modifier.size(48.dp).clip(CircleShape).hapticClickable(onClick = onBack).testTag("onboarding-back"), contentAlignment = Alignment.Center) {
                            ChronaIcon(R.drawable.ic_caret_left, contentDescription = stringResource(R.string.back_cd))
                        }
                    } else {
                        Box(Modifier.size(48.dp))
                    }
                    Text(stringResource(bar), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).hapticClickable(onClick = onSkip).padding(horizontal = 12.dp, vertical = 12.dp).testTag("onboarding-skip"),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(ONBOARDING_STEPS) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (index < step) scheme.primary else scheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EyebrowTag(stringResource(R.string.onboarding_step, step, ONBOARDING_STEPS), Modifier.testTag("onboarding-step"))
            Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            when (step) {
                2 -> DoubleBezelCard(Modifier.fillMaxWidth()) {
                    PermissionRow(R.string.health_notifications, status.notifications, null, "onboarding-notifications")
                    PermissionRow(R.string.health_full_screen, status.fullScreen, onConfigureFullScreen, "onboarding-full-screen")
                    PermissionRow(R.string.health_exact_alarms, status.exactAlarms, null, "onboarding-exact-alarms")
                }
                3 -> DoubleBezelCard(Modifier.fillMaxWidth()) {
                    PermissionRow(R.string.health_battery, status.battery, onConfigureBattery, "onboarding-battery")
                }
            }
            Box(Modifier.weight(1f))
            PillCta(
                stringResource(
                    when (step) {
                        1 -> R.string.onboarding_continue
                        2 -> R.string.onboarding_grant
                        else -> R.string.onboarding_finish
                    },
                ),
                onPrimary,
                Modifier.fillMaxWidth().testTag("onboarding-primary"),
                trailingIcon = if (step == 3) R.drawable.ic_check else R.drawable.ic_arrow_right,
                height = 56.dp,
            )
            if (step == 2) {
                Text(
                    stringResource(R.string.onboarding_not_now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).hapticClickable(onClick = onNotNow).padding(vertical = 14.dp).testTag("onboarding-not-now"),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onConfigure: (() -> Unit)?, tag: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChronaIcon(if (granted) R.drawable.ic_check else R.drawable.ic_warning, tint = if (granted) scheme.secondary else scheme.primary, size = 22.dp)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
        if (granted || onConfigure == null) {
            Text(
                stringResource(if (granted) R.string.health_granted else R.string.health_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        } else {
            SecondaryPill(stringResource(R.string.health_configure), onConfigure, height = 40.dp)
        }
    }
}
