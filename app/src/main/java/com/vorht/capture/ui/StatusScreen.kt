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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vorht.capture.R
import com.vorht.capture.capture.CaptureDispatcher
import com.vorht.capture.util.ConnectivityObserver
import com.vorht.capture.util.SystemIntents

/**
 * Status screen only — no logs, no stored content. The app forwards every
 * WhatsApp notification instantly; these counters reset with the process.
 */
@Composable
fun StatusScreen() {
    val forwarded by CaptureDispatcher.forwarded.collectAsState()
    val pending by CaptureDispatcher.pending.collectAsState()
    val context = LocalContext.current
    val online by remember(context) {
        ConnectivityObserver(context).isOnline
    }.collectAsState(initial = true)
    val listenerActive = SystemIntents.isListenerEnabled(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                if (online) "Online" else "Offline",
                if (online) VorhtColors.OkGreen else VorhtColors.WarnAmber,
            )
            StatusChip(
                if (listenerActive) "Listening" else "No access",
                if (listenerActive) VorhtColors.OkGreen else VorhtColors.ErrRed,
            )
            if (pending > 0) StatusChip("$pending sending…", VorhtColors.WarnAmber)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Every WhatsApp message is forwarded instantly.",
            style = MaterialTheme.typography.titleMedium,
            color = VorhtColors.Whiteout,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing is stored on this phone — no logs, no queue. Messages are " +
                "captured immediately, delivered via HTTP, and retried for up to 30 " +
                "seconds while the process is alive. Stale messages not delivered within " +
                "that 30-second window are intentionally discarded.",
            style = MaterialTheme.typography.bodyMedium,
            color = VorhtColors.Whiteout.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text(
                    forwarded.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = VorhtColors.OkGreen,
                )
                Text(
                    "forwarded",
                    style = MaterialTheme.typography.labelMedium,
                    color = VorhtColors.Whiteout.copy(alpha = 0.6f),
                )
            }
            if (pending > 0) {
                Column {
                    Text(
                        pending.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = VorhtColors.WarnAmber,
                    )
                    Text(
                        "sending (retrying)",
                        style = MaterialTheme.typography.labelMedium,
                        color = VorhtColors.Whiteout.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
