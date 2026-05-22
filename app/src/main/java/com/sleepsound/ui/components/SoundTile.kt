package com.sleepsound.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.R
import com.sleepsound.audio.SoundId
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SoftWhite
import com.sleepsound.ui.theme.SurfaceDark

/**
 * One sound tile. Combines four orthogonal pieces of state:
 *
 * - [active]: currently in the mix.
 * - [locked]: premium and not entitled. Adds a small lock badge.
 * - [previewMsRemaining]: if non-null, replaces the lock badge with a
 *   live countdown (e.g. "0:23"). Implies [active] and [locked].
 * - [showBuyPrompt]: this tile's preview just expired. The label is
 *   replaced with the price ("$0.99 →"); tapping invokes [onBuy] instead
 *   of [onToggle].
 */
@Composable
fun SoundTile(
    id: SoundId,
    active: Boolean,
    locked: Boolean = false,
    previewMsRemaining: Long? = null,
    showBuyPrompt: Boolean = false,
    price: String? = null,
    onToggle: () -> Unit,
    onBuy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            showBuyPrompt -> IconGrey
            active -> IconGrey
            else -> Color.Transparent
        },
        label = "tileBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (active || showBuyPrompt) SurfaceDark else PureBlack,
        label = "tileBg",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "tilePress",
    )
    val contentColor = if (active) SoftWhite else DimGrey
    val textColor = if (active) SoftWhite else DimGrey

    // TalkBack: collapse the tile's children into one announcement
    // "<sound>, button, <state>" rather than reading the icon + label + badge
    // independently. Without this the icon's contentDescription and the price
    // pill text get double-announced.
    val stateDesc = when {
        showBuyPrompt -> stringResource(R.string.tile_state_buy, price ?: "")
        active && previewMsRemaining != null ->
            stringResource(
                R.string.tile_state_previewing,
                (previewMsRemaining / 1000L).coerceAtLeast(0L).toInt(),
            )
        active -> stringResource(R.string.tile_state_playing)
        locked -> stringResource(R.string.tile_state_locked)
        else -> stringResource(R.string.tile_state_off)
    }
    val tileName = id.displayName

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(pressScale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (showBuyPrompt) onBuy() else onToggle()
                },
            )
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = tileName
                stateDescription = stateDesc
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = iconFor(id),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (showBuyPrompt) {
                BuyPill(price = price ?: stringResource(R.string.buy_fallback))
            } else {
                Text(
                    text = id.displayName,
                    color = textColor,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }

        // Top-right badge: lock icon when locked, countdown when previewing.
        when {
            previewMsRemaining != null -> CountdownBadge(
                remainingMs = previewMsRemaining,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            locked && !showBuyPrompt -> LockBadge(
                modifier = Modifier.align(Alignment.TopEnd),
            )
            else -> { /* no badge */ }
        }
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = stringResource(R.string.cd_locked_tile),
        tint = DimGrey,
        modifier = modifier.size(12.dp),
    )
}

@Composable
private fun CountdownBadge(remainingMs: Long, modifier: Modifier = Modifier) {
    val s = (remainingMs / 1000).coerceAtLeast(0)
    val label = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "${s}s"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PureBlack)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text = label, color = IconGrey, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BuyPill(price: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(PureBlack)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "$price →",
            color = IconGrey,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun iconFor(id: SoundId): ImageVector = when (id) {
    SoundId.BROWN_NOISE -> Icons.Default.GraphicEq
    SoundId.PINK_NOISE -> Icons.Default.BlurOn
    SoundId.WHITE_NOISE -> Icons.Default.Brightness1
    SoundId.VIOLET_NOISE -> Icons.Default.Bolt
    SoundId.RAIN -> Icons.Default.WaterDrop
    SoundId.THUNDERSTORM -> Icons.Default.Thunderstorm
    SoundId.DRYER -> Icons.Default.LocalLaundryService
    SoundId.OCEAN -> Icons.Default.Waves
    SoundId.FAN -> Icons.Default.Air
    SoundId.FIREPLACE -> Icons.Default.LocalFireDepartment
}
