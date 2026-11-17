package com.nutabe.chrona.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmProblem
import com.nutabe.chrona.engine.AlarmValidation
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.ui.alarms.AlarmEditorScreen
import com.nutabe.chrona.ui.alarms.AlarmEditorState
import com.nutabe.chrona.ui.alarms.EditorEnvironment
import com.nutabe.chrona.ui.theme.ChronaTheme
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The editor is stateless, so these tests feed it a fake state and never touch the store or AlarmManager. */
class AlarmEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun state(draft: Alarm = Alarm(), locked: Boolean = false) = AlarmEditorState(
        draft = draft,
        isNew = false,
        problems = AlarmValidation.validate(draft),
        locked = locked,
    )

    private fun show(
        state: AlarmEditorState,
        env: EditorEnvironment = EditorEnvironment(),
        onChange: (((Alarm) -> Alarm)) -> Unit = {},
        onSave: () -> Unit = {},
        onPermission: () -> Unit = {},
    ) {
        compose.setContent {
            ChronaTheme {
                AlarmEditorScreen(
                    state = state,
                    soundLabel = context.getString(R.string.sound_default),
                    env = env,
                    onChange = onChange,
                    onBack = {},
                    onDelete = {},
                    onSave = onSave,
                    onPickRingtone = {},
                    onPickFile = {},
                    onRequestStepsPermission = onPermission,
                )
            }
        }
    }

    @Test
    fun aValidDraftCanBeSaved() {
        var saved = false
        show(state(), onSave = { saved = true })
        compose.onNodeWithTag("save").assertIsEnabled().performClick()
        assertTrue(saved)
    }

    @Test
    fun aDraftWithAProblemCannotBeSaved() {
        val tooLong = Alarm(label = "x".repeat(AlarmValidation.MAX_LABEL + 1))
        assertTrue(AlarmProblem.LABEL_TOO_LONG in AlarmValidation.validate(tooLong))
        var saved = false
        show(state(tooLong), onSave = { saved = true })
        compose.onNodeWithTag("save").assertIsNotEnabled().performClick()
        assertEquals(false, saved)
    }

    @Test
    fun aLockedAlarmCannotBeSavedAndExplainsWhy() {
        show(state(locked = true))
        compose.onNodeWithTag("editor-locked").assertIsDisplayed()
        compose.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun tappingADayTogglesItInTheDraft() {
        var draft = Alarm(repeatDays = setOf(DayOfWeek.MONDAY))
        show(state(draft), onChange = { change -> draft = change(draft) })
        compose.onNodeWithTag("day-3").performScrollTo().performClick()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), draft.repeatDays)
        compose.onNodeWithTag("day-1").performClick()
        assertEquals(setOf(DayOfWeek.WEDNESDAY), draft.repeatDays)
    }

    @Test
    fun theMissionSheetListsEveryMissionAndPicksOne() {
        var draft = Alarm()
        show(state(draft), onChange = { change -> draft = change(draft) })
        compose.onNodeWithTag("mission-row").performScrollTo().performClick()
        compose.onNodeWithTag("mission-sheet").assertIsDisplayed()
        compose.onNodeWithTag("mission-math").performClick()
        assertTrue(draft.mission is Mission.Math)
        compose.onNodeWithTag("mission-shake").performClick()
        assertTrue(draft.mission is Mission.Shake)
    }

    @Test
    fun theStepsMissionIsDisabledWithoutAStepSensor() {
        var draft = Alarm()
        show(state(draft), env = EditorEnvironment(hasStepSensor = false), onChange = { change -> draft = change(draft) })
        compose.onNodeWithTag("mission-row").performScrollTo().performClick()
        compose.onNodeWithTag("mission-steps").performClick()
        assertEquals(Mission.None, draft.mission)
    }

    @Test
    fun theStepsMissionAsksForThePermissionBeforeBeingPicked() {
        var draft = Alarm()
        var asked = false
        show(
            state(draft),
            env = EditorEnvironment(hasStepSensor = true, activityRecognitionGranted = false),
            onChange = { change -> draft = change(draft) },
            onPermission = { asked = true },
        )
        compose.onNodeWithTag("mission-row").performScrollTo().performClick()
        compose.onNodeWithTag("mission-steps").performClick()
        assertTrue(asked)
        assertEquals(Mission.None, draft.mission)
    }

    @Test
    fun aMutedAlarmStreamShowsAWarning() {
        show(state(), env = EditorEnvironment(alarmStreamMuted = true))
        compose.onNodeWithTag("muted-warning").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theVibrationRowIsShownInTheSoundGroup() {
        show(state(Alarm(vibrate = true)))
        compose.onNodeWithTag("vibration-row").performScrollTo().assertIsDisplayed()
    }
}
