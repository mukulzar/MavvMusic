package com.mavvmusic.core.domain

import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.CachedTrack
import com.mavvmusic.core.model.MusicSettings
import com.mavvmusic.core.model.PlaybackState
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTrackUseCaseTest {
    @Test
    fun streamResolutionFailureIsWrappedWithItsOriginalCause() = runBlocking {
        val cause = IllegalStateException("Video is unavailable.")
        val useCase = ResolveStreamUseCase(object : StreamResolver {
            override suspend fun resolve(trackId: String): ResolvedStream = throw cause
        })

        try {
            useCase("track-id")
            throw AssertionError("Expected stream resolution to fail.")
        } catch (error: IllegalStateException) {
            assertEquals(cause.message, error.message)
            assertEquals(cause, error.cause)
        }
    }

    @Test
    fun playbackReceivesTheSelectedTrackQueueWithoutBlockingOnDownload() = runBlocking {
        val track = Track(
            id = "track-id",
            title = "Track",
            artist = Artist("artist-id", "Artist"),
            album = Album("album-id", "Album", null),
            durationMs = 180_000,
            artworkUri = null,
        )
        val cache = RetryingCacheRepository()
        val playback = RecordingPlaybackRepository()
        val useCase = PlayTrackUseCase(
            cache = cache,
            settings = object : SettingsRepository {
                override val settings = MutableStateFlow(MusicSettings(autoCacheOnPlay = true))
                override suspend fun setAutoCacheOnPlay(enabled: Boolean) = Unit
            },
            playback = playback,
        )

        useCase(track, listOf(track))

        assertEquals(0, cache.cacheCalls)
        assertEquals(null, playback.cachedUri)
        assertEquals(CacheStatus.NOT_CACHED, playback.playedCacheStatus)
        assertEquals(true, playback.cacheOnPlay)
        assertEquals(listOf(track), playback.queue)
    }

    private class RetryingCacheRepository : CacheRepository {
        var cacheCalls = 0
        var cachedStreamUri: String? = null

        override suspend fun cache(track: Track, stream: ResolvedStream): CachedTrack {
            cacheCalls += 1
            if (cacheCalls == 1) throw StreamHttpException(403, "Expired stream")
            cachedStreamUri = stream.uri
            return CachedTrack(track.id, "file:///cached/${track.id}.audio", 10, 1, 1)
        }

        override suspend fun getCachedTrack(trackId: String): CachedTrack? = null
        override suspend fun clear() = Unit
        override suspend fun cachedTrackIds(): Set<String> = emptySet()
    }

    private class RecordingPlaybackRepository : PlaybackRepository {
        override val state = MutableStateFlow(PlaybackState())
        var playedUri: String? = null
        var playedCacheStatus: CacheStatus? = null
        var cacheOnPlay = false

        override suspend fun play(
            track: Track,
            queue: List<Track>,
            cachedUri: String?,
            cacheStatus: CacheStatus,
            cacheOnPlay: Boolean,
        ) {
            this.queue = queue
            this.cachedUri = cachedUri
            playedCacheStatus = cacheStatus
            this.cacheOnPlay = cacheOnPlay
        }

        var queue: List<Track> = emptyList()
        var cachedUri: String? = null

        override fun pause() = Unit
        override fun resume() = Unit
        override fun seek(positionMs: Long) = Unit
        override fun skipToNext() = Unit
    }
}
