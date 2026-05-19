package com.sleepsound.audio.procedural

import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import kotlin.random.Random

/**
 * Thunderstorm — Rain backdrop + state-machine that randomly fires low-pass
 * filtered brown-noise thunder rumbles every 20–120 seconds.
 */
class Thunderstorm(
    private val random: Random = Random.Default,
    private val peak: Float = 7500f,
) : NoiseSource {
    private val rain = Rain(random, peak * 0.7f)
    private val rainBuf = ShortArray(BUFFER_FRAMES * 2)

    private val brown = BrownNoise(random, peak = peak * 0.95f)
    private val brownBuf = ShortArray(BUFFER_FRAMES * 2)

    private enum class State { IDLE, ROLLING }
    private var state = State.IDLE
    private var samplesUntilNext = nextDelay()
    private var thunderPhase = 0f
    private var thunderDuration = 0f
    private var thunderAmplitude = 0f
    private var lpL = 0f
    private var lpR = 0f

    private fun nextDelay(): Int =
        ((20f + random.nextFloat() * 100f) * SAMPLE_RATE).toInt()

    override fun fillBuffer(out: ShortArray, frames: Int) {
        rain.fillBuffer(rainBuf, frames)
        brown.fillBuffer(brownBuf, frames)

        for (i in 0 until frames) {
            val rainL = rainBuf[i * 2].toInt()
            val rainR = rainBuf[i * 2 + 1].toInt()

            var thunderL = 0f
            var thunderR = 0f
            when (state) {
                State.IDLE -> {
                    samplesUntilNext--
                    if (samplesUntilNext <= 0) {
                        state = State.ROLLING
                        thunderPhase = 0f
                        thunderDuration = 3f + random.nextFloat() * 4f
                        thunderAmplitude = 0.4f + random.nextFloat() * 0.6f
                    }
                }
                State.ROLLING -> {
                    val t = thunderPhase / thunderDuration
                    if (t >= 1f) {
                        state = State.IDLE
                        samplesUntilNext = nextDelay()
                    } else {
                        val env = if (t < 0.1f) t / 0.1f else (1f - t) / 0.9f
                        val brownL = brownBuf[i * 2].toFloat() / SHORT_MAX
                        val brownR = brownBuf[i * 2 + 1].toFloat() / SHORT_MAX
                        // 1-pole LP, ~38Hz cutoff for deep rumble
                        lpL = lpL * 0.995f + brownL * 0.005f
                        lpR = lpR * 0.995f + brownR * 0.005f
                        thunderL = lpL * env * thunderAmplitude * peak * 1.4f
                        thunderR = lpR * env * thunderAmplitude * peak * 1.4f
                        thunderPhase += 1f / SAMPLE_RATE
                    }
                }
            }

            val l = (rainL + thunderL.toInt())
            val r = (rainR + thunderR.toInt())
            out[i * 2] = l.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            out[i * 2 + 1] = r.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
    }
}
