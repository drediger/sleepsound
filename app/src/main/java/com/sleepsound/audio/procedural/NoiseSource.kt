package com.sleepsound.audio.procedural

interface NoiseSource {
    /**
     * Fill an interleaved stereo PCM16 buffer with [frames] frames of audio.
     * Layout: [L0, R0, L1, R1, ...]. Buffer size must be at least frames * 2.
     */
    fun fillBuffer(out: ShortArray, frames: Int)
}
