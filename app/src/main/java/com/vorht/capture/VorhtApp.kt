package com.vorht.capture

import android.app.Application
import com.vorht.capture.capture.CaptureDispatcher
import com.vorht.capture.util.CrashReporter

class VorhtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CaptureDispatcher.init(this)
    }
}
