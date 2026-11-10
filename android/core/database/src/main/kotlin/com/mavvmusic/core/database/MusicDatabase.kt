package com.mavvmusic.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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

@Entity(tableName = "track_metadata")
data class TrackMetadataEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val artworkUri: String?,
    val genre: String?,
)

@Entity(
    tableName = "liked_tracks",
    indices = [Index("likedAtMs")],
)
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAtMs: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val addedAtMs: Long,
)

data class PlaybackHistoryItem(
    @Embedded val track: TrackMetadataEntity,
    val playedAtMs: Long,
)

data class CachedMusicItem(
    @Embedded val cachedTrack: CachedTrackEntity,
    @Embedded(prefix = "metadata_") val track: TrackMetadataEntity?,
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackMetadata(track: TrackMetadataEntity)

    @Query(
        """
        SELECT metadata.* FROM playback_history AS history
        INNER JOIN track_metadata AS metadata ON metadata.trackId = history.trackId
        ORDER BY history.playedAtMs DESC
        """,
    )
    suspend fun playbackHistory(): List<TrackMetadataEntity>

    @Insert
    suspend fun insertPlaybackHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearPlaybackHistory()

    @Query(
        """
        SELECT cached_tracks.*,
               metadata.trackId AS metadata_trackId,
               metadata.title AS metadata_title,
               metadata.artistId AS metadata_artistId,
               metadata.artistName AS metadata_artistName,
               metadata.albumId AS metadata_albumId,
               metadata.albumTitle AS metadata_albumTitle,
               metadata.durationMs AS metadata_durationMs,
               metadata.artworkUri AS metadata_artworkUri,
               metadata.genre AS metadata_genre
        FROM cached_tracks
        LEFT JOIN track_metadata AS metadata ON metadata.trackId = cached_tracks.trackId
        ORDER BY cached_tracks.lastPlayedAtMs DESC
        """,
    )
    suspend fun cachedMusic(): List<CachedMusicItem>

    @Query("SELECT track_metadata.* FROM track_metadata INNER JOIN liked_tracks USING (trackId) ORDER BY likedAtMs DESC")
    suspend fun likedTracks(): List<TrackMetadataEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId)")
    suspend fun isTrackLiked(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTrackLiked(track: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun removeLikedTrack(trackId: String)

    @Query("SELECT * FROM playlists ORDER BY updatedAtMs DESC")
    suspend fun playlists(): List<PlaylistEntity>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query(
        """
        SELECT track_metadata.* FROM track_metadata
        INNER JOIN playlist_tracks ON playlist_tracks.trackId = track_metadata.trackId
        WHERE playlist_tracks.playlistId = :playlistId
        ORDER BY playlist_tracks.addedAtMs ASC
        """,
    )
    suspend fun playlistTracks(playlistId: String): List<TrackMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("UPDATE playlists SET updatedAtMs = :updatedAtMs WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAtMs: Long)
}

fun com.mavvmusic.core.model.Track.toMetadataEntity() = TrackMetadataEntity(
    trackId = id,
    title = title,
    artistId = artist.id,
    artistName = artist.name,
    albumId = album.id,
    albumTitle = album.title,
    durationMs = durationMs,
    artworkUri = artworkUri,
    genre = genre,
)

fun TrackMetadataEntity.toTrack() = com.mavvmusic.core.model.Track(
    id = trackId,
    title = title,
    artist = com.mavvmusic.core.model.Artist(artistId, artistName),
    album = com.mavvmusic.core.model.Album(albumId, albumTitle, artworkUri),
    durationMs = durationMs,
    artworkUri = artworkUri,
    genre = genre,
)

@Database(
    entities = [
        CachedTrackEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        TrackMetadataEntity::class,
        LikedTrackEntity::class,
        PlaylistTrackEntity::class,
        ListeningHistoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun historyDao(): HistoryDao
}
