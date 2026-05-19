package com.sleepsound.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot tracker for the first-run onboarding carousel. Stores a single
 * boolean in its own SharedPreferences file so it can be cleared
 * independently of playback state (e.g. via "clear data" in Settings → Apps).
 */
object OnboardingState {

    private const val PREFS_NAME = "onboarding"
    private const val KEY_COMPLETED = "completed"

    private var prefs: SharedPreferences? = null

    private val _shouldShow = MutableStateFlow(true)
    val shouldShow: StateFlow<Boolean> = _shouldShow.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _shouldShow.value = !p.getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted() {
        _shouldShow.value = false
        prefs?.edit()?.putBoolean(KEY_COMPLETED, true)?.apply()
    }
}
