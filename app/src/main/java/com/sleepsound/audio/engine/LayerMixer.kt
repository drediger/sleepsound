package com.sleepsound.audio.engine

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.procedural.NoiseSource

private const val LAYER_FADE_MS = 800L

/**
 * Mixes multiple [NoiseSource]s into a single stereo PCM stream with
 * per-layer linear gain ramps (~800ms fade in/out on enable/disable).
 * Inactive layers (gain near zero) are skipped so their generators don't
 * burn CPU.
 */
class LayerMixer(
    private val sources: Map<SoundId, NoiseSource>,
) : NoiseSource {

    private class LayerState {
        @Volatile var targetGain: Float = 0f
        @Volatile var currentGain: Float = 0f
        val buf: ShortArray = ShortArray(BUFFER_FRAMES * 2)
    }

    private val states: Map<SoundId, LayerState> = sources.mapValues { LayerState() }
    private val gainStep = 1f / (SAMPLE_RATE * LAYER_FADE_MS / 1000f)

    fun setActive(id: SoundId, active: Boolean) {
        states[id]?.targetGain = if (active) 1f else 0f
    }

    fun setGain(id: SoundId, gain: Float) {
        states[id]?.targetGain = gain.coerceIn(0f, 1f)
    }

    fun anyAudible(): Boolean =
        states.values.any { it.targetGain > 0f || it.currentGain > 0.001f }

    override fun fillBuffer(out: ShortArray, frames: Int) {
        for (i in 0 until frames * 2) out[i] = 0

        for ((id, state) in states) {
            if (state.targetGain <= 0.001f && state.currentGain <= 0.001f) continue

            val src = sources[id] ?: continue
            src.fillBuffer(state.buf, frames)

            var g = state.currentGain
            for (i in 0 until frames) {
                g = when {
                    g < state.targetGain -> minOf(g + gainStep, state.targetGain)
                    g > state.targetGain -> maxOf(g - gainStep, state.targetGain)
                    else -> g
                }
                val l = out[i * 2].toInt() + (state.buf[i * 2].toFloat() * g).toInt()
                val r = out[i * 2 + 1].toInt() + (state.buf[i * 2 + 1].toFloat() * g).toInt()
                out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
                out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            }
            state.currentGain = g
        }
    }
}
