package com.vorht.capture

import android.app.Application
import com.vorht.capture.capture.CaptureDispatcher
import com.vorht.capture.util.CrashReporter
import com.vorht.capture.util.VorhtLogger

class VorhtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        VorhtLogger.init(this)
        CaptureDispatcher.init(this)
    }
}
