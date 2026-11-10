package com.mavvmusic.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "listening_history",
    indices = [
        Index("timestamp"),
        Index("artist"),
        Index("genre"),
        Index("trackId"),
    ],
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val artist: String,
    val genre: String,
    val timestamp: Long,
)

data class ArtistListeningCount(
    val artist: String,
    val playCount: Int,
    val seedTrackId: String,
)

data class GenreListeningCount(
    val genre: String,
    val playCount: Int,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun addListeningHistory(entry: ListeningHistoryEntity)

    @Query(
        """
        SELECT MIN(TRIM(history.artist)) AS artist,
               COUNT(*) AS playCount,
               (
                   SELECT seed.trackId
                   FROM listening_history AS seed
                   WHERE LOWER(TRIM(seed.artist)) = LOWER(TRIM(history.artist))
                     AND seed.timestamp >= :weekStartTimestamp
                   GROUP BY seed.trackId
                   ORDER BY COUNT(*) DESC, MAX(seed.timestamp) DESC
                   LIMIT 1
               ) AS seedTrackId
        FROM listening_history AS history
        WHERE history.timestamp >= :weekStartTimestamp
          AND TRIM(history.artist) != ''
        GROUP BY TRIM(history.artist) COLLATE NOCASE
        ORDER BY playCount DESC, artist COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun topArtistsSince(
        weekStartTimestamp: Long,
        limit: Int,
    ): List<ArtistListeningCount>

    @Query(
        """
        SELECT MIN(TRIM(genre)) AS genre, COUNT(*) AS playCount
        FROM listening_history
        WHERE timestamp >= :weekStartTimestamp
          AND TRIM(genre) != ''
          AND genre != 'Unknown'
        GROUP BY TRIM(genre) COLLATE NOCASE
        ORDER BY playCount DESC, genre COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun topGenresSince(
        weekStartTimestamp: Long,
        limit: Int,
    ): List<GenreListeningCount>

    @Query(
        """
        SELECT metadata.*
        FROM liked_tracks AS liked
        INNER JOIN track_metadata AS metadata ON metadata.trackId = liked.trackId
        ORDER BY liked.likedAtMs DESC
        LIMIT :limit
        """,
    )
    suspend fun recentlyLikedTracks(limit: Int): List<TrackMetadataEntity>

    @Query("SELECT * FROM track_metadata WHERE trackId = :trackId LIMIT 1")
    suspend fun findTrack(trackId: String): TrackMetadataEntity?

    @Query("DELETE FROM listening_history")
    suspend fun clearListeningHistory()
}
