package com.sleepsound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Don't auto-resume if the most recent stop was longer ago than a typical
// sleep cycle. Prevents a daytime reboot (battery swap, OS update) from
// playing last night's bedtime mix at 10am.
private const val MAX_RESUME_AGE_MS = 8L * 60L * 60L * 1000L  // 8 hours

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        PlaybackController.init(context)
        if (!PlaybackController.resumeOnReboot.value) return
        if (PlaybackController.activeSounds.value.isEmpty()) return

        val lastStop = PlaybackController.lastStopAtMs
        if (lastStop == 0L) return  // never stopped cleanly; nothing to resume
        val sinceStop = System.currentTimeMillis() - lastStop
        if (sinceStop > MAX_RESUME_AGE_MS) return

        PlaybackController.startPlayback(context)
    }
}
