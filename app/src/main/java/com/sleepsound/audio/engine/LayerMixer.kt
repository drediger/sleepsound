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
    // Accumulator wide enough to hold the sum of all layers without overflow.
    // Clamp to PCM range happens once, after every layer is summed, so a
    // partially-out-of-phase layer can still pull the sum back into range.
    // Previously, per-layer clipping was destructive across iterations and
    // produced audible distortion when 3+ layers played at unity gain.
    private val accum: IntArray = IntArray(BUFFER_FRAMES * 2)

    fun setActive(id: SoundId, active: Boolean) {
        states[id]?.targetGain = if (active) 1f else 0f
    }

    fun setGain(id: SoundId, gain: Float) {
        states[id]?.targetGain = gain.coerceIn(0f, 1f)
    }

    fun anyAudible(): Boolean =
        states.values.any { it.targetGain > 0f || it.currentGain > 0.001f }

    override fun fillBuffer(out: ShortArray, frames: Int) {
        val outLen = frames * 2
        for (i in 0 until outLen) accum[i] = 0

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
                accum[i * 2] += (state.buf[i * 2].toFloat() * g).toInt()
                accum[i * 2 + 1] += (state.buf[i * 2 + 1].toFloat() * g).toInt()
            }
            state.currentGain = g
        }

        for (i in 0 until outLen) {
            out[i] = accum[i].coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
