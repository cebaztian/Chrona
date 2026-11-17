package com.nutabe.chrona.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nutabe.chrona.AppContainer
import com.nutabe.chrona.ui.health.HealthKind
import com.nutabe.chrona.ui.health.HealthStatus
import com.nutabe.chrona.ui.health.rememberHealthItems
import com.nutabe.chrona.ui.health.settingsIntent
import kotlinx.coroutines.launch

/** First launch or "run onboarding again": the same checks as the health screen, skippable. */
@Composable
fun OnboardingHost(container: AppContainer, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    val items = rememberHealthItems(container, refresh)
    fun ok(kind: HealthKind) = items.firstOrNull { it.kind == kind }?.status == HealthStatus.OK
    val status = OnboardingStatus(
        notifications = ok(HealthKind.NOTIFICATIONS),
        fullScreen = ok(HealthKind.FULL_SCREEN_INTENT),
        exactAlarms = ok(HealthKind.EXACT_ALARMS),
        battery = ok(HealthKind.BATTERY),
    )
    val finish: () -> Unit = {
        scope.launch {
            container.repository.setOnboardingDone(true)
            onFinished()
        }
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        step = 3
    }
    OnboardingScreen(
        step = step,
        status = status,
        onBack = { if (step > 1) step-- },
        onSkip = finish,
        onPrimary = {
            when (step) {
                1 -> step = 2
                2 -> if (status.notifications) step = 3 else notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> finish()
            }
        },
        onNotNow = { step = 3 },
        onConfigureFullScreen = { context.startActivity(settingsIntent(context, HealthKind.FULL_SCREEN_INTENT)) },
        onConfigureBattery = { context.startActivity(settingsIntent(context, HealthKind.BATTERY)) },
    )
}
