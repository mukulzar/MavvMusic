package com.mavvmusic.core.domain

import com.mavvmusic.core.model.CachedTrack
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.CancellationException

class SearchTracksUseCase(
    private val catalog: MusicCatalogRepository,
) {
    suspend operator fun invoke(query: String): List<Track> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        return catalog.search(normalizedQuery)
    }
}

class GetHomeFeedUseCase(
    private val catalog: MusicCatalogRepository,
) {
    suspend operator fun invoke(): List<Track> = catalog.getHomeFeed()
}

class ResolveStreamUseCase(
    private val resolver: StreamResolver,
) {
    suspend operator fun invoke(trackId: String): ResolvedStream = try {
        resolver.resolve(trackId)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException(
            error.message ?: "A stream could not be resolved for track $trackId.",
            error,
        )
    }
}

class CacheTrackUseCase(
    private val cache: CacheRepository,
) {
    suspend operator fun invoke(track: Track, stream: ResolvedStream): CachedTrack =
        cache.cache(track, stream)
}

class PlayTrackUseCase(
    private val resolveStream: ResolveStreamUseCase,
    private val cache: CacheRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackRepository,
) {
    suspend operator fun invoke(track: Track): Track {
        val trackId = track.id
        val cachedTrack = cache.getCachedTrack(trackId)
        val autoCacheOnPlay = settings.settings.value.autoCacheOnPlay
        val playableUri = cachedTrack?.filePath ?: resolveStream(trackId).uri
        val cacheStatus = if (cachedTrack != null) CacheStatus.CACHED else CacheStatus.NOT_CACHED
        playback.play(track, playableUri, cacheStatus, cachedTrack == null && autoCacheOnPlay)
        return track
    }
}

class SetAutoCacheOnPlayUseCase(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = settings.setAutoCacheOnPlay(enabled)
}

class ObserveMusicSettingsUseCase(
    private val settings: SettingsRepository,
) {
    operator fun invoke() = settings.settings
}

class PausePlaybackUseCase(
    private val playback: PlaybackRepository,
) {
    operator fun invoke() = playback.pause()
}

class ResumePlaybackUseCase(
    private val playback: PlaybackRepository,
) {
    operator fun invoke() = playback.resume()
}

class SeekPlaybackUseCase(
    private val playback: PlaybackRepository,
) {
    operator fun invoke(positionMs: Long) = playback.seek(positionMs.coerceAtLeast(0))
}

class SkipToNextUseCase(
    private val playback: PlaybackRepository,
) {
    operator fun invoke() = playback.skipToNext()
}

class ClearCacheUseCase(
    private val cache: CacheRepository,
) {
    suspend operator fun invoke() = cache.clear()
}
