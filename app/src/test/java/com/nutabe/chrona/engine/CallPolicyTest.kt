package com.nutabe.chrona.engine

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CallPolicyTest {
    @Test
    fun aRingingPhoneAndEveryKindOfCallAreBusy() {
        for (mode in listOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        )) {
            assertEquals("mode $mode", true, CallPolicy.isBusy(mode))
        }
    }

    @Test
    fun normalAndInvalidModesAreNotACall() {
        assertEquals(false, CallPolicy.isBusy(AudioManager.MODE_NORMAL))
        assertEquals(false, CallPolicy.isBusy(AudioManager.MODE_CURRENT))
        assertEquals(false, CallPolicy.isBusy(AudioManager.MODE_INVALID))
    }
}
