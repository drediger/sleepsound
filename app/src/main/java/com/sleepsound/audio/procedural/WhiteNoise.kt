package com.sleepsound.audio.procedural

import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import kotlin.random.Random

/** Uniform random samples, stereo-decorrelated (independent L/R streams). */
class WhiteNoise(
    private val random: Random = Random.Default,
    private val peak: Float = 6000f,
) : NoiseSource {
    override fun fillBuffer(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            out[i * 2] = ((random.nextFloat() * 2f - 1f) * peak)
                .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = ((random.nextFloat() * 2f - 1f) * peak)
                .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
