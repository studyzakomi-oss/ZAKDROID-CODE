package tech.zakomi.zakdroid.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecentWorkspaceEntity::class, OpenTabEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ZakdroidDatabase : RoomDatabase() {
    abstract fun recentWorkspaceDao(): RecentWorkspaceDao
    abstract fun openTabDao(): OpenTabDao
}
