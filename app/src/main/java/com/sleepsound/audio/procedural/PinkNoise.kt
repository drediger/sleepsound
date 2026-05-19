package com.sleepsound.audio.procedural

import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import kotlin.random.Random

/**
 * Pink noise (-3 dB/octave) via Paul Kellet's 7-band parallel one-pole filter.
 * Separate L/R filter state for stereo decorrelation.
 */
class PinkNoise(
    private val random: Random = Random.Default,
    private val peak: Float = 7000f,
) : NoiseSource {
    private var b0L = 0f; private var b1L = 0f; private var b2L = 0f
    private var b3L = 0f; private var b4L = 0f; private var b5L = 0f; private var b6L = 0f
    private var b0R = 0f; private var b1R = 0f; private var b2R = 0f
    private var b3R = 0f; private var b4R = 0f; private var b5R = 0f; private var b6R = 0f

    override fun fillBuffer(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            val wL = random.nextFloat() * 2f - 1f
            val wR = random.nextFloat() * 2f - 1f

            b0L = 0.99886f * b0L + wL * 0.0555179f
            b1L = 0.99332f * b1L + wL * 0.0750759f
            b2L = 0.96900f * b2L + wL * 0.1538520f
            b3L = 0.86650f * b3L + wL * 0.3104856f
            b4L = 0.55000f * b4L + wL * 0.5329522f
            b5L = -0.7616f * b5L - wL * 0.0168980f
            val pinkL = (b0L + b1L + b2L + b3L + b4L + b5L + b6L + wL * 0.5362f) * 0.11f
            b6L = wL * 0.115926f

            b0R = 0.99886f * b0R + wR * 0.0555179f
            b1R = 0.99332f * b1R + wR * 0.0750759f
            b2R = 0.96900f * b2R + wR * 0.1538520f
            b3R = 0.86650f * b3R + wR * 0.3104856f
            b4R = 0.55000f * b4R + wR * 0.5329522f
            b5R = -0.7616f * b5R - wR * 0.0168980f
            val pinkR = (b0R + b1R + b2R + b3R + b4R + b5R + b6R + wR * 0.5362f) * 0.11f
            b6R = wR * 0.115926f

            out[i * 2] = (pinkL * peak).toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = (pinkR * peak).toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
