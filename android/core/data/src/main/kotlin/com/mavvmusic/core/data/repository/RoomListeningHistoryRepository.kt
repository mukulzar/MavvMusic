package com.mavvmusic.core.data.repository

import com.mavvmusic.core.database.ListeningHistoryEntity
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.database.toMetadataEntity
import com.mavvmusic.core.database.toTrack
import com.mavvmusic.core.domain.ListeningArtistProfile
import com.mavvmusic.core.domain.ListeningGenreProfile
import com.mavvmusic.core.domain.ListeningHistoryRepository
import com.mavvmusic.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomListeningHistoryRepository @Inject constructor(
    private val database: MusicDatabase,
) : ListeningHistoryRepository {
    override suspend fun topArtistsThisWeek(limit: Int): List<ListeningArtistProfile> {
        require(limit > 0) { "The artist result limit must be positive." }
        val weekStart = System.currentTimeMillis() - WEEK_MILLIS
        return database.historyDao().topArtistsSince(weekStart, limit).map {
            ListeningArtistProfile(it.artist, it.seedTrackId, it.playCount)
        }
    }

    override suspend fun topGenresThisWeek(limit: Int): List<ListeningGenreProfile> {
        require(limit > 0) { "The genre result limit must be positive." }
        val weekStart = System.currentTimeMillis() - WEEK_MILLIS
        return database.historyDao().topGenresSince(weekStart, limit).map {
            ListeningGenreProfile(it.genre, it.playCount)
        }
    }

    override suspend fun findTrack(trackId: String): Track? =
        database.historyDao().findTrack(trackId)?.toTrack()

    override suspend fun recordTrackPlayed(track: Track, timestampMs: Long) {
        database.musicDao().upsertTrackMetadata(track.toMetadataEntity())
        database.historyDao().addListeningHistory(
            ListeningHistoryEntity(
                trackId = track.id,
                artist = track.artist.name,
                genre = track.genre?.trim()?.takeIf(String::isNotEmpty) ?: UNKNOWN_GENRE,
                timestamp = timestampMs,
            ),
        )
    }

    override suspend fun clearHistory() {
        database.historyDao().clearListeningHistory()
        database.musicDao().clearPlaybackHistory()
    }

    private companion object {
        const val WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val UNKNOWN_GENRE = "Unknown"
    }
}
