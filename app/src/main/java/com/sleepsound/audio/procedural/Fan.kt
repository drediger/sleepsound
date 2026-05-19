package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * Box fan — brown-noise air rush modulated by a slow ~6 Hz tremolo so the
 * whoosh "rotates" the way a real fan does, plus a faint 120 Hz blade tone.
 * Without the tremolo the procedural fan is indistinguishable from the
 * dryer's air base.
 */
class Fan(
    private val random: Random = Random.Default,
    private val peak: Float = 6000f,
) : NoiseSource {
    private val airBase = BrownNoise(random, peak = peak * 0.85f)
    private val airBuf = ShortArray(BUFFER_FRAMES * 2)

    private var bladePhase = 0f
    private val bladeInc = TWO_PI_F * 120f / SAMPLE_RATE

    // ~6 Hz rotation tremolo. Real box fans run at ~1200 RPM with 3–5
    // blades, giving 60–100 Hz blade-pass + a perceptible low-freq pulse
    // from blade-edge turbulence; this approximates the latter.
    private var tremoloPhase = 0f
    private val tremoloInc = TWO_PI_F * 6f / SAMPLE_RATE

    override fun fillBuffer(out: ShortArray, frames: Int) {
        airBase.fillBuffer(airBuf, frames)
        for (i in 0 until frames) {
            // Tremolo: amplitude swings 0.65–1.0 (so the fan never goes
            // silent; it just breathes).
            val tremolo = 0.825f + sin(tremoloPhase.toDouble()).toFloat() * 0.175f
            tremoloPhase += tremoloInc
            if (tremoloPhase > TWO_PI_F) tremoloPhase -= TWO_PI_F

            val blade = (sin(bladePhase.toDouble()).toFloat() * peak * 0.12f).toInt()
            bladePhase += bladeInc
            if (bladePhase > TWO_PI_F) bladePhase -= TWO_PI_F

            val l = ((airBuf[i * 2].toFloat() * tremolo).toInt() + blade)
            val r = ((airBuf[i * 2 + 1].toFloat() * tremolo).toInt() + blade)
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
