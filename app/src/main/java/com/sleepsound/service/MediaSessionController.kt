package com.sleepsound.service

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Wraps a [MediaSessionCompat] so lock-screen media controls, Bluetooth media
 * keys, and Android Auto can drive the player. Maps transport actions:
 *   - PLAY  → [onPlay]   (resumes after a focus-loss pause, or restarts a mix)
 *   - PAUSE → [onPause]  (mapped to "stop" — a sleep app doesn't really need
 *                         a separate pause state)
 *   - STOP  → [onStop]
 *
 * Token must be passed to [androidx.media.app.NotificationCompat.MediaStyle]
 * so the playback notification appears as a media-style card.
 */
class MediaSessionController(
    context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val session = MediaSessionCompat(context, TAG).apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { this@MediaSessionController.onPlay() }
            override fun onPause() { this@MediaSessionController.onPause() }
            override fun onStop() { this@MediaSessionController.onStop() }
        })
        isActive = true
    }

    val token: MediaSessionCompat.Token get() = session.sessionToken

    fun setPlaying(activeCount: Int) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY,
                )
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleFor(activeCount))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "SleepSound")
                .build(),
        )
    }

    fun setStopped() {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build(),
        )
    }

    fun release() {
        session.isActive = false
        session.release()
    }

    private fun titleFor(count: Int): String = when {
        count == 1 -> "Playing 1 sound"
        count > 1 -> "Playing $count sounds"
        else -> "Idle"
    }

    companion object { private const val TAG = "SleepSound" }
}
