package com.mavvmusic.core.domain

import com.mavvmusic.core.model.CachedTrack
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.MusicSettings
import com.mavvmusic.core.model.PlaybackState
import com.mavvmusic.core.model.Track
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow

data class ResolvedStream(
    val uri: String,
    val mimeType: String?,
    val bitrate: Int?,
    val expiresAtMs: Long?,
)

class StreamHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

interface MusicCatalogRepository {
    suspend fun getHomeFeed(): List<Track>
    suspend fun search(query: String): List<Track>
    suspend fun getTrack(trackId: String): Track?
}

interface StreamResolver {
    suspend fun resolve(trackId: String): ResolvedStream
}

interface CacheRepository {
    suspend fun cache(track: Track, stream: ResolvedStream): CachedTrack
    suspend fun getCachedTrack(trackId: String): CachedTrack?
    suspend fun clear()
    suspend fun cachedTrackIds(): Set<String>
}

interface SettingsRepository {
    val settings: StateFlow<MusicSettings>

    suspend fun setAutoCacheOnPlay(enabled: Boolean)
}

interface PlaybackRepository {
    val state: StateFlow<PlaybackState>

    suspend fun play(track: Track, playableUri: String, cacheStatus: CacheStatus, cacheOnPlay: Boolean)
    fun pause()
    fun resume()
    fun seek(positionMs: Long)
    fun skipToNext()
}
