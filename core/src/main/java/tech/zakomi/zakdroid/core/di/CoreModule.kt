package tech.zakomi.zakdroid.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.zakomi.zakdroid.core.data.OpenTabDao
import tech.zakomi.zakdroid.core.data.RecentWorkspaceDao
import tech.zakomi.zakdroid.core.data.ZakdroidDatabase
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.fs.WorkspaceFileSystem
import tech.zakomi.zakdroid.core.settings.DataStoreSettingsRepository
import tech.zakomi.zakdroid.core.settings.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreProvidesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZakdroidDatabase =
        Room.databaseBuilder(context, ZakdroidDatabase::class.java, "zakdroid.db").build()

    @Provides
    fun recentWorkspaceDao(db: ZakdroidDatabase): RecentWorkspaceDao = db.recentWorkspaceDao()

    @Provides
    fun openTabDao(db: ZakdroidDatabase): OpenTabDao = db.openTabDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindsModule {
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun fileSystem(impl: WorkspaceFileSystem): FileSystem
}
