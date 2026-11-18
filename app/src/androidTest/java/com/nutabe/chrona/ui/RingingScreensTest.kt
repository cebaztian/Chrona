package com.nutabe.chrona.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.RingInfo
import com.nutabe.chrona.mission.MissionView
import com.nutabe.chrona.ui.ringing.MissionScreen
import com.nutabe.chrona.ui.ringing.RingingScreen
import com.nutabe.chrona.ui.theme.ChronaTheme
import java.time.Instant
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RingingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun info(mission: Mission = Mission.Math(Difficulty.MEDIUM, 3), canSnooze: Boolean = true, fellBack: Boolean = false) =
        RingInfo(
            alarmId = 1,
            startedAt = Instant.EPOCH,
            label = "Gym",
            mission = mission,
            fellBackToMath = fellBack,
            canSnooze = canSnooze,
            snoozeMinutes = 5,
            snoozesLeft = 2,
            snoozeMax = 3,
        )

    private fun showRinging(info: RingInfo, onStart: () -> Unit = {}, onStop: () -> Unit = {}, onSnooze: () -> Unit = {}) {
        compose.setContent {
            ChronaTheme {
                RingingScreen(info, LocalDateTime.of(2026, 11, 17, 6, 0), onStart, onStop, onSnooze, locale = Locale.US)
            }
        }
    }

    private fun showMission(
        view: MissionView,
        onSubmit: (Int) -> Unit = {},
        onTouch: () -> Unit = {},
        onSwitch: () -> Unit = {},
    ) {
        compose.setContent { ChronaTheme { MissionScreen(view, onSubmit, onTouch, onSwitch) } }
    }

    @Test
    fun aMissionAlarmOffersTheMissionAndSnoozeButNoStop() {
        var started = false
        showRinging(info(), onStart = { started = true })
        compose.onNodeWithText("06:00").assertIsDisplayed()
        compose.onNodeWithText("Gym").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ringing_stop)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ringing_start_mission)).performClick()
        assertEquals(true, started)
        compose.onNodeWithText(context.getString(R.string.ringing_snooze_minutes, 5)).assertIsDisplayed()
    }

    @Test
    fun anAlarmWithoutAMissionOffersStop() {
        var stopped = false
        showRinging(info(Mission.None), onStop = { stopped = true })
        compose.onNodeWithText(context.getString(R.string.ringing_start_mission)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ringing_stop)).performClick()
        assertEquals(true, stopped)
    }

    @Test
    fun theSnoozeButtonDisappearsWhenNoSnoozesAreLeft() {
        showRinging(info(canSnooze = false))
        compose.onNodeWithText(context.getString(R.string.ringing_snooze_minutes, 5)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ringing_no_snoozes)).assertIsDisplayed()
    }

    @Test
    fun aFallbackToMathExplainsWhy() {
        showRinging(info(Mission.Math(), fellBack = true))
        compose.onNodeWithText(context.getString(R.string.ringing_fallback_note)).assertIsDisplayed()
    }

    @Test
    fun theNumpadBuildsAnAnswerAndSubmitsIt() {
        var submitted: Int? = null
        var touches = 0
        showMission(MissionView.MathView("12 + 30", 0, 3, 0), onSubmit = { submitted = it }, onTouch = { touches++ })
        compose.onNodeWithTag("math-problem").assertTextEquals("12 + 30 = ?")
        compose.onNodeWithTag("key-4").performClick()
        compose.onNodeWithTag("key-2").performClick()
        compose.onNodeWithTag("math-input").assertTextEquals("42")
        compose.onNodeWithTag("key-confirm").performClick()
        assertEquals(42, submitted)
        assertEquals(2, touches)
    }

    @Test
    fun backspaceRemovesTheLastDigit() {
        showMission(MissionView.MathView("1 + 1", 0, 1, 0))
        compose.onNodeWithTag("key-7").performClick()
        compose.onNodeWithTag("key-8").performClick()
        compose.onNodeWithTag("key-backspace").performClick()
        compose.onNodeWithTag("math-input").assertTextEquals("7")
    }

    @Test
    fun confirmingAnEmptyInputSubmitsNothing() {
        var submitted = false
        showMission(MissionView.MathView("1 + 1", 0, 1, 0), onSubmit = { submitted = true })
        compose.onNodeWithTag("key-confirm").performClick()
        assertEquals(false, submitted)
    }

    @Test
    fun aWrongAnswerShowsTheErrorWithWordsAndClearsTheInput() {
        var view by mutableStateOf<MissionView>(MissionView.MathView("1 + 1", 0, 2, 0))
        compose.setContent { ChronaTheme { MissionScreen(view, {}, {}, {}) } }
        compose.onNodeWithTag("key-9").performClick()
        compose.runOnIdle { view = MissionView.MathView("5 + 5", 0, 2, wrongCount = 1) }
        compose.onNodeWithText(context.getString(R.string.mission_wrong)).assertIsDisplayed()
        compose.onNodeWithTag("math-input").assertTextEquals("")
    }

    @Test
    fun theShakeScreenShowsTheCountAndALockedSwitchToMath() {
        showMission(MissionView.ShakeView(count = 18, target = 30, offerMath = false))
        compose.onNodeWithTag("mission-count").assertTextEquals("18")
        compose.onNodeWithTag("switch-to-math-locked").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mission_switch_hint_shake)).assertIsDisplayed()
    }

    @Test
    fun theSwitchToMathBecomesAvailableWhenTheSensorWentQuiet() {
        var switched = false
        showMission(MissionView.StepsView(count = 3, target = 50, offerMath = true), onSwitch = { switched = true })
        compose.onNodeWithTag("switch-to-math").performClick()
        assertEquals(true, switched)
    }

    @Test
    fun theStepsScreenTellsThePhoneMayBeLocked() {
        showMission(MissionView.StepsView(count = 23, target = 50, offerMath = false))
        compose.onNodeWithText(context.getString(R.string.mission_steps_lock_hint)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mission_switch_hint_steps)).assertIsDisplayed()
    }
}
