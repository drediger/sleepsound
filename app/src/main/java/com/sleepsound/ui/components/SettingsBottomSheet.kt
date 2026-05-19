package com.sleepsound.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.BuildConfig
import com.sleepsound.R
import com.sleepsound.billing.BillingManager
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    resumeOnReboot: Boolean,
    onResumeOnRebootChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val coroutineScope = rememberCoroutineScope()
    var restoreStatus by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SurfaceDark,
        contentColor = DimGrey,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            SectionHeader(text = "Reliability")
            SettingRow(
                title = "Allow background playback",
                subtitle = "Prevents Android from killing audio overnight",
                onClick = { requestIgnoreBatteryOptimizations(context) },
            )
            SettingRow(
                title = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} settings",
                subtitle = "Open dontkillmyapp.com for your phone",
                onClick = { openOemInstructions(context) },
            )
            SettingToggle(
                title = "Resume on reboot",
                subtitle = "Auto-start last mix when your phone restarts",
                checked = resumeOnReboot,
                onChange = onResumeOnRebootChange,
            )

            SectionDivider()

            SectionHeader(text = "Purchases")
            SettingRow(
                title = "Restore purchases",
                subtitle = restoreStatus ?: "Restore premium unlocks from your Google account",
                onClick = {
                    restoreStatus = "Restoring…"
                    coroutineScope.launch {
                        val result = BillingManager.restorePurchases()
                        restoreStatus = when {
                            result.error != null -> result.error
                            result.restoredCount == 0 -> "Nothing to restore"
                            result.restoredCount == 1 -> "Restored 1 sound"
                            else -> "Restored ${result.restoredCount} sounds"
                        }
                    }
                },
            )

            SectionDivider()

            SectionHeader(text = stringResource(R.string.about_title))
            PromiseLine(text = stringResource(R.string.about_promise))
            SettingRow(
                title = stringResource(R.string.about_privacy),
                subtitle = privacyUrl,
                onClick = { openUrl(context, privacyUrl) },
            )
            VersionLine(
                text = stringResource(
                    R.string.about_version,
                    BuildConfig.VERSION_NAME,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = DimGrey,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    Box(modifier = Modifier.padding(vertical = 12.dp)) {
        HorizontalDivider(color = PureBlack, thickness = 1.dp)
    }
}

@Composable
private fun PromiseLine(text: String) {
    Text(
        text = text,
        color = DimmerGrey,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun VersionLine(text: String) {
    Text(
        text = text,
        color = DimmerGrey,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = DimGrey, fontSize = 14.sp)
            Text(subtitle, color = DimmerGrey, fontSize = 11.sp, maxLines = 1)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = DimmerGrey,
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = DimGrey, fontSize = 14.sp)
            Text(subtitle, color = DimmerGrey, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IconGrey,
                checkedTrackColor = SurfaceDark,
                checkedBorderColor = IconGrey,
                uncheckedThumbColor = DimmerGrey,
                uncheckedTrackColor = PureBlack,
                uncheckedBorderColor = DimmerGrey,
            ),
        )
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun openOemInstructions(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    openUrl(context, "https://dontkillmyapp.com/$manufacturer")
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
