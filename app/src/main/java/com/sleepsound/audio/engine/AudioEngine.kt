package com.sleepsound.audio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.PowerManager
import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.SoundCatalog
import com.sleepsound.audio.SoundId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

private const val MASTER_FADE_MS = 1500L
// Safety net: if AudioTrack.write blocks (disconnected headphones, sink
// stall), force-cancel the render loop so the stop() callback can't hang.
private const val FADE_TIMEOUT_MS = 3_000L
// 13 hours: must exceed the maximum custom timer (12 h, from
// TimerSelector.MAX_CUSTOM_MINUTES = 720) plus headroom so the wake-lock
// doesn't release before the user's configured sleep audio ends. At 13 h
// the wake-lock is still a safety net — render loop bails on its own when
// targetGain == 0 or the timer expires.
private const val WAKE_LOCK_TIMEOUT_MS = 13L * 60L * 60L * 1000L

/**
 * Owns the AudioTrack pipeline and a [LayerMixer]. Applies equal-power master
 * fade-in / fade-out on top of the mixer's per-layer fades. Sample assets are
 * decoded lazily through the [LayerMixer]'s SampleSource entries.
 */
class AudioEngine(
    context: Context,
    private val scope: CoroutineScope,
) {
    val mixer: LayerMixer = LayerMixer(SoundCatalog.createAll(context, scope))

    private val wakeLock = (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepSound::AudioEngine")
        .apply { setReferenceCounted(false) }

    private var track: AudioTrack? = null
    private var renderJob: Job? = null

    @Volatile private var targetGain = 0f
    @Volatile private var currentGain = 0f
    @Volatile private var ducked = false
    // Bumps on every start()/stop() — pending stop watchdogs use it to detect
    // they've been superseded by a new start and bow out gracefully instead
    // of cancelling the render job mid-playback.
    @Volatile private var stopSequence = 0

    val isRendering: Boolean
        get() = renderJob?.isActive == true

    fun setLayerActive(id: SoundId, active: Boolean) {
        mixer.setActive(id, active)
    }

    fun setLayerGain(id: SoundId, gain: Float) {
        mixer.setGain(id, gain)
    }

    fun setDucked(duck: Boolean) {
        ducked = duck
        targetGain = if (duck) 0.3f else 1f
    }

    fun start() {
        stopSequence++  // invalidates any in-flight stop watchdog
        if (renderJob?.isActive == true) {
            targetGain = if (ducked) 0.3f else 1f
            return
        }
        if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BUFFER_FRAMES * 4)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // SONIFICATION (not MUSIC) — ambient sleep audio, not a song.
                    // Matches AudioFocusManager so the focus and the track agree.
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * 4)
            .build()
            .apply { play() }

        currentGain = 0f
        targetGain = if (ducked) 0.3f else 1f
        renderJob = scope.launch(Dispatchers.Default) { render() }
    }

    private suspend fun render() {
        val buf = ShortArray(BUFFER_FRAMES * 2)
        val fadeRate = 1f / (SAMPLE_RATE * MASTER_FADE_MS / 1000f)
        try {
            while (currentCoroutineContext().isActive) {
                mixer.fillBuffer(buf, BUFFER_FRAMES)
                var g = currentGain
                for (i in 0 until BUFFER_FRAMES) {
                    g = when {
                        g < targetGain -> minOf(g + fadeRate, targetGain)
                        g > targetGain -> maxOf(g - fadeRate, targetGain)
                        else -> g
                    }
                    val curve = sin(g.toDouble() * PI / 2.0).toFloat()
                    buf[i * 2] = (buf[i * 2].toFloat() * curve)
                        .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
                    buf[i * 2 + 1] = (buf[i * 2 + 1].toFloat() * curve)
                        .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
                }
                currentGain = g
                track?.write(buf, 0, buf.size)
                if (targetGain == 0f && currentGain <= 0.001f) break
            }
        } finally {
            track?.runCatching { stop() }
            track?.runCatching { release() }
            track = null
            currentGain = 0f
            if (wakeLock.isHeld) runCatching { wakeLock.release() }
        }
    }

    fun stop(onComplete: () -> Unit = {}) {
        val job = renderJob
        if (job?.isActive != true) {
            onComplete()
            return
        }
        val mySeq = ++stopSequence
        targetGain = 0f
        scope.launch {
            val finished = withTimeoutOrNull(FADE_TIMEOUT_MS) { job.join() } != null
            if (mySeq != stopSequence) return@launch  // start() superseded us
            if (!finished) {
                job.cancel()
                runCatching { job.join() }
            }
            onComplete()
        }
    }

    fun release() {
        targetGain = 0f
        renderJob?.cancel()
        track?.runCatching { stop() }
        track?.runCatching { release() }
        track = null
        if (wakeLock.isHeld) runCatching { wakeLock.release() }
    }
}
