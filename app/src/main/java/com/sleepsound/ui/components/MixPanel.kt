package com.sleepsound.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.R
import com.sleepsound.audio.SoundId
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey

/**
 * Per-active-sound volume sliders with a mute icon that remembers the last
 * non-zero gain. Rows fade/expand in as sounds become active.
 * Order matches SoundId declaration so layout doesn't shuffle as users toggle.
 */
@Composable
fun MixPanel(
    activeSounds: Set<SoundId>,
    layerGains: Map<SoundId, Float>,
    onGainChange: (SoundId, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
    ) {
        SoundId.entries.forEach { id ->
            AnimatedVisibility(
                visible = id in activeSounds,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MixRow(
                    id = id,
                    gain = layerGains[id] ?: 1f,
                    onGainChange = { onGainChange(id, it) },
                )
            }
        }
    }
}

@Composable
private fun MixRow(
    id: SoundId,
    gain: Float,
    onGainChange: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var lastNonZero by rememberSaveable(id) { mutableFloatStateOf(if (gain > 0f) gain else 1f) }
    if (gain > 0f && gain != lastNonZero) lastNonZero = gain
    val isMuted = gain <= 0.001f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = id.displayName,
            modifier = Modifier.width(88.dp),
            color = DimGrey,
            fontSize = 12.sp,
        )
        Slider(
            value = gain,
            onValueChange = onGainChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = IconGrey,
                activeTrackColor = DimGrey,
                inactiveTrackColor = DimmerGrey,
            ),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onGainChange(if (isMuted) lastNonZero else 0f)
                },
            contentAlignment = Alignment.Center,
        ) {
            val muteCd = stringResource(R.string.cd_mute_format, id.displayName)
            val unmuteCd = stringResource(R.string.cd_unmute_format, id.displayName)
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) unmuteCd else muteCd,
                tint = if (isMuted) DimmerGrey else IconGrey,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
