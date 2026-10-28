/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import fr.makoff.music.playback.ResizableCacheEvictor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import fr.makoff.music.constants.MaxSongCacheSizeKey
import fr.makoff.music.db.InternalDatabase
import fr.makoff.music.db.MusicDatabase
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase = InternalDatabase.newInternalDatabaseInstance(context)

    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @ApplicationScope scope: CoroutineScope,
    ): Cache {
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        fun limit(size: Int) = if (size == -1) Long.MAX_VALUE else size.coerceAtLeast(0) * 1024 * 1024L
        val evictor = ResizableCacheEvictor(limit(cacheSize))
        val cache = SimpleCache(context.filesDir.resolve("exoplayer"), evictor, databaseProvider)
        scope.launch(Dispatchers.IO) {
            context.dataStore.data.map { it[MaxSongCacheSizeKey] ?: 1024 }.distinctUntilChanged().collect {
                evictor.resize(cache, limit(it))
            }
        }
        return cache
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache = SimpleCache(
        context.filesDir.resolve("download"),
        NoOpCacheEvictor(),
        databaseProvider,
    )


}
