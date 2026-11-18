package com.nutabe.chrona.engine

import java.time.DayOfWeek
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SnoozeConfig(
    val enabled: Boolean = true,
    val minutes: Int = 5,
    val maxCount: Int = 3,
)

@Serializable
enum class Difficulty { EASY, MEDIUM, HARD }

@Serializable
enum class Sensitivity { LOW, MEDIUM, HIGH }

@Serializable
sealed interface Mission {
    @Serializable
    @SerialName("none")
    data object None : Mission

    @Serializable
    @SerialName("math")
    data class Math(val difficulty: Difficulty = Difficulty.MEDIUM, val count: Int = 3) : Mission

    @Serializable
    @SerialName("shake")
    data class Shake(val count: Int = 30, val sensitivity: Sensitivity = Sensitivity.MEDIUM) : Mission

    @Serializable
    @SerialName("steps")
    data class Steps(val count: Int = 30) : Mission
}

@Serializable
data class Alarm(
    val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val repeatDays: Set<@Serializable(with = DayOfWeekAsIsoNumber::class) DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val volumeRampSeconds: Int = 30,
    val maxVolumePercent: Int = 100,
    val snooze: SnoozeConfig = SnoozeConfig(),
    val mission: Mission = Mission.None,
    @Serializable(with = InstantAsEpochMillis::class) val nextTriggerAt: Instant? = null,
    @Serializable(with = InstantAsEpochMillis::class) val snoozedUntil: Instant? = null,
    val snoozesUsed: Int = 0,
) {
    val isOneShot: Boolean get() = repeatDays.isEmpty()
}

@Serializable
data class ActiveRing(
    val alarmId: Long = 0,
    @Serializable(with = InstantAsEpochMillis::class) val startedAt: Instant = Instant.EPOCH,
    val forcedSnoozeCycles: Int = 0,
)

@Serializable
data class AppSettings(val onboardingDone: Boolean = false)

@Serializable
data class StoreData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val alarms: List<Alarm> = emptyList(),
    val activeRing: ActiveRing? = null,
    val settings: AppSettings = AppSettings(),
    val nextAlarmId: Long = 1,
    val ringQueue: List<Long> = emptyList(),
) {
    /** True while the alarm rings or waits its turn behind the ringing one. */
    fun isRingBusy(alarmId: Long): Boolean = activeRing?.alarmId == alarmId || alarmId in ringQueue

    /**
     * Allocates an id that was never used: the counter only grows, so a deleted alarm's id is not reused
     * while its undo is still open. Files written before the counter existed fall back to the highest id.
     */
    fun allocateId(): Pair<Long, StoreData> {
        val id = maxOf(nextAlarmId, (alarms.maxOfOrNull { it.id } ?: 0L) + 1)
        return id to copy(nextAlarmId = id + 1)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
