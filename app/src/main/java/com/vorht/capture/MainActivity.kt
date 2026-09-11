package com.vorht.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vorht.capture.ui.SetupScreen
import com.vorht.capture.ui.StatusScreen
import com.vorht.capture.ui.VorhtTheme
import com.vorht.capture.util.SystemIntents

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VorhtTheme {
                VorhtAppUi()
            }
        }
    }
}

@Composable
private fun VorhtAppUi() {
    // Permission gate: re-check both grants every time the activity resumes
    // (i.e. when the user returns from the system settings screens).
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var resumeKey by remember { mutableIntStateOf(0) }
    if (activity != null) {
        DisposableEffect(activity) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) resumeKey++
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }
    val listenerEnabled = remember(resumeKey) { SystemIntents.isListenerEnabled(context) }
    val batteryExempt = remember(resumeKey) { SystemIntents.isIgnoringBatteryOptimizations(context) }

    if (activity != null && (!listenerEnabled || !batteryExempt)) {
        SetupScreen(
            listenerEnabled = listenerEnabled,
            batteryExempt = batteryExempt,
            onRefresh = { resumeKey++ },
        )
        return
    }

    StatusScreen()
}
