package com.mavvmusic.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val trackId: String,
    val filePath: String,
    val sizeBytes: Long,
    val cachedAtMs: Long,
    val lastPlayedAtMs: Long,
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAtMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val updatedAtMs: Long,
)

@Dao
interface MusicDao {
    @Query("SELECT * FROM cached_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun findCachedTrack(trackId: String): CachedTrackEntity?

    @Query("SELECT * FROM cached_tracks ORDER BY lastPlayedAtMs ASC")
    suspend fun cachedTracksByLeastRecentlyPlayed(): List<CachedTrackEntity>

    @Query("SELECT trackId FROM cached_tracks")
    suspend fun cachedTrackIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedTrack(track: CachedTrackEntity)

    @Query("DELETE FROM cached_tracks WHERE trackId = :trackId")
    suspend fun deleteCachedTrack(trackId: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clearCachedTracks()
}

@Database(
    entities = [
        CachedTrackEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
}
