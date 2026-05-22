package com.sleepsound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.R
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey
import com.sleepsound.ui.theme.SoftWhite
import com.sleepsound.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import java.util.Locale

private const val MAX_CUSTOM_MINUTES = 720  // 12h ceiling

@Composable
fun TimerSelector(
    minutes: Int?,
    expiryMs: Long?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    val label = timerLabel(minutes, expiryMs)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = stringResource(R.string.cd_timer),
                    tint = IconGrey,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = SoftWhite, fontSize = 14.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceDark),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timer_off_label), color = DimGrey) },
                onClick = { onSelect(null); expanded = false },
            )
            listOf(15, 30, 60, 90).forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.timer_n_minutes, m), color = DimGrey)
                    },
                    onClick = { onSelect(m); expanded = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timer_custom), color = DimGrey) },
                onClick = {
                    expanded = false
                    showCustomDialog = true
                },
            )
        }
    }

    if (showCustomDialog) {
        CustomMinutesDialog(
            initial = minutes ?: 30,
            onConfirm = { picked ->
                onSelect(picked)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false },
        )
    }
}

@Composable
private fun CustomMinutesDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.coerceIn(1, MAX_CUSTOM_MINUTES).toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..MAX_CUSTOM_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = DimGrey,
        textContentColor = DimGrey,
        title = { Text(stringResource(R.string.timer_custom_title), color = SoftWhite) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) text = v },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = {
                    Text(stringResource(R.string.timer_custom_hint, MAX_CUSTOM_MINUTES))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DimGrey,
                    unfocusedTextColor = DimGrey,
                    focusedBorderColor = IconGrey,
                    unfocusedBorderColor = DimmerGrey,
                    focusedLabelColor = DimGrey,
                    unfocusedLabelColor = DimmerGrey,
                    cursorColor = IconGrey,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.coerceIn(1, MAX_CUSTOM_MINUTES)) } },
                enabled = valid,
            ) {
                Text(
                    stringResource(R.string.action_set),
                    color = if (valid) IconGrey else DimmerGrey,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = DimGrey)
            }
        },
    )
}

@Composable
private fun timerLabel(minutes: Int?, expiryMs: Long?): String {
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(minutes, expiryMs) {
        if (minutes == null || expiryMs == null) {
            remainingMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            val r = expiryMs - System.currentTimeMillis()
            remainingMs = r.coerceAtLeast(0L)
            if (r <= 0) break
            delay(500)
        }
    }
    val offText = stringResource(R.string.timer_status_off)
    val endingText = stringResource(R.string.timer_status_ending)
    val pendingText = minutes?.let { stringResource(R.string.timer_status_pending, it) } ?: ""
    return when {
        minutes == null -> offText
        expiryMs == null -> pendingText
        remainingMs <= 0 -> endingText
        else -> "%d:%02d".format(Locale.US, remainingMs / 60_000, (remainingMs / 1000) % 60)
    }
}

