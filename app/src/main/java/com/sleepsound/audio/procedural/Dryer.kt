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
    private val airBase = BrownNoise(random, peak = peak * 0.4f)
    private val airBuf = ShortArray(BUFFER_FRAMES * 2)

    private val tumbleNoise = WhiteNoise(random, peak)
    private val tumbleBuf = ShortArray(BUFFER_FRAMES * 2)

    private var humPhase = 0f
    private val humInc = TWO_PI_F * 60f / SAMPLE_RATE

    private var inTumble = false
    private var tumblePhase = 0f
    // Longer + more frequent thumps so they read clearly over the air base.
    private var tumbleDuration = 0.14f
    private var samplesUntilNext = (0.95f * SAMPLE_RATE).toInt()
    private var lpL = 0f
    private var lpR = 0f

    override fun fillBuffer(out: ShortArray, frames: Int) {
        airBase.fillBuffer(airBuf, frames)
        tumbleNoise.fillBuffer(tumbleBuf, frames)

        for (i in 0 until frames) {
            val airL = airBuf[i * 2].toInt()
            val airR = airBuf[i * 2 + 1].toInt()

            val hum = (sin(humPhase.toDouble()).toFloat() * peak * 0.18f).toInt()
            humPhase += humInc
            if (humPhase > TWO_PI_F) humPhase -= TWO_PI_F

            var tumbleL = 0f
            var tumbleR = 0f
            if (inTumble) {
                val t = tumblePhase / tumbleDuration
                if (t >= 1f) {
                    inTumble = false
                    samplesUntilNext = ((0.85f + random.nextFloat() * 0.35f) * SAMPLE_RATE).toInt()
                } else {
                    // Punchy attack (~20%), longer decay — feels like a thump
                    // rather than a click.
                    val env = if (t < 0.2f) t / 0.2f else (1f - t) / 0.8f
                    val rawL = tumbleBuf[i * 2].toFloat() / SHORT_MAX
                    val rawR = tumbleBuf[i * 2 + 1].toFloat() / SHORT_MAX
                    // ~300Hz LP for a chestier thud (was ~800Hz).
                    lpL = lpL * 0.96f + rawL * 0.04f
                    lpR = lpR * 0.96f + rawR * 0.04f
                    // The LP knocks ~12 dB off the noise; pre-boost the gain
                    // so the thump is genuinely louder than the air base.
                    tumbleL = lpL * env * peak * 4.0f
                    tumbleR = lpR * env * peak * 4.0f
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
