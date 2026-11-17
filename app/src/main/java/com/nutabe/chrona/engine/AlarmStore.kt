package com.nutabe.chrona.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth: one JSON file in device-protected storage, written atomically. Mutators
 * are serialized by a [Mutex]. An unreadable file is moved aside and the store starts empty.
 */
class AlarmStore private constructor(
    private val file: AtomicFile,
    private val onCorrupt: (File) -> Unit,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(load())

    val state: StateFlow<StoreData> = mutableState.asStateFlow()

    suspend fun update(transform: (StoreData) -> StoreData): StoreData = mutex.withLock {
        withContext(Dispatchers.IO) {
            val updated = transform(mutableState.value)
            write(updated)
            mutableState.value = updated
            updated
        }
    }

    private fun load(): StoreData {
        if (!file.baseFile.exists()) return StoreData()
        return when (val result = StoreCodec.decode(String(file.readFully(), Charsets.UTF_8))) {
            is StoreCodec.DecodeResult.Ok -> result.data
            is StoreCodec.DecodeResult.Corrupt -> {
                Log.e(TAG, "store file unreadable, starting empty", result.cause)
                val moved = File(file.baseFile.parentFile, CORRUPT_FILE_NAME)
                file.baseFile.renameTo(moved)
                onCorrupt(moved)
                StoreData()
            }
        }
    }

    private fun write(data: StoreData) {
        val stream = file.startWrite()
        try {
            stream.write(StoreCodec.encode(data).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    companion object {
        const val TAG = "ChronaStore"
        const val FILE_NAME = "alarms.json"
        const val CORRUPT_FILE_NAME = "alarms.json.corrupt"

        @Volatile
        private var instance: AlarmStore? = null

        /** Process-wide store. Only the device-protected context is touched, so it works before unlock. */
        fun get(context: Context): AlarmStore = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext).also { instance = it }
        }

        private fun open(context: Context): AlarmStore {
            val protectedContext = context.createDeviceProtectedStorageContext()
            return AlarmStore(AtomicFile(File(protectedContext.filesDir, FILE_NAME))) {
                notifyCorrupt(protectedContext)
            }
        }

        private fun notifyCorrupt(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(STORE_CHANNEL_ID, "Alarm data", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.notify(
                STORE_NOTIFICATION_ID,
                android.app.Notification.Builder(context, STORE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Alarms were reset")
                    .setContentText("The alarm data could not be read. The old file was kept as $CORRUPT_FILE_NAME.")
                    .build(),
            )
        }

        private const val STORE_CHANNEL_ID = "store_errors"
        private const val STORE_NOTIFICATION_ID = 3
    }
}
