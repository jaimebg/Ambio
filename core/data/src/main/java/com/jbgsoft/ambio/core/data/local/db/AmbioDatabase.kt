package com.jbgsoft.ambio.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jbgsoft.ambio.core.data.local.dao.SessionDao
import com.jbgsoft.ambio.core.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AmbioDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "ambio_database"
    }
}
