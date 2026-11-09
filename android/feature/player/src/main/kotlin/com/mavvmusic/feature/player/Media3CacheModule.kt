package com.mavvmusic.feature.player

import android.content.Context
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Media3CacheModule {
    @Provides
    @Singleton
    fun provideMediaCache(@ApplicationContext context: Context): Cache = SimpleCache(
        File(context.cacheDir, "media3-audio"),
        LeastRecentlyUsedCacheEvictor(MAX_MEDIA_CACHE_BYTES),
        StandaloneDatabaseProvider(context),
    )

    private const val MAX_MEDIA_CACHE_BYTES = 256L * 1024L * 1024L
}
