package com.sleepsound.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sleepsound.MainActivity
import com.sleepsound.PlaybackController
import com.sleepsound.R
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.engine.AudioEngine
import com.sleepsound.audio.engine.AudioFocusCallbacks
import com.sleepsound.audio.engine.AudioFocusManager
import com.sleepsound.audio.engine.BecomingNoisyReceiver
import com.sleepsound.audio.engine.SleepTimer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SleepAudioService : LifecycleService() {

    private lateinit var engine: AudioEngine
    private lateinit var focusManager: AudioFocusManager
    private lateinit var noisyReceiver: BecomingNoisyReceiver
    private lateinit var sleepTimer: SleepTimer
    private lateinit var mediaSession: MediaSessionController
    private var foregroundActive = false
    private var pausedForFocus = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        engine = AudioEngine(this, lifecycleScope)
        focusManager = AudioFocusManager(this, focusCallbacks)
        noisyReceiver = BecomingNoisyReceiver(::onBecomingNoisy)
        sleepTimer = SleepTimer(lifecycleScope, ::onTimerExpired)
        mediaSession = MediaSessionController(
            context = this,
            onPlay = { PlaybackController.startPlayback(this) },
            onPause = {
                PlaybackController.notifyServiceStopped()
                handleStop("mediaSession.onPause")
            },
            onStop = {
                PlaybackController.notifyServiceStopped()
                handleStop("mediaSession.onStop")
            },
        )

        startStateCollectors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> handleStop()
            else -> handleStart()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        val activeCount = PlaybackController.activeSounds.value.size
        Log.i(TAG, "handleStart: activeCount=$activeCount")
        mediaSession.setPlaying(activeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(activeCount),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(activeCount))
        }
        foregroundActive = true

        val gotFocus = focusManager.acquire()
        Log.i(TAG, "handleStart: focusAcquired=$gotFocus")
        if (!gotFocus) {
            handleStop("focusDenied")
            return
        }
        noisyReceiver.register(this)
        engine.start()
    }

    private fun handleStop(reason: String = "explicit") {
        Log.i(TAG, "handleStop: reason=$reason")
        pausedForFocus = false
        mediaSession.setStopped()
        sleepTimer.cancel()
        noisyReceiver.unregister(this)
        focusManager.release()
        foregroundActive = false
        // Drop the foreground notification immediately so the user sees stop
        // take effect. The engine continues its fade-out for ~1.5 s; the
        // service stays alive (background) until the callback fires stopSelf.
        stopForeground(STOP_FOREGROUND_REMOVE)
        engine.stop { stopSelf() }
    }

    private fun startStateCollectors() {
        // Notification + MediaSession metadata both track the active count.
        PlaybackController.activeSounds
            .onEach { active ->
                updateNotification(active.size)
                if (PlaybackController.isPlaying.value && active.isNotEmpty()) {
                    mediaSession.setPlaying(active.size)
                }
            }
            .launchIn(lifecycleScope)

        // Master volume → engine
        PlaybackController.masterVolume
            .onEach { engine.setMasterVolume(it) }
            .launchIn(lifecycleScope)

        // Active sounds × per-layer gains × preview fade → engine layer gains.
        // The preview fade is a transient multiplier (1.0 except while a
        // premium preview is fading out post-30s) so it doesn't disturb the
        // user's persisted per-sound volume.
        combine(
            PlaybackController.activeSounds,
            PlaybackController.layerGains,
            PlaybackController.previewFade,
        ) { active, gains, fades ->
            SoundId.entries.associateWith { id ->
                if (id in active) (gains[id] ?: 1f) * (fades[id] ?: 1f) else 0f
            }
        }
            .onEach { effectiveGains ->
                effectiveGains.forEach { (id, g) -> engine.setLayerGain(id, g) }
            }
            .launchIn(lifecycleScope)

        // Timer only ticks while playing
        combine(
            PlaybackController.isPlaying,
            PlaybackController.timerMinutes,
        ) { playing, minutes -> if (playing && minutes != null) minutes else null }
            .onEach { activeMinutes ->
                if (activeMinutes == null) sleepTimer.cancel()
                else sleepTimer.set(activeMinutes)
            }
            .launchIn(lifecycleScope)
    }

    private fun onTimerExpired() {
        PlaybackController.notifyServiceStopped()
        handleStop("timerExpired")
    }

    private fun onBecomingNoisy() {
        Log.i(TAG, "onBecomingNoisy received")
        PlaybackController.notifyServiceStopped()
        handleStop("becomingNoisy")
    }

    private val focusCallbacks = object : AudioFocusCallbacks {
        override fun onFocusLostPermanent() {
            Log.i(TAG, "onFocusLostPermanent")
            PlaybackController.notifyServiceStopped()
            handleStop("focusLossPermanent")
        }
        override fun onFocusLostTransient() {
            Log.i(TAG, "onFocusLostTransient")
            // Phone call, voice recorder, alarm. Pause cleanly rather than
            // ducking — playing audio under another stream is intrusive at
            // 3am and at 30% volume isn't the right experience for a call.
            pausedForFocus = true
            engine.stop()
        }
        override fun onFocusLostCanDuck() {
            Log.i(TAG, "onFocusLostCanDuck")
            engine.setDucked(true)
        }
        override fun onFocusGain() {
            Log.i(TAG, "onFocusGain pausedForFocus=$pausedForFocus")
            engine.setDucked(false)
            if (pausedForFocus) {
                pausedForFocus = false
                engine.start()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        mediaSession.release()
        noisyReceiver.unregister(this)
        focusManager.release()
        engine.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(activeCount: Int) {
        if (!foregroundActive) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(activeCount))
    }

    private fun buildNotification(activeCount: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SleepAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (activeCount) {
            0 -> getString(R.string.notification_idle)
            1 -> getString(R.string.notification_playing_one)
            else -> getString(R.string.notification_playing_n, activeCount)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_notification_stop,
                getString(R.string.notification_stop),
                stopIntent,
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.token)
                    .setShowActionsInCompactView(0),
            )
            .build()
    }

    companion object {
        const val ACTION_START = "com.sleepsound.action.START"
        const val ACTION_STOP = "com.sleepsound.action.STOP"
        private const val CHANNEL_ID = "sleep_audio"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "SleepSoundSvc"
    }
}
