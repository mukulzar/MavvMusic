package com.mavvmusic.core.model

data class Artist(
    val id: String,
    val name: String,
)

data class Album(
    val id: String,
    val title: String,
    val artworkUri: String?,
)

enum class CacheStatus {
    NOT_CACHED,
    CACHING,
    CACHED,
    FAILED,
}

data class Track(
    val id: String,
    val title: String,
    val artist: Artist,
    val album: Album,
    val durationMs: Long,
    val artworkUri: String?,
)

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val cacheStatus: CacheStatus = CacheStatus.NOT_CACHED,
    val error: String? = null,
)

data class CachedTrack(
    val trackId: String,
    val filePath: String,
    val sizeBytes: Long,
    val cachedAtMs: Long,
    val lastPlayedAtMs: Long,
)

data class MusicSettings(
    val autoCacheOnPlay: Boolean = true,
)
