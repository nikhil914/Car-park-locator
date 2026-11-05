package com.realworldmagic.parkpin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ParkingSpotEntity::class], version = 1, exportSchema = true)
abstract class ParkPinDatabase : RoomDatabase() {
    abstract fun parkingDao(): ParkingDao

    companion object {
        const val NAME = "parkpin.db"

        fun build(context: Context): ParkPinDatabase =
            Room.databaseBuilder(context.applicationContext, ParkPinDatabase::class.java, NAME).build()
    }
}
