package com.sleepsound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        PlaybackController.init(context)
        if (PlaybackController.resumeOnReboot.value &&
            PlaybackController.activeSounds.value.isNotEmpty()
        ) {
            PlaybackController.startPlayback(context)
        }
    }
}
