package com.sleepsound

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.SoundTier
import com.sleepsound.billing.EntitlementStore
import com.sleepsound.service.SleepAudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val PREVIEW_DURATION_MS = 30_000L
private const val PREVIEW_FADE_MS = 5_000L
private const val PREVIEW_TICK_MS = 100L

/**
 * Process-wide singleton state for the player. UI writes here; the service
 * collects flows and reacts. Persists selected sounds, per-layer gains, and
 * master volume via SharedPreferences. Timer state and preview expiries are
 * intentionally not persisted across launches.
 */
object PlaybackController {

    private const val PREFS_NAME = "playback_state"
    private const val KEY_ACTIVE_SOUNDS = "active_sounds"
    private const val KEY_MASTER_VOLUME = "master_volume"
    private const val KEY_LAYER_GAIN_PREFIX = "layer_gain_"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previewWatcher: Job? = null

    private val _activeSounds = MutableStateFlow<Set<SoundId>>(emptySet())
    val activeSounds: StateFlow<Set<SoundId>> = _activeSounds.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timerMinutes = MutableStateFlow<Int?>(null)
    val timerMinutes: StateFlow<Int?> = _timerMinutes.asStateFlow()

    private val _timerExpiryMs = MutableStateFlow<Long?>(null)
    val timerExpiryMs: StateFlow<Long?> = _timerExpiryMs.asStateFlow()

    private val _masterVolume = MutableStateFlow(1f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _layerGains = MutableStateFlow<Map<SoundId, Float>>(emptyMap())
    val layerGains: StateFlow<Map<SoundId, Float>> = _layerGains.asStateFlow()

    /** Wall-clock millis when each previewing sound's free trial ends. */
    private val _previewExpiry = MutableStateFlow<Map<SoundId, Long>>(emptyMap())
    val previewExpiry: StateFlow<Map<SoundId, Long>> = _previewExpiry.asStateFlow()

    /**
     * Per-sound transient gain multiplier in [0, 1]. Set to a value < 1 only
     * while a preview is fading out; the service multiplies through. Not
     * persisted across launches.
     */
    private val _previewFade = MutableStateFlow<Map<SoundId, Float>>(emptyMap())
    val previewFade: StateFlow<Map<SoundId, Float>> = _previewFade.asStateFlow()

    /** Most recently expired preview — UI can surface a Buy CTA. */
    private val _pendingPurchasePrompt = MutableStateFlow<SoundId?>(null)
    val pendingPurchasePrompt: StateFlow<SoundId?> = _pendingPurchasePrompt.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        val saved = p.getStringSet(KEY_ACTIVE_SOUNDS, emptySet())
            ?.mapNotNull { runCatching { SoundId.valueOf(it) }.getOrNull() }
            ?.toSet() ?: emptySet()
        _activeSounds.value = saved
        _masterVolume.value = p.getFloat(KEY_MASTER_VOLUME, 1f)

        val gains = mutableMapOf<SoundId, Float>()
        SoundId.entries.forEach { id ->
            gains[id] = p.getFloat(KEY_LAYER_GAIN_PREFIX + id.name, 1f)
        }
        _layerGains.value = gains

        startPreviewWatcher()
        observeEntitlements()
    }

