package com.sleepsound.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundIdTest {

    @Test
    fun `catalog has exactly 10 sounds after TV static drop`() {
        // If this fires, update store/STORE_LISTING.md + BUILDING.md product
        // IDs + CHANGELOG.md "Premium tier (N × $0.99)" line.
        assertEquals(10, SoundId.entries.size)
    }

    @Test
    fun `four sounds are free, six are premium`() {
        val free = SoundId.entries.filter { it.tier == SoundTier.FREE }
        val premium = SoundId.entries.filter { it.tier == SoundTier.PREMIUM }
        assertEquals("free tier count", 4, free.size)
        assertEquals("premium tier count", 6, premium.size)
    }

    @Test
    fun `free tier is exactly the four most-searched generic noise + nature sounds`() {
        // PLAN.md section 3: the four free sounds are decided by Play Store
        // search-term volume. Locking the set here so a slip into "let's
        // make X free" doesn't get past code review.
        val expected = setOf(
            SoundId.BROWN_NOISE,
            SoundId.WHITE_NOISE,
            SoundId.RAIN,
            SoundId.OCEAN,
        )
        val actual = SoundId.entries.filter { it.tier == SoundTier.FREE }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `displayName is non-empty for every sound`() {
        SoundId.entries.forEach {
            assertTrue("${it.name} has blank displayName", it.displayName.isNotBlank())
        }
    }
}
