package com.mavvmusic.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mavvmusic.core.data.LocalSettingsRepository
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.domain.CacheRepository
import com.mavvmusic.core.domain.ClearCacheUseCase
import com.mavvmusic.core.domain.GetHomeFeedUseCase
import com.mavvmusic.core.domain.GenerateDailyMixesUseCase
import com.mavvmusic.core.domain.GetRadioQueueUseCase
import com.mavvmusic.core.domain.ListeningHistoryRepository
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    fun provideGenerateDailyMixesUseCase(
        history: ListeningHistoryRepository,
        repository: MusicCatalogRepository,
    ) = GenerateDailyMixesUseCase(history, repository)

    @Provides
    fun provideGetRadioQueueUseCase(repository: MusicCatalogRepository) =
        GetRadioQueueUseCase(repository)

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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS track_metadata (
                    trackId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    artistId TEXT NOT NULL,
                    artistName TEXT NOT NULL,
                    albumId TEXT NOT NULL,
                    albumTitle TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    artworkUri TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS liked_tracks (
                    trackId TEXT NOT NULL PRIMARY KEY,
                    likedAtMs INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_tracks (
                    playlistId TEXT NOT NULL,
                    trackId TEXT NOT NULL,
                    addedAtMs INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, trackId),
                    FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)",
            )
        }

    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE track_metadata ADD COLUMN genre TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS listening_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    trackId TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    genre TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_listening_history_timestamp ON listening_history(timestamp)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_listening_history_artist ON listening_history(artist)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_listening_history_genre ON listening_history(genre)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_listening_history_trackId ON listening_history(trackId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_liked_tracks_likedAtMs ON liked_tracks(likedAtMs)",
            )
            db.execSQL(
                """
                INSERT INTO listening_history (trackId, artist, genre, timestamp)
                SELECT history.trackId,
                       metadata.artistName,
                       COALESCE(NULLIF(TRIM(metadata.genre), ''), 'Unknown'),
                       history.playedAtMs
                FROM playback_history AS history
                INNER JOIN track_metadata AS metadata ON metadata.trackId = history.trackId
                """.trimIndent(),
            )
        }
    }
}
