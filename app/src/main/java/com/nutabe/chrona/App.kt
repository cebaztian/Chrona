package com.nutabe.chrona

import android.app.Application
import com.nutabe.chrona.engine.AlarmStore

/**
 * Must stay safe before the first unlock: it touches only the device-protected context (the store
 * lives there), never default SharedPreferences or credential-encrypted storage.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmStore.get(this)
    }
}
