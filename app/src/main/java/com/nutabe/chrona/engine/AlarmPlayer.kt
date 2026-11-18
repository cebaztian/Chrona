package com.nutabe.chrona.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.nutabe.chrona.R

/** Loops the alarm sound on the alarm stream: the chosen tone first, then the bundled one. */
class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player != null

    /** Returns false when neither source plays, so the caller can fall back to vibration. */
    fun start(soundUri: String?, gain: Float): Boolean {
        val sources = buildList<(MediaPlayer) -> Unit> {
            soundUri?.let { uri -> add { it.setDataSource(context, Uri.parse(uri)) } }
            add { candidate ->
                context.resources.openRawResourceFd(R.raw.alarm_default).use {
                    candidate.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
        }
        for ((index, source) in sources.withIndex()) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                source(candidate)
                candidate.isLooping = true
                candidate.prepare()
                candidate.setVolume(gain, gain)
                candidate.start()
                player = candidate
                Log.i(TAG, "MediaPlayer started (source ${if (index == 0 && soundUri != null) "chosen" else "bundled"})")
                return true
            } catch (error: Exception) {
                Log.w(TAG, "sound source $index failed", error)
                candidate.release()
            }
        }
        Log.e(TAG, "no sound could be played, falling back to vibration only")
        return false
    }

    /** Player gain, 0..1: the ramp and the mission ducking act here, never on the alarm stream. */
    fun setGain(gain: Float) {
        player?.setVolume(gain, gain)
    }

    fun pause() {
        player?.runCatching { if (isPlaying) pause() }
    }

    fun resume() {
        player?.runCatching { if (!isPlaying) start() }
    }

    fun release() {
        player?.runCatching { stop(); release() }
        player = null
    }

    private companion object {
        const val TAG = RingingService.TAG
    }
}
