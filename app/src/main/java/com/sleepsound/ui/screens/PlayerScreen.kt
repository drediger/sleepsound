package com.sleepsound.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.PlaybackController
import com.sleepsound.R
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.SoundTier
import com.sleepsound.billing.BillingManager
import com.sleepsound.billing.EntitlementStore
import com.sleepsound.billing.PurchaseResult
import com.sleepsound.ui.components.IdleDimmer
import com.sleepsound.ui.components.MixPanel
import com.sleepsound.ui.components.SettingsBottomSheet
import com.sleepsound.ui.components.SoundTile
import com.sleepsound.ui.components.TimerSelector
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SurfaceDark
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val activeSounds by PlaybackController.activeSounds.collectAsState()
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val timerMinutes by PlaybackController.timerMinutes.collectAsState()
    val timerExpiryMs by PlaybackController.timerExpiryMs.collectAsState()
    val layerGains by PlaybackController.layerGains.collectAsState()
    val resumeOnReboot by PlaybackController.resumeOnReboot.collectAsState()
    val previewExpiry by PlaybackController.previewExpiry.collectAsState()
    val pendingPurchasePrompt by PlaybackController.pendingPurchasePrompt.collectAsState()
    val unlocked by EntitlementStore.unlocked.collectAsState()
    val prices by BillingManager.prices.collectAsState()
    val lastPurchaseResult by BillingManager.lastResult.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val unlockedMsgFmt = stringResource(R.string.snackbar_unlocked)
    val canceledMsg = stringResource(R.string.snackbar_purchase_canceled)
    val failedMsg = stringResource(R.string.snackbar_purchase_failed)

    LaunchedEffect(lastPurchaseResult) {
        val msg = when (val r = lastPurchaseResult) {
            is PurchaseResult.Success -> unlockedMsgFmt.format(r.id.displayName)
            PurchaseResult.UserCanceled -> canceledMsg
            is PurchaseResult.Failure -> failedMsg
            null -> null
        }
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            BillingManager.consumeLastResult()
        }
    }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }

    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — playback continues regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    IdleDimmer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val statusMsg = when {
                    activeSounds.isEmpty() -> stringResource(R.string.player_tap_a_sound)
                    isPlaying && activeSounds.size == 1 ->
                        stringResource(R.string.player_playing_one)
                    isPlaying -> stringResource(R.string.player_playing_n, activeSounds.size)
                    else -> stringResource(R.string.player_tap_to_resume)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = statusMsg,
                        color = DimmerGrey,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(vertical = 12.dp),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showSettings = true }
                            .padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = DimmerGrey,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(SoundId.entries) { id ->
                        val isLocked = id.tier == SoundTier.PREMIUM && id !in unlocked
                        val expiry = previewExpiry[id]
                        val previewRemaining = expiry?.let { (it - now).coerceAtLeast(0L) }
                        SoundTile(
                            id = id,
                            active = id in activeSounds,
                            locked = isLocked,
                            previewMsRemaining = previewRemaining,
                            showBuyPrompt = pendingPurchasePrompt == id,
                            price = prices[id],
                            onToggle = {
                                PlaybackController.dismissPurchasePrompt()
                                PlaybackController.toggleSound(context, id)
                            },
                            onBuy = {
                                activity?.let { BillingManager.launchPurchaseFlow(it, id) }
                                PlaybackController.dismissPurchasePrompt()
                            },
                        )
                    }
                }

                MixPanel(
                    activeSounds = activeSounds,
                    layerGains = layerGains,
                    onGainChange = { id, gain -> PlaybackController.setLayerGain(id, gain) },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimerSelector(
                        minutes = timerMinutes,
                        expiryMs = timerExpiryMs,
                        onSelect = { PlaybackController.setTimer(it) },
                    )
                    if (isPlaying) {
                        StopChip(onClick = { PlaybackController.stopPlayback(context) })
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            ) { data ->
                Snackbar(
                    containerColor = SurfaceDark,
                    contentColor = DimGrey,
                ) { Text(data.visuals.message, color = DimGrey, fontSize = 14.sp) }
            }
        }
    }

    if (showSettings) {
        SettingsBottomSheet(
            resumeOnReboot = resumeOnReboot,
            onResumeOnRebootChange = PlaybackController::setResumeOnReboot,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StopChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = stringResource(R.string.cd_stop),
            tint = DimGrey,
            modifier = Modifier.size(22.dp),
        )
    }
}

