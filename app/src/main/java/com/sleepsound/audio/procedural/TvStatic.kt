package com.sleepsound.audio.procedural

import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * TV static — stereo white noise with a subtle 60 Hz hum on top (analog AC pickup).
 */
class TvStatic(
    private val random: Random = Random.Default,
    private val peak: Float = 5500f,
) : NoiseSource {
    private var humPhase = 0f
    private val humInc = TWO_PI_F * 60f / SAMPLE_RATE

    override fun fillBuffer(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            val wL = random.nextFloat() * 2f - 1f
            val wR = random.nextFloat() * 2f - 1f
            val hum = sin(humPhase.toDouble()).toFloat() * 0.08f
            humPhase += humInc
            if (humPhase > TWO_PI_F) humPhase -= TWO_PI_F

            out[i * 2] = ((wL + hum) * peak * 0.95f)
                .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = ((wR + hum) * peak * 0.95f)
                .toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