    private fun startPreviewWatcher() {
        previewWatcher?.cancel()
        previewWatcher = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val expiries = _previewExpiry.value
                if (expiries.isNotEmpty()) {
                    val newFades = _previewFade.value.toMutableMap()
                    val toFinalize = mutableListOf<SoundId>()
                    for ((id, deadline) in expiries) {
                        val elapsedAfterDeadline = now - deadline
                        when {
                            elapsedAfterDeadline < 0 -> {
                                // Still in the free-trial window — no fade.
                                if (newFades[id] != 1f) newFades[id] = 1f
                            }
                            elapsedAfterDeadline < PREVIEW_FADE_MS -> {
                                val ratio = elapsedAfterDeadline.toFloat() / PREVIEW_FADE_MS
                                newFades[id] = (1f - ratio).coerceIn(0f, 1f)
                            }
                            else -> {
                                newFades.remove(id)
                                toFinalize += id
                            }
                        }
                    }
                    if (newFades != _previewFade.value) _previewFade.value = newFades
                    toFinalize.forEach { onPreviewFadeComplete(it) }
                }
                delay(PREVIEW_TICK_MS)
            }
        }
    }

    private fun observeEntitlements() {
        scope.launch {
            EntitlementStore.unlocked.collect { unlocked ->
                // Any sound that becomes unlocked mid-preview: clear its expiry.
                val cleared = _previewExpiry.value.filterKeys { it !in unlocked }
                if (cleared != _previewExpiry.value) _previewExpiry.value = cleared
            }
        }
    }

    /**
     * Called when a preview's fade-out has completed (5 s after the 30 s
     * free-trial deadline). Removes the sound from the active set and
     * surfaces a Buy CTA. The pre-expiry watcher tick handles the fade
     * itself via [_previewFade].
     */
    private fun onPreviewFadeComplete(id: SoundId) {
        _previewExpiry.value = _previewExpiry.value - id
        _activeSounds.value = _activeSounds.value - id
        persistActive(_activeSounds.value)
        _pendingPurchasePrompt.value = id
        if (_activeSounds.value.isEmpty() && _isPlaying.value) {
            _isPlaying.value = false
            _timerExpiryMs.value = null
        }
    }

    fun dismissPurchasePrompt() {
        _pendingPurchasePrompt.value = null
    }

    fun toggleSound(context: Context, id: SoundId) {
        val current = _activeSounds.value
        val turningOn = id !in current
        val updated = if (turningOn) current + id else current - id
        if (updated == current) return

        _activeSounds.value = updated
        persistActive(updated)

        // Premium-locked + not entitled = preview mode
        if (turningOn && id.tier == SoundTier.PREMIUM && !EntitlementStore.isUnlocked(id)) {
            _previewExpiry.value = _previewExpiry.value +
                (id to System.currentTimeMillis() + PREVIEW_DURATION_MS)
        } else if (!turningOn) {
            _previewExpiry.value = _previewExpiry.value - id
            _previewFade.value = _previewFade.value - id
        }

        when {
            updated.isEmpty() && _isPlaying.value -> stopPlayback(context)
            updated.isNotEmpty() && !_isPlaying.value -> startPlayback(context)
        }
    }

    fun setSoundActive(context: Context, id: SoundId, active: Boolean) {
        val current = _activeSounds.value
        val updated = if (active) current + id else current - id
        if (updated == current) return
        _activeSounds.value = updated
        persistActive(updated)

        if (!active) {
            _previewExpiry.value = _previewExpiry.value - id
        }

        when {
            updated.isEmpty() && _isPlaying.value -> stopPlayback(context)
            updated.isNotEmpty() && !_isPlaying.value -> startPlayback(context)
        }
    }

    fun setLayerGain(id: SoundId, gain: Float) {
        val v = gain.coerceIn(0f, 1f)
        if (_layerGains.value[id] == v) return
        _layerGains.value = _layerGains.value + (id to v)
        prefs?.edit()?.putFloat(KEY_LAYER_GAIN_PREFIX + id.name, v)?.apply()
    }

    fun clearAll(context: Context) {
        _activeSounds.value = emptySet()
        _previewExpiry.value = emptyMap()
        _previewFade.value = emptyMap()
        persistActive(emptySet())
        if (_isPlaying.value) stopPlayback(context)
    }

    fun startPlayback(context: Context) {
        if (_activeSounds.value.isEmpty()) return
        _isPlaying.value = true
        _timerMinutes.value?.let {
            _timerExpiryMs.value = System.currentTimeMillis() + it * 60_000L
        }
        val intent = Intent(context, SleepAudioService::class.java).apply {
            action = SleepAudioService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopPlayback(context: Context) {
        _isPlaying.value = false
        _timerExpiryMs.value = null
        val intent = Intent(context, SleepAudioService::class.java).apply {
            action = SleepAudioService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun setTimer(minutes: Int?) {
        // Treat <=0 as "no timer" rather than computing a now-or-past expiry.
        // The UI already clamps to >=1; this is defensive for any future
        // call path (Tasker, Wear, MediaSession custom action).
        val normalized = minutes?.takeIf { it > 0 }
        _timerMinutes.value = normalized
        _timerExpiryMs.value = if (normalized != null && _isPlaying.value) {
            System.currentTimeMillis() + normalized * 60_000L
        } else null
    }

    fun setMasterVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _masterVolume.value = v
        prefs?.edit()?.putFloat(KEY_MASTER_VOLUME, v)?.apply()
    }

    /** Service tells us it stopped on its own (timer expired, focus lost). */
    fun notifyServiceStopped() {
        _isPlaying.value = false
        _timerMinutes.value = null
        _timerExpiryMs.value = null
        _previewExpiry.value = emptyMap()
        _previewFade.value = emptyMap()
    }

    private fun persistActive(sounds: Set<SoundId>) {
        prefs?.edit()
            ?.putStringSet(KEY_ACTIVE_SOUNDS, sounds.map { it.name }.toSet())
            ?.apply()
    }
}
