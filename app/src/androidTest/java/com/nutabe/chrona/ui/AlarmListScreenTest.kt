package com.nutabe.chrona.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.ui.alarms.AlarmItem
import com.nutabe.chrona.ui.alarms.AlarmListScreen
import com.nutabe.chrona.ui.alarms.AlarmListState
import com.nutabe.chrona.ui.alarms.LockReason
import com.nutabe.chrona.ui.alarms.RingsIn
import com.nutabe.chrona.ui.theme.ChronaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlarmListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(
        state: AlarmListState,
        onToggle: (Long, Boolean) -> Unit = { _, _ -> },
        onOpen: (Long) -> Unit = {},
        onDuplicate: (Long) -> Unit = {},
        onDelete: (Long) -> Unit = {},
        onSelect: (Long) -> Unit = {},
        onToggleSelected: (Long) -> Unit = {},
        onDeleteSelected: () -> Unit = {},
    ) {
        compose.setContent {
            ChronaTheme {
                AlarmListScreen(state, SnackbarHostState(), onToggle, onOpen, onAdd = {},
                    onSettings = {},
                    onDuplicate = onDuplicate,
                    onDelete = onDelete,
                    onSelect = onSelect,
                    onToggleSelected = onToggleSelected,
                    onDeleteSelected = onDeleteSelected,
                )
            }
        }
    }

    @Test
    fun aRingingAlarmIsLockedWithNoToggleAndTheSolveNote() {
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 1), LockReason.RINGING))))
        compose.onNodeWithTag("locked-note").assertIsDisplayed()
        compose.onNodeWithTag("toggle-1").assertDoesNotExist()
    }

    @Test
    fun aSnoozedAlarmIsLockedAndShowsWhenItEnds() {
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 1), LockReason.SNOOZED, snoozedUntilClock = "06:39"))))
        compose.onNodeWithTag("locked-note").assertIsDisplayed()
        compose.onNodeWithTag("toggle-1").assertDoesNotExist()
    }

    @Test
    fun aLockedAlarmCannotBeOpenedForEditing() {
        var opened = -1L
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 7), LockReason.RINGING))), onOpen = { opened = it })
        compose.onNodeWithTag("alarm-7").performClick()
        assertEquals(-1L, opened)
    }

    @Test
    fun anUnlockedAlarmHasAToggleThatReportsTheChange() {
        var toggled: Pair<Long, Boolean>? = null
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 3, enabled = true), null))), onToggle = { id, on -> toggled = id to on })
        compose.onNodeWithTag("toggle-3").assertIsDisplayed().performClick()
        assertEquals(3L to false, toggled)
    }

    @Test
    fun aLongPressOffersToDuplicateTheAlarm() {
        var duplicated = -1L
        var opened = -1L
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 5), null))), onOpen = { opened = it }, onDuplicate = { duplicated = it })
        compose.onNodeWithTag("alarm-5").performTouchInput { longClick() }
        assertEquals(-1L, opened)
        compose.onNodeWithTag("duplicate-5").assertIsDisplayed().performClick()
        assertEquals(5L, duplicated)
    }

    @Test
    fun theLongPressMenuCanSelectOrDeleteTheAlarm() {
        var selected = -1L
        var deleted = -1L
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 6), null))), onSelect = { selected = it }, onDelete = { deleted = it })
        compose.onNodeWithTag("alarm-6").performTouchInput { longClick() }
        compose.onNodeWithTag("select-6").performClick()
        assertEquals(6L, selected)
        compose.onNodeWithTag("alarm-6").performTouchInput { longClick() }
        compose.onNodeWithTag("delete-6").performClick()
        assertEquals(6L, deleted)
    }

    @Test
    fun selectionModeShowsTheCountMarksAndHidesTheToggleAndTheFab() {
        val items = listOf(AlarmItem(Alarm(id = 1), null), AlarmItem(Alarm(id = 2), null))
        show(AlarmListState(items, selection = setOf(1L)))
        compose.onNodeWithTag("selection-count").assertTextEquals(context.resources.getQuantityString(R.plurals.alarms_selected, 1, 1))
        compose.onNodeWithTag("mark-1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("mark-2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("toggle-1").assertDoesNotExist()
        compose.onNodeWithTag("add-alarm").assertDoesNotExist()
    }

    @Test
    fun tappingAnAlarmInSelectionModeTogglesItInsteadOfOpeningIt() {
        var opened = -1L
        var toggled = -1L
        show(
            AlarmListState(listOf(AlarmItem(Alarm(id = 8), null)), selection = setOf(9L)),
            onOpen = { opened = it },
            onToggleSelected = { toggled = it },
        )
        compose.onNodeWithTag("alarm-8").performClick()
        assertEquals(-1L, opened)
        assertEquals(8L, toggled)
    }

    @Test
    fun theSelectionBarDeletesTheSelectedAlarms() {
        var deleted = false
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 1), null)), selection = setOf(1L)), onDeleteSelected = { deleted = true })
        compose.onNodeWithTag("selection-delete").performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun tappingAnUnlockedAlarmOpensIt() {
        var opened = -1L
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 4), null))), onOpen = { opened = it })
        compose.onNodeWithTag("alarm-4").performClick()
        assertEquals(4L, opened)
    }

    @Test
    fun anEmptyListShowsTheEmptyStateAndNoAlarms() {
        show(AlarmListState())
        compose.onNodeWithTag("alarm-list").assertDoesNotExist()
        compose.onNodeWithTag("add-alarm").assertIsDisplayed()
    }

    @Test
    fun theHeaderShowsTheTimeToTheNextAlarm() {
        show(AlarmListState(listOf(AlarmItem(Alarm(id = 1), null)), RingsIn(0, 7, 12)))
        compose.onNodeWithTag("rings-in").assertIsDisplayed()
        // The duration is formatted from the resources, whatever the locale.
        val expected = context.getString(R.string.duration_hm, 7, 12)
        compose.onNodeWithTag("rings-in").assertTextContains(expected, substring = true)
    }
}
