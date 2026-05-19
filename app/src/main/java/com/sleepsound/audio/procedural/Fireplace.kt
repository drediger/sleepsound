package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import kotlin.random.Random

/**
 * Fireplace — quiet brown-noise base (the fire rush) + random short impulse
 * crackles with squared exponential decay (30–130 ms, every 100–1500 ms).
 */
class Fireplace(
    private val random: Random = Random.Default,
    private val peak: Float = 6500f,
) : NoiseSource {
    private val fireBase = BrownNoise(random, peak = peak * 0.4f)
    private val fireBuf = ShortArray(BUFFER_FRAMES * 2)

    private var crackleActive = false
    private var cracklePhase = 0f
    private var crackleDuration = 0f
    private var crackleAmp = 0f
    private var samplesUntilNext = nextDelay()

    private fun nextDelay(): Int =
        ((0.1f + random.nextFloat() * 1.4f) * SAMPLE_RATE).toInt()

    override fun fillBuffer(out: ShortArray, frames: Int) {
        fireBase.fillBuffer(fireBuf, frames)
        for (i in 0 until frames) {
            val fireL = fireBuf[i * 2].toInt()
            val fireR = fireBuf[i * 2 + 1].toInt()

            var crackleSample = 0f
            if (crackleActive) {
                val t = cracklePhase / crackleDuration
                if (t >= 1f) {
                    crackleActive = false
                    samplesUntilNext = nextDelay()
                } else {
                    val decay = (1f - t) * (1f - t)
                    val noiseVal = random.nextFloat() * 2f - 1f
                    crackleSample = noiseVal * decay * crackleAmp * peak
                    cracklePhase += 1f / SAMPLE_RATE
                }
            } else {
                samplesUntilNext--
                if (samplesUntilNext <= 0) {
                    crackleActive = true
                    cracklePhase = 0f
                    crackleDuration = 0.03f + random.nextFloat() * 0.1f
                    crackleAmp = 0.3f + random.nextFloat() * 0.5f
                }
            }

            val l = (fireL + crackleSample.toInt())
            val r = (fireR + crackleSample.toInt())
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
