package com.sleepsound.ui.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepsound.R
import com.sleepsound.ui.theme.DimGrey
import com.sleepsound.ui.theme.DimmerGrey
import com.sleepsound.ui.theme.IconGrey
import com.sleepsound.ui.theme.PureBlack
import com.sleepsound.ui.theme.SoftWhite
import com.sleepsound.ui.theme.SurfaceDark

/**
 * Two-card first-run carousel. Shown over the player on first launch:
 *   1. What SleepSound is (the five-pillar promise + the four free sounds).
 *   2. Battery-optimization exemption opt-in (the #1 cause of overnight
 *      audio death). The Reliability section in Settings has the deeper
 *      OEM-killer guidance for the few users who hit that case.
 *
 * Either the "Next" / "Done" button or the "Skip" link marks it completed.
 */
@Composable
fun OnboardingCarousel(onComplete: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val pages = onboardingPages()
    val page = pages[index]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Top row: skip
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = { complete(onComplete) },
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingPage",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = current.icon,
                        contentDescription = null,
                        tint = IconGrey,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = current.title,
                        color = SoftWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = current.body,
                        color = IconGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    current.action?.let { action ->
                        Spacer(modifier = Modifier.height(24.dp))
                        if (action.fulfilled) {
                            FulfilledPill(
                                text = action.fulfilledLabel ?: action.label,
                            )
                        } else {
                            PrimaryButton(
                                text = action.label,
                                onClick = { action.run(context) },
                            )
                        }
                    }
                }
            }

            // Bottom row: dot indicator + next / done
            val nextLabel = if (index == pages.lastIndex)
                stringResource(R.string.onboarding_done)
            else
                stringResource(R.string.onboarding_next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DotIndicator(count = pages.size, current = index)
                PrimaryButton(
                    text = nextLabel,
                    onClick = {
                        if (index == pages.lastIndex) complete(onComplete)
                        else index += 1
                    },
                )
            }
        }
    }
}

private fun complete(onComplete: () -> Unit) {
    OnboardingState.markCompleted()
    onComplete()
}

@Composable
private fun FulfilledPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = IconGrey,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = DimGrey, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, IconGrey, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = DimGrey, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = DimGrey, fontSize = 14.sp)
    }
}

@Composable
private fun DotIndicator(count: Int, current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val color = if (i == current) IconGrey else DimmerGrey
            Box(
                modifier = Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            if (i < count - 1) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

private data class OnboardingAction(
    val label: String,
    val run: (Context) -> Unit,
    /** When true, the button is replaced with a static "done" pill. */
    val fulfilled: Boolean = false,
    /** Text to show when [fulfilled]; defaults to [label] if null. */
    val fulfilledLabel: String? = null,
)

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val action: OnboardingAction?,
)

@Composable
private fun onboardingPages(): List<OnboardingPage> {
    val context = LocalContext.current
    val alreadyExempt = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    return listOf(
        OnboardingPage(
            icon = Icons.Default.Bedtime,
            title = stringResource(R.string.onboarding_p1_title),
            body = stringResource(R.string.onboarding_p1_body),
            action = null,
        ),
        OnboardingPage(
            icon = Icons.Default.BatteryFull,
            title = stringResource(R.string.onboarding_p2_title),
            body = stringResource(R.string.onboarding_p2_body),
            action = OnboardingAction(
                label = stringResource(R.string.onboarding_p2_action),
                run = ::requestIgnoreBatteryOptimizations,
                fulfilled = alreadyExempt,
                fulfilledLabel = stringResource(R.string.onboarding_p2_fulfilled),
            ),
        ),
    )
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
