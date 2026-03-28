package br.com.corp.heimdall.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccessLogEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class HeimdallDatabase : RoomDatabase() {
    abstract fun accessLogDao(): AccessLogDao
}
