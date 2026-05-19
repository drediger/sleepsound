package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * Box fan — steady brown-noise air rush + faint 120 Hz blade tone.
 */
class Fan(
    private val random: Random = Random.Default,
    private val peak: Float = 6000f,
) : NoiseSource {
    private val airBase = BrownNoise(random, peak = peak * 0.8f)
    private val airBuf = ShortArray(BUFFER_FRAMES * 2)

    private var bladePhase = 0f
    private val bladeInc = TWO_PI_F * 120f / SAMPLE_RATE

    override fun fillBuffer(out: ShortArray, frames: Int) {
        airBase.fillBuffer(airBuf, frames)
        for (i in 0 until frames) {
            val blade = (sin(bladePhase.toDouble()).toFloat() * peak * 0.1f).toInt()
            bladePhase += bladeInc
            if (bladePhase > TWO_PI_F) bladePhase -= TWO_PI_F

            val l = airBuf[i * 2].toInt() + blade
            val r = airBuf[i * 2 + 1].toInt() + blade
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
