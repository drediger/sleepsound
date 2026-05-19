package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ocean — pink-noise base modulated by a slow ~10-second LFO that simulates
 * waves rolling in and receding. Amplitude ranges 0.35–1.0.
 */
class Ocean(
    private val random: Random = Random.Default,
    private val peak: Float = 7000f,
) : NoiseSource {
    private val base = PinkNoise(random, peak)
    private val baseBuf = ShortArray(BUFFER_FRAMES * 2)

    private var wavePhase = 0f
    private val waveInc = TWO_PI_F * 0.1f / SAMPLE_RATE // 10s period

    override fun fillBuffer(out: ShortArray, frames: Int) {
        base.fillBuffer(baseBuf, frames)
        for (i in 0 until frames) {
            val s = sin(wavePhase.toDouble()).toFloat() * 0.5f + 0.5f // 0..1
            val wave = 0.35f + 0.65f * s
            wavePhase += waveInc
            if (wavePhase > TWO_PI_F) wavePhase -= TWO_PI_F

            val l = (baseBuf[i * 2].toFloat() * wave).toInt()
            val r = (baseBuf[i * 2 + 1].toFloat() * wave).toInt()
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
