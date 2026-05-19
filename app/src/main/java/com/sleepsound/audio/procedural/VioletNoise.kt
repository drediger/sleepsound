package com.sleepsound.audio.procedural

import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import kotlin.random.Random

/**
 * Violet noise (+6 dB/octave) — first difference of white noise.
 * Sharp, hissy, accentuates high frequencies. Useful for tinnitus masking.
 */
class VioletNoise(
    private val random: Random = Random.Default,
    private val peak: Float = 4500f,
) : NoiseSource {
    private var prevL = 0f
    private var prevR = 0f

    override fun fillBuffer(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            val wL = random.nextFloat() * 2f - 1f
            val wR = random.nextFloat() * 2f - 1f
            val vL = (wL - prevL) * 0.5f
            val vR = (wR - prevR) * 0.5f
            prevL = wL
            prevR = wR
            out[i * 2] = (vL * peak).toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = (vR * peak).toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
