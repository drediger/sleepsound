package com.sleepsound.audio.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * Listens for [AudioManager.ACTION_AUDIO_BECOMING_NOISY] — fires when headphones
 * are unplugged or Bluetooth audio disconnects. Must register dynamically;
 * manifest-declared receivers do not fire on Android 8+.
 */
class BecomingNoisyReceiver(
    private val onNoisy: () -> Unit,
) : BroadcastReceiver() {

    private var registered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onNoisy()
        }
    }

    fun register(context: Context) {
        if (registered) return
        context.registerReceiver(
            this,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        registered = true
    }

    fun unregister(context: Context) {
        if (!registered) return
        runCatching { context.unregisterReceiver(this) }
        registered = false
    }
}
