package com.vorht.capture.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vorht.capture.R
import com.vorht.capture.util.SystemIntents

/**
 * Blocking onboarding: the app ASKS for both grants it needs instead of hoping
 * the user finds them. Shown until notification access AND battery exemption
 * are both granted.
 */
@Composable
fun SetupScreen(
    listenerEnabled: Boolean,
    batteryExempt: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    // Auto-launch the notification access screen on first entry — the "ask".
    LaunchedEffect(listenerEnabled) {
        if (!listenerEnabled && !askedOnce) {
            askedOnce = true
            activity?.let { SystemIntents.openNotificationAccessSettings(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.vorht_logo_light),
                contentDescription = "Vorht",
                modifier = Modifier.height(24.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Set up ",
                style = MaterialTheme.typography.headlineSmall,
                color = VorhtColors.Whiteout,
            )
            Text(
                "capture",
                style = CursiveAccent.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize),
                color = VorhtColors.SignalBlue,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Two system grants keep WhatsApp verification capture running — even offline, overnight, or after a reboot.",
            style = MaterialTheme.typography.bodyMedium,
            color = VorhtColors.Whiteout.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(24.dp))

        SetupCard(
            step = "1",
            title = "Notification access",
            body = "Allows Vorht to read SMS and WhatsApp notifications the moment they arrive. " +
                "Monitored strictly on-device.",
            done = listenerEnabled,
            doneLabel = "Granted",
            actionLabel = "Grant access",
            onAction = { activity?.let { SystemIntents.openNotificationAccessSettings(it) } },
        )

        Spacer(Modifier.height(12.dp))

        SetupCard(
            step = "2",
            title = "Unrestricted background",
            body = "Stops the system from freezing Vorht so captured events upload " +
                "instantly and the listener never sleeps.",
            done = batteryExempt,
            doneLabel = "Unrestricted",
            actionLabel = "Allow",
            onAction = { activity?.let { SystemIntents.requestIgnoreBatteryOptimizations(it) } },
        )

        Spacer(Modifier.height(12.dp))

        HonorTipCard()

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onRefresh,
            shape = MaterialTheme.shapes.small,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = VorhtColors.Whiteout,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, VorhtColors.Whiteout),
        ) {
            Text("I've granted both — re-check")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "The screen unlocks automatically once both grants are detected.",
            style = MaterialTheme.typography.labelSmall,
            color = VorhtColors.Whiteout.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun HonorTipCard() {
    // Honor / MagicOS ignores the generic exemption dialog — their own manager
    // must be used or it kills the listener.
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = VorhtColors.SkyDeep),
        border = androidx.compose.foundation.BorderStroke(1.dp, VorhtColors.Hairline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Honor / Huawei extra step",
                style = MaterialTheme.typography.titleMedium,
                color = VorhtColors.WarnAmber,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Settings → Battery → App launch → Vorht Capture → turn on " +
                    "“Manage manually” and enable all three switches " +
                    "(Auto-launch, Secondary launch, Run in background). " +
                    "Without this, MagicOS kills the listener.",
                style = MaterialTheme.typography.bodySmall,
                color = VorhtColors.Whiteout.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SetupCard(
    step: String,
    title: String,
    body: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = VorhtColors.SkyMid),
        border = androidx.compose.foundation.BorderStroke(1.dp, VorhtColors.Hairline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    step,
                    style = MaterialTheme.typography.headlineSmall,
                    color = VorhtColors.SignalBlue,
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                StatusChip(
                    if (done) doneLabel else "Required",
                    if (done) VorhtColors.OkGreen else VorhtColors.WarnAmber,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = VorhtColors.Whiteout.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            if (!done) {
                // Ghost button per DESIGN.md
                OutlinedButton(
                    onClick = onAction,
                    shape = MaterialTheme.shapes.small,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = VorhtColors.Whiteout,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VorhtColors.Whiteout),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
