package com.sleepsound.audio

/**
 * Free tier covers the four classics most users come for; everything else is
 * a $0.99 in-app unlock. Free/premium split is *content*, not a feature gate
 * — the engine, mixer, timer, and reliability features are identical for both.
 */
enum class SoundId(val displayName: String, val tier: SoundTier) {
    BROWN_NOISE("Brown noise", SoundTier.FREE),
    PINK_NOISE("Pink noise", SoundTier.PREMIUM),
    WHITE_NOISE("White noise", SoundTier.FREE),
    VIOLET_NOISE("Violet noise", SoundTier.PREMIUM),
    RAIN("Rain", SoundTier.FREE),
    THUNDERSTORM("Thunderstorm", SoundTier.PREMIUM),
    DRYER("Dryer", SoundTier.PREMIUM),
    OCEAN("Ocean", SoundTier.FREE),
    FAN("Fan", SoundTier.PREMIUM),
    FIREPLACE("Fireplace", SoundTier.PREMIUM),
}
