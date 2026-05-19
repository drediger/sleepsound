package com.sleepsound.audio.procedural

import kotlin.random.Random

/**
 * Brown noise (-6 dB/octave) via a leaky integrator of white noise.
 * Mono signal duplicated to both stereo channels.
 *
 * leak * brown + step * white  — the leak prevents DC drift, step controls
 * how quickly the integrator follows new white samples.
 */
class BrownNoise(
    private val random: Random = Random.Default,
    private val peak: Float = 8000f,
) : NoiseSource {

    private var brown = 0f
    private val leak = 0.999f
    private val step = 0.02f

    override fun fillBuffer(out: ShortArray, frames: Int) {
        var b = brown
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            b = b * leak + white * step
            if (b > 1f) b = 1f else if (b < -1f) b = -1f
            val sample = (b * peak).toInt().toShort()
            out[i * 2] = sample
            out[i * 2 + 1] = sample
        }
        brown = b
    }
}
