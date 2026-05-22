package com.sleepsound.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sleepsound.BuildConfig
import com.sleepsound.R
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.SoundTier
import com.sleepsound.billing.BillingManager
import com.sleepsound.billing.EntitlementStore
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SoftWhite
import com.sleepsound.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val coroutineScope = rememberCoroutineScope()
    var restoreStatus by remember { mutableStateOf<String?>(null) }
    // Re-check on every ON_RESUME so the row flips to "Allowed" when the
    // user returns from the system battery-optimization dialog.
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val bundlePrice by BillingManager.bundlePrice.collectAsState()
    // If BillingClient never returns a price (no Play Services, network
    // dead, products not yet registered), don't leave the row stuck on
    // "Bundle price loading…" forever — give it a 5 s grace then hide.
    var bundleTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(bundlePrice) {
        if (bundlePrice == null) {
            delay(5_000)
            bundleTimedOut = true
        } else {
            bundleTimedOut = false
        }
    }
    val unlocked by EntitlementStore.unlocked.collectAsState()
    val allPremiumUnlocked = SoundId.entries
        .filter { it.tier == SoundTier.PREMIUM }
        .all { it in unlocked }

    val bundleLoadingText = stringResource(R.string.settings_bundle_loading)
    val bundleSubtitleFmt = stringResource(R.string.settings_bundle_subtitle_format)
    val restoreDefault = stringResource(R.string.settings_restore_subtitle)
    val restoreInProgress = stringResource(R.string.restore_in_progress)
    val restoreNoneText = stringResource(R.string.restore_none)
    val restoreOneText = stringResource(R.string.restore_one)
    val restoreNFmt = stringResource(R.string.restore_n)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SurfaceDark,
        contentColor = DimGrey,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            SectionHeader(text = stringResource(R.string.settings_section_reliability))
            SettingRow(
                title = stringResource(R.string.settings_battery_title),
                subtitle = if (batteryExempt)
                    stringResource(R.string.settings_battery_granted_subtitle)
                else
                    stringResource(R.string.settings_battery_subtitle),
                onClick = { requestIgnoreBatteryOptimizations(context) },
                done = batteryExempt,
            )

            SectionDivider()

            SectionHeader(text = stringResource(R.string.settings_section_purchases))
            if (!allPremiumUnlocked && (bundlePrice != null || !bundleTimedOut)) {
                SettingRow(
                    title = stringResource(R.string.settings_bundle_title),
                    subtitle = bundlePrice?.let { bundleSubtitleFmt.format(it) }
                        ?: bundleLoadingText,
                    onClick = {
                        activity?.let { BillingManager.launchBundlePurchaseFlow(it) }
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_restore_title),
                subtitle = restoreStatus ?: restoreDefault,
                onClick = {
                    restoreStatus = restoreInProgress
                    coroutineScope.launch {
                        val result = BillingManager.restorePurchases()
                        restoreStatus = when {
                            result.error != null -> result.error
                            result.restoredCount == 0 -> restoreNoneText
                            result.restoredCount == 1 -> restoreOneText
                            else -> restoreNFmt.format(result.restoredCount)
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
        color = SoftWhite,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    // DimmerGrey #333 on SurfaceDark #111 is ~2:1 — intentionally subtle but
    // visible. PureBlack on SurfaceDark was effectively invisible (1.59:1).
    Box(modifier = Modifier.padding(vertical = 12.dp)) {
        HorizontalDivider(color = DimmerGrey, thickness = 1.dp)
    }
}

@Composable
private fun PromiseLine(text: String) {
    Text(
        text = text,
        color = DimGrey,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun VersionLine(text: String) {
    Text(
        text = text,
        color = DimGrey,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    done: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (done) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontSize = 14.sp)
            Text(subtitle, color = DimGrey, fontSize = 11.sp, maxLines = 1)
        }
        Icon(
            imageVector = if (done) Icons.Default.Check else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = DimGrey,
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (isIgnoringBatteryOptimizations(context)) return
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
