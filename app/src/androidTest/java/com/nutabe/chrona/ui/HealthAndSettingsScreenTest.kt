package com.nutabe.chrona.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.health.DndFilter
import com.nutabe.chrona.ui.health.HealthFacts
import com.nutabe.chrona.ui.health.HealthKind
import com.nutabe.chrona.ui.health.HealthScreen
import com.nutabe.chrona.ui.health.healthReport
import com.nutabe.chrona.ui.settings.SettingsScreen
import com.nutabe.chrona.ui.theme.ChronaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HealthAndSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val healthy = HealthFacts(
        exactAlarms = true, notifications = true, fullScreenIntent = true, activityRecognition = true,
        hasStepsAlarm = false, ignoringBatteryOptimizations = true, alarmVolumePercent = 80,
        dnd = DndFilter.OFF, isSamsung = false,
    )

    private fun showHealth(facts: HealthFacts, onAction: (HealthKind) -> Unit = {}) {
        compose.setContent {
            ChronaTheme { HealthScreen(healthReport(facts), onAction, onBack = {}, onRefresh = {}) }
        }
    }

    @Test
    fun aRowThatNeedsAttentionOffersTheFixAndReportsWhichOne() {
        var fixed: HealthKind? = null
        showHealth(healthy.copy(ignoringBatteryOptimizations = false), onAction = { fixed = it })
        compose.onNodeWithTag("fix-battery").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(HealthKind.BATTERY, fixed)
    }

    @Test
    fun aHealthyDeviceOffersNoFixes() {
        showHealth(healthy)
        listOf("fix-exact_alarms", "fix-notifications", "fix-battery", "fix-dnd").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
    }

    @Test
    fun theStepsRowAppearsOnlyWhenAStepsAlarmExists() {
        showHealth(healthy)
        compose.onNodeWithTag("health-physical_activity").assertDoesNotExist()
    }

    @Test
    fun theStepsRowShowsItsStateInWordsWhenNotGranted() {
        showHealth(healthy.copy(hasStepsAlarm = true, activityRecognition = false))
        compose.onNodeWithText(context.getString(R.string.health_missing), substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSamsungGuideAppearsOnlyOnSamsung() {
        showHealth(healthy.copy(isSamsung = true))
        compose.onNodeWithTag("health-samsung").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSettingsBadgeCountsTheWarningsWithTheRightPlural() {
        compose.setContent { ChronaTheme { SettingsScreen(issueCount = 1, versionName = "0.1.0", onBack = {}, onHealth = {}, onOnboarding = {}) } }
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.health_issues, 1, 1)).assertIsDisplayed()
    }

    @Test
    fun theSettingsScreenShowsTheRealVersionAndNoBadgeWhenAllIsWell() {
        compose.setContent { ChronaTheme { SettingsScreen(issueCount = 0, versionName = "9.9.9", onBack = {}, onHealth = {}, onOnboarding = {}) } }
        compose.onNodeWithText("9.9.9").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.health_issues, 1, 1)).assertDoesNotExist()
    }

    @Test
    fun theSettingsRowsNavigate() {
        var health = false
        var onboarding = false
        compose.setContent { ChronaTheme { SettingsScreen(0, "0.1.0", onBack = {}, onHealth = { health = true }, onOnboarding = { onboarding = true }) } }
        compose.onNodeWithTag("settings-health").performClick()
        compose.onNodeWithTag("settings-onboarding").performClick()
        assertEquals(true, health)
        assertEquals(true, onboarding)
    }
}
