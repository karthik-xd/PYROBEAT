package com.skullbeat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SkullBeatApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
