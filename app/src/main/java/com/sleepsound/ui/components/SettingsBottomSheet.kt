package com.sleepsound.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showLicenses by remember { mutableStateOf(false) }
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
    val oemTitleFmt = stringResource(R.string.settings_oem_title_format)
    val oemSubtitle = stringResource(R.string.settings_oem_subtitle)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SurfaceDark,
        contentColor = DimGrey,
    ) {
        // Fixed minimum height keeps the sheet visually stable when the
        // bundle row hides after the 5 s billing timeout. Without this
        // the sheet snaps shorter and the player tiles peek through.
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .heightIn(min = 480.dp),
        ) {
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
            SettingRow(
                title = oemTitleFmt.format(manufacturerLabel()),
                subtitle = oemSubtitle,
                onClick = { openUrl(context, oemKillerUrl()) },
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
                subtitle = stringResource(R.string.about_privacy_subtitle),
                onClick = { openUrl(context, privacyUrl) },
            )
            SettingRow(
                title = stringResource(R.string.about_oss),
                subtitle = stringResource(R.string.about_oss_subtitle),
                onClick = { showLicenses = true },
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

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Asset read on first composition only — the file is ~7 KB so loading
    // it inline is fine; no need for a background dispatcher.
    val text = remember {
        runCatching {
            context.assets.open("licenses.txt").use { it.bufferedReader().readText() }
        }.getOrDefault("Open-source license attribution unavailable.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = SoftWhite,
        textContentColor = DimGrey,
        title = { Text(stringResource(R.string.about_oss), color = SoftWhite) },
        text = {
            Box(modifier = Modifier.heightIn(max = 420.dp)) {
                Text(
                    text = text,
                    color = DimGrey,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = IconGrey)
            }
        },
    )
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
    // DimmerGrey on SurfaceDark is intentionally subtle but visible.
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

private fun manufacturerLabel(): String {
    val raw = Build.MANUFACTURER.trim()
    if (raw.isEmpty()) return "Phone"
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun oemKillerUrl(): String {
    // dontkillmyapp.com hosts per-OEM guidance pages keyed by lowercase
    // manufacturer slug (e.g. /samsung, /xiaomi, /huawei). Fall back to
    // the index page when MANUFACTURER is empty (rare; some emulators).
    val slug = Build.MANUFACTURER.lowercase().trim().replace(" ", "-")
    return if (slug.isEmpty()) "https://dontkillmyapp.com/"
    else "https://dontkillmyapp.com/$slug"
}
