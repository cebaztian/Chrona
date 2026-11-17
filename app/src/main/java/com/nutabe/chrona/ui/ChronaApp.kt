package com.nutabe.chrona.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nutabe.chrona.AppContainer
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.alarms.AlarmEditorHost
import com.nutabe.chrona.ui.alarms.AlarmListScreen
import com.nutabe.chrona.ui.alarms.AlarmListViewModel
import com.nutabe.chrona.ui.health.HealthHost
import com.nutabe.chrona.ui.onboarding.OnboardingHost
import com.nutabe.chrona.ui.settings.SettingsHost
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class EditorRoute(val id: Long = 0)

@Serializable
data object SettingsRoute

@Serializable
data object HealthRoute

/** [first] is true on the very first launch, where finishing replaces onboarding with the list. */
@Serializable
data class OnboardingRoute(val first: Boolean = false)

private fun startRoute(container: AppContainer): Any =
    if (container.repository.data.value.settings.onboardingDone) ListRoute else OnboardingRoute(first = true)

@Composable
fun ChronaApp(container: AppContainer) {
    val navController = rememberNavController()
    val start = remember { startRoute(container) }
    NavHost(navController, startDestination = start) {
        composable<ListRoute> {
            ListHost(
                container,
                onOpen = { navController.navigate(EditorRoute(it)) },
                onAdd = { navController.navigate(EditorRoute()) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            AlarmEditorHost(container, route.id) { navController.popBackStack() }
        }
        composable<SettingsRoute> {
            SettingsHost(
                container,
                onBack = { navController.popBackStack() },
                onHealth = { navController.navigate(HealthRoute) },
                onOnboarding = { navController.navigate(OnboardingRoute()) },
            )
        }
        composable<HealthRoute> { HealthHost(container) { navController.popBackStack() } }
        composable<OnboardingRoute> { entry ->
            val route = entry.toRoute<OnboardingRoute>()
            OnboardingHost(container) {
                if (route.first) {
                    navController.navigate(ListRoute) { popUpTo(route) { inclusive = true } }
                } else {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun ListHost(container: AppContainer, onOpen: (Long) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val viewModel = viewModel { AlarmListViewModel(container.repository) }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.alarm_deleted)
    val undoLabel = stringResource(R.string.undo)
    val deleted by container.deletedAlarm.collectAsState()
    LaunchedEffect(deleted) {
        val alarm = deleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) container.repository.restore(alarm)
        container.deletedAlarm.compareAndSet(alarm, null)
    }
    AlarmListScreen(state, snackbarHostState, viewModel::setEnabled, onOpen, onAdd, onSettings)
}
