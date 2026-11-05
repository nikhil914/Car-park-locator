package com.realworldmagic.parkpin

import android.app.Application
import com.realworldmagic.parkpin.di.AppContainer
import com.realworldmagic.parkpin.reminders.NotificationChannels

class ParkPinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
    }
}
