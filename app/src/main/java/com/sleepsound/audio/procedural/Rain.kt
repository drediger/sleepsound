package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rain — pink-noise wash + stereo white-noise hiss + slow amplitude modulation
 * for naturalistic intensity variation.
 */
class Rain(
    private val random: Random = Random.Default,
    private val peak: Float = 7000f,
) : NoiseSource {
    private val base = PinkNoise(random, peak * 0.75f)
    private val highFreq = WhiteNoise(random, peak * 0.25f)
    private val baseBuf = ShortArray(BUFFER_FRAMES * 2)
    private val hfBuf = ShortArray(BUFFER_FRAMES * 2)

    // ~20s modulation period to simulate gusts
    private var modPhase = 0f
    private val modInc = TWO_PI_F * 0.05f / SAMPLE_RATE

    override fun fillBuffer(out: ShortArray, frames: Int) {
        base.fillBuffer(baseBuf, frames)
        highFreq.fillBuffer(hfBuf, frames)
        for (i in 0 until frames) {
            val mod = 0.85f + 0.15f * sin(modPhase.toDouble()).toFloat()
            modPhase += modInc
            if (modPhase > TWO_PI_F) modPhase -= TWO_PI_F

            val l = ((baseBuf[i * 2].toInt() + hfBuf[i * 2].toInt()).toFloat() * mod).toInt()
            val r = ((baseBuf[i * 2 + 1].toInt() + hfBuf[i * 2 + 1].toInt()).toFloat() * mod).toInt()
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
