package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.TWO_PI_F
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dryer — brown-noise air rush + 60 Hz motor hum + periodic tumble thuds
 * (filtered noise bursts every ~1.0–1.5 s).
 */
class Dryer(
    private val random: Random = Random.Default,
    private val peak: Float = 6500f,
) : NoiseSource {
    private val airBase = BrownNoise(random, peak = peak * 0.5f)
    private val airBuf = ShortArray(BUFFER_FRAMES * 2)

    private val tumbleNoise = WhiteNoise(random, peak * 0.6f)
    private val tumbleBuf = ShortArray(BUFFER_FRAMES * 2)

    private var humPhase = 0f
    private val humInc = TWO_PI_F * 60f / SAMPLE_RATE

    private var inTumble = false
    private var tumblePhase = 0f
    private var tumbleDuration = 0.08f
    private var samplesUntilNext = (1.2f * SAMPLE_RATE).toInt()
    private var lpL = 0f
    private var lpR = 0f

    override fun fillBuffer(out: ShortArray, frames: Int) {
        airBase.fillBuffer(airBuf, frames)
        tumbleNoise.fillBuffer(tumbleBuf, frames)

        for (i in 0 until frames) {
            val airL = airBuf[i * 2].toInt()
            val airR = airBuf[i * 2 + 1].toInt()

            val hum = (sin(humPhase.toDouble()).toFloat() * peak * 0.15f).toInt()
            humPhase += humInc
            if (humPhase > TWO_PI_F) humPhase -= TWO_PI_F

            var tumbleL = 0f
            var tumbleR = 0f
            if (inTumble) {
                val t = tumblePhase / tumbleDuration
                if (t >= 1f) {
                    inTumble = false
                    samplesUntilNext = ((1.0f + random.nextFloat() * 0.5f) * SAMPLE_RATE).toInt()
                } else {
                    val env = if (t < 0.3f) t / 0.3f else (1f - t) / 0.7f
                    val rawL = tumbleBuf[i * 2].toFloat() / SHORT_MAX
                    val rawR = tumbleBuf[i * 2 + 1].toFloat() / SHORT_MAX
                    // ~800Hz LP — gives the cloth-thump character
                    lpL = lpL * 0.9f + rawL * 0.1f
                    lpR = lpR * 0.9f + rawR * 0.1f
                    tumbleL = lpL * env * peak * 0.7f
                    tumbleR = lpR * env * peak * 0.7f
                    tumblePhase += 1f / SAMPLE_RATE
                }
            } else {
                samplesUntilNext--
                if (samplesUntilNext <= 0) {
                    inTumble = true
                    tumblePhase = 0f
                }
            }

            val l = (airL + hum + tumbleL.toInt())
            val r = (airR + hum + tumbleR.toInt())
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
