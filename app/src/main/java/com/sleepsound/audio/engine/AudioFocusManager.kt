package com.sleepsound.audio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

private const val TAG = "SleepSoundFocus"

interface AudioFocusCallbacks {
    fun onFocusLostPermanent()
    fun onFocusLostTransient()
    fun onFocusLostCanDuck()
    fun onFocusGain()
}

class AudioFocusManager(
    context: Context,
    private val callbacks: AudioFocusCallbacks,
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // CONTENT_TYPE_SONIFICATION rather than CONTENT_TYPE_MUSIC so the OS does
    // not apply music-app behaviors (route to BT music sink, "Music" lockscreen
    // label, Samsung "Dolby Atmos for Music" DSP) to what is really ambient
    // sleep audio. Match in AudioEngine's AudioTrack attributes.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> callbacks.onFocusLostPermanent()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> callbacks.onFocusLostTransient()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callbacks.onFocusLostCanDuck()
            AudioManager.AUDIOFOCUS_GAIN -> callbacks.onFocusGain()
        }
    }

    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(listener)
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .build()

    fun acquire(): Boolean {
        val result = audioManager.requestAudioFocus(request)
        val name = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
            else -> "code=$result"
        }
        Log.d(TAG, "requestAudioFocus → $name")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun release() {
        audioManager.abandonAudioFocusRequest(request)
    }
}
