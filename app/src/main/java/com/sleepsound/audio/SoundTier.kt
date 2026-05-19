package com.sleepsound.audio

enum class SoundTier { FREE, PREMIUM }

/**
 * Play Billing product ID for a premium sound. Convention:
 *   sound_<lowercase_id>   (e.g. sound_pink_noise)
 *
 * Free sounds have no product ID — entitlement queries are short-circuited.
 * Premium sounds must be registered in Play Console under these exact IDs.
 */
fun SoundId.productId(): String? =
    if (tier == SoundTier.PREMIUM) "sound_${name.lowercase()}" else null
