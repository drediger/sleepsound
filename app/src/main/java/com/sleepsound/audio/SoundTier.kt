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

/**
 * One-shot all-sounds bundle. Registered separately in Play Console at a
 * discounted price ($3.99 vs 6 x $0.99 = $5.94). Purchasing it unlocks
 * every premium SoundId.
 */
const val BUNDLE_PRODUCT_ID = "bundle_all_sounds"
