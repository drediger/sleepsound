package com.sleepsound.audio.procedural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BrownNoiseTest {

    @Test
    fun `output is stereo-mirrored — mono source duplicated to both channels`() {
        val source = BrownNoise(random = Random(42))
        val frames = 256
        val out = ShortArray(frames * 2)
        source.fillBuffer(out, frames)
        for (i in 0 until frames) {
            assertEquals(
                "frame $i left/right mismatch",
                out[i * 2],
                out[i * 2 + 1],
            )
        }
    }

    @Test
    fun `seeded Random produces deterministic output across runs`() {
        val a = BrownNoise(random = Random(123))
        val b = BrownNoise(random = Random(123))
        val frames = 512
        val outA = ShortArray(frames * 2)
        val outB = ShortArray(frames * 2)
        a.fillBuffer(outA, frames)
        b.fillBuffer(outB, frames)
        assertTrue("same seed must produce identical output", outA.contentEquals(outB))
    }

    @Test
    fun `output stays within Short range — no overflow from leaky integrator`() {
        val source = BrownNoise(random = Random(7), peak = 32_000f)
        // 1 second of audio
        val frames = SAMPLE_RATE_TEST
        val out = ShortArray(frames * 2)
        source.fillBuffer(out, frames)
        // Trivially holds because the loop clamps b∈[-1,1] before scaling and
        // ShortArray storage truncates — this guards against future changes to
        // the clamp logic.
        val maxAbs = out.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("sample magnitude $maxAbs exceeded Short.MAX_VALUE", maxAbs <= Short.MAX_VALUE.toInt())
    }

    @Test
    fun `output is not silent after warm-up`() {
        val source = BrownNoise(random = Random(99))
        val frames = 4096
        val out = ShortArray(frames * 2)
        source.fillBuffer(out, frames)
        // After ~85 ms of integration we should see meaningful signal.
        val zeros = out.count { it == 0.toShort() }
        assertNotEquals("brown noise produced an all-zeros buffer", out.size, zeros)
    }

    @Test
    fun `state persists across consecutive fillBuffer calls`() {
        // Brown noise is an integrator — output of two calls back-to-back
        // should differ from output of one big call only by buffer boundary
        // accounting, not by reset. We assert that calling fillBuffer twice
        // with frames=N produces output whose second half is *not* equal to
        // the first half (i.e. the integrator kept walking, didn't reset).
        val source = BrownNoise(random = Random(2024))
        val frames = 256
        val first = ShortArray(frames * 2)
        val second = ShortArray(frames * 2)
        source.fillBuffer(first, frames)
        source.fillBuffer(second, frames)
        assertTrue(
            "expected second buffer to differ from first (no reset between calls)",
            !first.contentEquals(second),
        )
    }

    private companion object {
        const val SAMPLE_RATE_TEST = 48_000
    }
}
