package com.mavvmusic.di

import android.content.Context
import androidx.room.Room
import com.mavvmusic.core.data.LocalSettingsRepository
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.domain.CacheRepository
import com.mavvmusic.core.domain.ClearCacheUseCase
import com.mavvmusic.core.domain.GetHomeFeedUseCase
import com.mavvmusic.core.domain.MusicCatalogRepository
import com.mavvmusic.core.domain.ObserveMusicSettingsUseCase
import com.mavvmusic.core.domain.PausePlaybackUseCase
import com.mavvmusic.core.domain.PlaybackRepository
import com.mavvmusic.core.domain.PlayTrackUseCase
import com.mavvmusic.core.domain.ResumePlaybackUseCase
import com.mavvmusic.core.domain.SearchTracksUseCase
import com.mavvmusic.core.domain.SeekPlaybackUseCase
import com.mavvmusic.core.domain.SkipToNextUseCase
import com.mavvmusic.core.domain.SetAutoCacheOnPlayUseCase
import com.mavvmusic.core.domain.SettingsRepository
import com.mavvmusic.feature.cache.AutoCacheRepository
import com.mavvmusic.feature.player.Media3PlaybackRepository
import com.mavvmusic.feature.player.PlaybackStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "mavv-music.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        LocalSettingsRepository(context)

    @Provides
    @Singleton
    fun provideCacheRepository(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        httpClient: OkHttpClient,
    ): CacheRepository = AutoCacheRepository(context, database.musicDao(), httpClient)

    @Provides
    @Singleton
    fun providePlaybackStateStore() = PlaybackStateStore()

    @Provides
    @Singleton
    fun providePlaybackRepository(
        @ApplicationContext context: Context,
        stateStore: PlaybackStateStore,
    ): PlaybackRepository = Media3PlaybackRepository(context, stateStore)

    @Provides
    fun provideSearchTracksUseCase(repository: MusicCatalogRepository) = SearchTracksUseCase(repository)

    @Provides
    fun provideObserveMusicSettingsUseCase(settings: SettingsRepository) =
        ObserveMusicSettingsUseCase(settings)

    @Provides
    fun provideSetAutoCacheOnPlayUseCase(settings: SettingsRepository) =
        SetAutoCacheOnPlayUseCase(settings)

    @Provides
    fun provideHomeFeedUseCase(repository: MusicCatalogRepository) = GetHomeFeedUseCase(repository)

    @Provides
    fun providePlayTrackUseCase(
        cache: CacheRepository,
        settings: SettingsRepository,
        playback: PlaybackRepository,
    ) = PlayTrackUseCase(cache, settings, playback)

    @Provides
    fun providePausePlaybackUseCase(playback: PlaybackRepository) = PausePlaybackUseCase(playback)

    @Provides
    fun provideResumePlaybackUseCase(playback: PlaybackRepository) = ResumePlaybackUseCase(playback)

    @Provides
    fun provideSeekPlaybackUseCase(playback: PlaybackRepository) = SeekPlaybackUseCase(playback)

    @Provides
    fun provideSkipToNextUseCase(playback: PlaybackRepository) = SkipToNextUseCase(playback)

    @Provides
    fun provideClearCacheUseCase(cache: CacheRepository) = ClearCacheUseCase(cache)
}
