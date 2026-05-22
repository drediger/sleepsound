package com.sleepsound.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.sleepsound.ui.components.MixPanel
import com.sleepsound.ui.components.SettingsBottomSheet
import com.sleepsound.ui.components.SoundTile
import com.sleepsound.ui.components.TimerSelector
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SoftWhite
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
    val previewExpiry by PlaybackController.previewExpiry.collectAsState()
    val pendingPurchasePrompt by PlaybackController.pendingPurchasePrompt.collectAsState()
    val unlocked by EntitlementStore.unlocked.collectAsState()
    val prices by BillingManager.prices.collectAsState()
    val lastPurchaseResult by BillingManager.lastResult.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val unlockedMsgFmt = stringResource(R.string.snackbar_unlocked)
    val bundleUnlockedMsg = stringResource(R.string.snackbar_bundle_unlocked)
    val canceledMsg = stringResource(R.string.snackbar_purchase_canceled)
    val failedMsg = stringResource(R.string.snackbar_purchase_failed)
    val failedOfflineMsg = stringResource(R.string.snackbar_purchase_failed_offline)
    val pendingMsg = stringResource(R.string.snackbar_purchase_pending)

    LaunchedEffect(lastPurchaseResult) {
        val msg = when (val r = lastPurchaseResult) {
            is PurchaseResult.Success -> unlockedMsgFmt.format(r.id.displayName)
            PurchaseResult.BundleSuccess -> bundleUnlockedMsg
            PurchaseResult.UserCanceled -> canceledMsg
            PurchaseResult.Pending -> pendingMsg
            is PurchaseResult.Failure -> if (r.offline) failedOfflineMsg else failedMsg
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

    // Defer the POST_NOTIFICATIONS system prompt until the user has actually
    // started a sound, AND show our own rationale in a snackbar action first.
    // Otherwise the dialog fires immediately after onboarding's "No tracking"
    // pillar lands, which reads as a contradiction. One ask per session.
    val notifRationaleMsg = stringResource(R.string.permission_notif_rationale)
    val notifAllowLabel = stringResource(R.string.permission_notif_action)
    var notifPromptShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeSounds.isNotEmpty()) {
        if (!activeSounds.isNotEmpty()) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (notifPromptShown) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        notifPromptShown = true
        if (granted) return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = notifRationaleMsg,
            actionLabel = notifAllowLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = statusMsg,
                    color = SoftWhite,
                    // 16 sp Normal carries enough weight for the only
                    // persistent "is anything happening?" signal; the
                    // previous 14 sp Light competed visually with the
                    // settings gear.
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(vertical = 14.dp)
                        // Polite liveRegion so TalkBack announces
                        // "Playing 3 sounds" when the count changes,
                        // without interrupting the current utterance.
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
                        tint = SoftWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val allIds = SoundId.entries
                    // Natural row-by-row fill — the orphan 10th tile lands at
                    // row-4 col-1. Earlier rc2 code bracketed it with empty
                    // cells to "center" it, but when MixPanel grew the grid
                    // shrank and the centered orphan was the first thing to
                    // clip. Left-aligned is more predictable under pressure.

                    @Composable
                    fun tile(id: SoundId) {
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

                    items(allIds.size) { i -> tile(allIds[i]) }
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
                contentColor = SoftWhite,
            ) { Text(data.visuals.message, color = SoftWhite, fontSize = 14.sp) }
        }
    }

    if (showSettings) {
        SettingsBottomSheet(
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StopChip(onClick: () -> Unit) {
    // 56 dp circle — matches the visual weight of the Timer pill on the
    // opposite corner. For a sleep app Stop is the primary overnight
    // action; under-sizing it relative to the Timer was the wrong
    // hierarchy.
    Box(
        modifier = Modifier
            .size(56.dp)
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
            tint = SoftWhite,
            modifier = Modifier.size(26.dp),
        )
    }
}

