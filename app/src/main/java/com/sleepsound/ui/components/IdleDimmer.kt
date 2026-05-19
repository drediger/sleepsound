package com.sleepsound.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * After [idleAfterMs] of no touch inside [content], dims the activity's
 * screen brightness and overlays a near-opaque scrim. Any tap on the scrim
 * restores brightness and absorbs the tap (so a sleepy user doesn't
 * accidentally toggle a sound on first contact).
 *
 * Observes pointer events on the Initial pass so it tracks interaction
 * without consuming events meant for children.
 */
@Composable
fun IdleDimmer(
    idleAfterMs: Long = 30_000L,
    dimBrightness: Float = 0.02f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDim by remember { mutableStateOf(false) }

    fun applyBrightness(dim: Boolean) {
        activity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = if (dim) dimBrightness
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            it.window.attributes = lp
        }
    }

    // Re-key on lastInteraction so a tap immediately restarts the idle
    // countdown — no waiting for the next poll tick. While dim, the only
    // way out is the overlay's onClick (which writes lastInteraction and
    // forces undim synchronously below).
    LaunchedEffect(lastInteraction) {
        val deadline = lastInteraction + idleAfterMs
        val now = System.currentTimeMillis()
        if (now >= deadline) {
            if (!isDim) { isDim = true; applyBrightness(true) }
        } else {
            if (isDim) { isDim = false; applyBrightness(false) }
            delay(deadline - now)
            if (System.currentTimeMillis() >= lastInteraction + idleAfterMs) {
                isDim = true
                applyBrightness(true)
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.let {
                val lp = it.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = lp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction = System.currentTimeMillis()
                    }
                }
            },
    ) {
        content()
        if (isDim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // Restore brightness synchronously so the user sees
                        // the UI on the same frame. LaunchedEffect will
                        // re-fire from the lastInteraction change but the
                        // visible state is already correct.
                        isDim = false
                        applyBrightness(false)
                        lastInteraction = System.currentTimeMillis()
                    },
            )
        }
    }
}
