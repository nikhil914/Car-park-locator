package com.realworldmagic.parkpin.di

import android.content.Context
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.ParkingRepositoryImpl
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.data.db.ParkPinDatabase
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.location.LocationProviderFactory
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.sensors.BarometerSensor
import com.realworldmagic.parkpin.sensors.CompassSensor

/** Manual DI. Everything is lazy so app start stays cheap. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: ParkPinDatabase by lazy { ParkPinDatabase.build(appContext) }
    val parkingRepository: ParkingRepository by lazy { ParkingRepositoryImpl(database.parkingDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val locationProvider: LocationProvider by lazy { LocationProviderFactory.create(appContext) }
    val compassSensor: CompassSensor by lazy { CompassSensor(appContext) }
    val barometerSensor: BarometerSensor by lazy { BarometerSensor(appContext) }
    val meterReminderScheduler: MeterReminderScheduler by lazy { MeterReminderScheduler(appContext) }
}
