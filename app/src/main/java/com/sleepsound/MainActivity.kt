package com.sleepsound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.sleepsound.billing.BillingManager
import com.sleepsound.billing.EntitlementStore
import com.sleepsound.ui.onboarding.OnboardingCarousel
import com.sleepsound.ui.onboarding.OnboardingState
import com.sleepsound.ui.screens.PlayerScreen
import com.sleepsound.ui.theme.AmoledTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        PlaybackController.init(this)
        EntitlementStore.init(this)
        OnboardingState.init(this)
        BillingManager.init(this, lifecycleScope)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            AmoledTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    val showOnboarding by OnboardingState.shouldShow.collectAsState()
                    if (showOnboarding) {
                        OnboardingCarousel(onComplete = { /* state already flipped */ })
                    } else {
                        PlayerScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh entitlements on every resume — handles purchases made on
        // another device, refunds, or recovery after Play Services updates.
        BillingManager.connect()
    }
}
