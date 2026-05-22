package com.sleepsound.billing

import android.content.Context
import android.content.SharedPreferences
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.SoundTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which premium sounds the user has purchased. Free sounds are always
 * considered unlocked. Persists paid unlocks to SharedPreferences so cold
 * starts don't require a Play Billing round-trip; [setPaidUnlocks] is the
 * source-of-truth path after a restore-purchases call.
 */
object EntitlementStore {

    private const val PREFS_NAME = "entitlements"
    private const val KEY_UNLOCKED = "unlocked_sound_ids"

    private var prefs: SharedPreferences? = null

    private val _unlocked = MutableStateFlow<Set<SoundId>>(emptySet())
    val unlocked: StateFlow<Set<SoundId>> = _unlocked.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        val savedPaid = p.getStringSet(KEY_UNLOCKED, emptySet())
            ?.mapNotNull { runCatching { SoundId.valueOf(it) }.getOrNull() }
            ?.filter { it.tier == SoundTier.PREMIUM }
            ?.toSet() ?: emptySet()
        _unlocked.value = freeSounds + savedPaid
    }

    fun isUnlocked(id: SoundId): Boolean = id in _unlocked.value

    /** Mark a single premium sound as purchased. No-op for free sounds. */
    fun unlock(id: SoundId) {
        if (id.tier == SoundTier.FREE) return
        val current = _unlocked.value
        if (id in current) return
        val updated = current + id
        _unlocked.value = updated
        persistPaid(updated)
    }

    /**
     * Mark several premium sounds as purchased in one go — single SharedPrefs
     * write regardless of how many ids are added. Used by [BillingManager]
     * when a bundle purchase entitles all six premium sounds at once.
     */
    fun unlockMany(ids: Set<SoundId>) {
        val toAdd = ids.filter { it.tier == SoundTier.PREMIUM }.toSet()
        if (toAdd.isEmpty()) return
        val current = _unlocked.value
        val updated = current + toAdd
        if (updated == current) return
        _unlocked.value = updated
        persistPaid(updated)
    }

    /**
     * Replace the set of paid unlocks. Called by [BillingManager] after a
     * [queryPurchasesAsync] response so removed/refunded entitlements
     * are also reflected.
     */
    fun setPaidUnlocks(ids: Set<SoundId>) {
        val premiumOnly = ids.filter { it.tier == SoundTier.PREMIUM }.toSet()
        _unlocked.value = freeSounds + premiumOnly
        persistPaid(premiumOnly)
    }

    private val freeSounds: Set<SoundId> =
        SoundId.entries.filter { it.tier == SoundTier.FREE }.toSet()

    private fun persistPaid(unlocks: Set<SoundId>) {
        val paid = unlocks
            .filter { it.tier == SoundTier.PREMIUM }
            .map { it.name }
            .toSet()
        prefs?.edit()?.putStringSet(KEY_UNLOCKED, paid)?.apply()
    }
}
