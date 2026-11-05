package com.realworldmagic.parkpin

import android.app.Application
import android.content.Context
import com.realworldmagic.parkpin.di.AppContainer
import com.realworldmagic.parkpin.reminders.NotificationChannels
import org.osmdroid.config.Configuration
import java.io.File

class ParkPinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        configureOsmdroid()
    }

    private fun configureOsmdroid() {
        val prefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        val config = Configuration.getInstance()
        config.load(this, prefs)
        config.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid").apply { mkdirs() }
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        // Persist so later Configuration.load(...) calls (e.g. from the map screen) keep these values.
        config.save(this, prefs)
    }
}
