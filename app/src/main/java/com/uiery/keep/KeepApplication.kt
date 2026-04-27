package com.uiery.keep

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KeepApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}