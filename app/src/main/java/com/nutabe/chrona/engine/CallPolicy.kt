package com.nutabe.chrona.engine

import android.media.AudioManager

/**
 * A call is detected from `AudioManager.mode` (no `READ_PHONE_STATE`): while the phone rings or is in a call
 * the alarm sound pauses and a soft tone repeats instead, then the alarm resumes.
 */
object CallPolicy {
    fun isBusy(audioMode: Int): Boolean = when (audioMode) {
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING,
        -> true
        else -> false
    }
}
