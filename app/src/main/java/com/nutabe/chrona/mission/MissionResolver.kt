package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission

data class MissionEnvironment(
    val hasShakeSensor: Boolean,
    val hasStepSensor: Boolean,
    val activityRecognitionGranted: Boolean,
)

data class ResolvedMission(val mission: Mission, val fellBackToMath: Boolean)

/** Math is the universal fallback for a mission that cannot run (PLAN section 5). */
object MissionResolver {
    val FALLBACK: Mission.Math = Mission.Math(Difficulty.MEDIUM, 3)

    fun resolve(mission: Mission, env: MissionEnvironment): ResolvedMission = when (mission) {
        Mission.None, is Mission.Math -> ResolvedMission(mission, false)
        is Mission.Shake ->
            if (env.hasShakeSensor) ResolvedMission(mission, false) else ResolvedMission(FALLBACK, true)
        is Mission.Steps ->
            if (env.hasStepSensor && env.activityRecognitionGranted) {
                ResolvedMission(mission, false)
            } else {
                ResolvedMission(FALLBACK, true)
            }
    }
}
